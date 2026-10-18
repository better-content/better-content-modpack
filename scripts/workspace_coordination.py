#!/usr/bin/env python3
"""Atomic lane-local coordination. Claims/registrations are not user authorization.

No cleanup, Git, deployment, shell execution, or Herdr lifecycle operations.
"""
from __future__ import annotations
import argparse
from contextlib import contextmanager
import fcntl
import json
import os
from pathlib import Path
import signal
import stat
import subprocess
import sys
import time
import uuid

DEFAULT_ROOT = Path(os.environ.get('WORKSPACE_COORDINATION_ROOT', '/home/dev/.worklane/coordination'))

class Conflict(RuntimeError):
    pass


def process_identity(pid):
    """Linux boot + /proc start ticks; zombies are no longer live producers."""
    try:
        stat = Path(f'/proc/{int(pid)}/stat').read_text().rsplit(')', 1)[1].split()
        if stat[0] == 'Z':
            return None
        return {'pid': int(pid), 'start': stat[19],
                'boot': Path('/proc/sys/kernel/random/boot_id').read_text().strip()}
    except (OSError, ValueError, IndexError):
        return None


def identity_liveness(identity):
    """Observation only: absence/reuse is stale, inaccessible identity is unknown."""
    if not isinstance(identity,dict) or not all(k in identity for k in ('pid','start','boot')):
        return 'unknown'
    if type(identity['pid']) is not int or identity['pid'] <= 0 or not isinstance(identity['start'],str) or not isinstance(identity['boot'],str):
        return 'unknown'
    current = process_identity(identity['pid'])
    if current is not None:
        return 'live' if current == identity else 'stale'
    try:
        data = Path(f'/proc/{int(identity["pid"])}/stat').read_text().rsplit(')',1)[1].split()
        return 'stale' if data[0] == 'Z' else 'unknown'
    except FileNotFoundError:
        return 'stale'
    except (OSError,ValueError,IndexError):
        return 'unknown'


class Coordinator:
    def __init__(self, root=DEFAULT_ROOT):
        self.root = Path(root)
        if not self.root.is_absolute() or '..' in self.root.parts:
            raise Conflict('coordination root must be absolute without traversal')

    def prepare(self, create=True):
        for ancestor in reversed((self.root, *self.root.parents)):
            if ancestor.is_symlink():
                raise Conflict('symlink coordination ancestor rejected')
        if not create and not self.root.exists():
            return False
        self.root.mkdir(mode=0o700, parents=True, exist_ok=True) if create else None
        if self.root.stat().st_uid != os.getuid() or not self.root.is_dir():
            raise Conflict('coordination root must be an owned directory')
        return True

    @staticmethod
    def empty():
        return dict(version=1, tasks={}, assignments={}, turns={}, leases={}, jobs={}, reservation=None)

    def snapshot(self):
        """Lock-free read of one atomically replaced snapshot; never mkdir/write."""
        if not self.prepare(create=False):
            return self.empty()
        try:
            fd = os.open(self.root / 'state.json', os.O_RDONLY | os.O_NOFOLLOW)
        except FileNotFoundError:
            return self.empty()
        with os.fdopen(fd) as source:
            s = json.load(source)
        if s.get('version') != 1 or any(not isinstance(s.get(k),dict)
                for k in ('tasks','assignments','turns','leases','jobs')):
            raise Conflict('malformed coordination state')
        return s

    @contextmanager
    def transaction(self, admission=False):
        self.prepare()
        lockfd = os.open(self.root / 'lock', os.O_CREAT | os.O_RDWR | os.O_NOFOLLOW, 0o600)
        with os.fdopen(lockfd, 'a+') as lock:
            st = os.fstat(lock.fileno())
            if not stat.S_ISREG(st.st_mode) or st.st_uid != os.getuid() or st.st_nlink != 1:
                raise Conflict('unsafe coordination lock')
            try:
                fcntl.flock(lock, fcntl.LOCK_EX | (fcntl.LOCK_NB if admission else 0))
            except BlockingIOError:
                self.request_work()
                raise Conflict('coordination busy: work requested; retry after maintenance yields')
            path = self.root / 'state.json'
            if path.is_symlink():
                raise Conflict('symlink coordination state rejected')
            if path.exists():
                with os.fdopen(os.open(path, os.O_RDONLY | os.O_NOFOLLOW)) as source:
                    state = json.load(source)
                if state.get('version') != 1 or any(not isinstance(state.get(k), dict)
                        for k in ('tasks', 'assignments', 'turns', 'leases', 'jobs')):
                    raise Conflict('malformed coordination state')
            else:
                state = self.empty()
            original = json.dumps(state, separators=(',', ':'), sort_keys=True) if path.exists() else None
            yield state
            serialized = json.dumps(state, separators=(',', ':'), sort_keys=True)
            if serialized == original:
                return
            temp = self.root / f'.state-{uuid.uuid4().hex}'
            with temp.open('x') as out:
                out.write(serialized)
                out.flush()
                os.fsync(out.fileno())
            os.replace(temp, path)
            fd = os.open(self.root, os.O_DIRECTORY)
            try:
                os.fsync(fd)
            finally:
                os.close(fd)

    def request_work(self):
        self.prepare()
        fd = os.open(self.root / 'work-requested', os.O_CREAT | os.O_WRONLY | os.O_NOFOLLOW, 0o600)
        os.close(fd)

    def maintenance_should_yield(self):
        return (self.root / 'work-requested').exists()

    def admit(self, s):
        if s.get('reservation'):
            self.request_work()
            raise Conflict('maintenance reserved: work requested; retry after yield')

    @staticmethod
    def active(s, task, generation=None):
        t = s['tasks'].get(task)
        if not t or t['state'] != 'active' or (generation is not None and t['generation'] != generation):
            raise Conflict('missing, cancelled, completed, or superseded task')
        return t

    def authority(self, task, generation, owner=None, operation=None):
        t = self.snapshot()['tasks'].get(task)
        return dict(valid=bool(t and t['state'] == 'active' and t['generation'] == generation
                               and (owner is None or t['owner'] == owner)
                               and (operation is None or (owner is not None and operation in t.get('operations',[])))),
                    operation=operation, owner=t['owner'] if t else None,
                    task=task, state=t['state'] if t else 'missing', generation=t['generation'] if t else None)

    def register(self, task, owner, topology=None, helper_limit=4, operations=None):
        operations = sorted(set(operations or []))
        if any(not isinstance(op,str) or not op.strip() or len(op)>120 or '*' in op for op in operations):
            raise Conflict('operations must be exact nonblank tokens without wildcards')
        if not 0 <= helper_limit <= 16:
            raise Conflict('helper limit must be 0..16 (default 4)')
        if topology is None:
            topology = {k: os.environ.get('HERDR_' + k.upper() + ('_ID' if k != 'session' else ''), '')
                        for k in ('session','workspace','tab','pane')}
        with self.transaction(admission=True) as s:
            self.admit(s)
            old = s['tasks'].get(task)
            if old and old['state'] == 'active':
                if old['owner'] != owner:
                    raise Conflict('one accountable lead per task')
                return dict(old)
            t = dict(operations=operations, helper_limit=helper_limit, helper_workspace=None, owner=owner, generation=(old['generation'] + 1 if old else 1),
                     state='active', topology=topology or {}, created=time.time())
            s['tasks'][task] = t
            return dict(t)

    def assign(self, task, owner, helper, workspace, tab, pane, area):
        with self.transaction(admission=True) as s:
            self.admit(s)
            t = self.active(s, task)
            if t['owner'] != owner:
                raise Conflict('helpers cannot recursively delegate')
            top = t['topology']
            if not top.get('workspace') or not all((workspace, tab, pane)) or workspace == top.get('workspace'):
                raise Conflict('helper needs separate workspace and tab; no splits')
            live = [a for a in s['assignments'].values() if a['task'] == task and a['state'] == 'active']
            if len(live) >= t.get('helper_limit',4) or any(a['tab'] == tab or a['helper'] == helper for a in live):
                raise Conflict('helper bound or separate-tab constraint')
            if t.get('helper_workspace') and t['helper_workspace'] != workspace:
                raise Conflict('reuse the task dedicated helper workspace')
            t['helper_workspace'] = workspace
            key = uuid.uuid4().hex
            s['assignments'][key] = dict(task=task, generation=t['generation'], helper=helper,
                workspace=workspace, tab=tab, pane=pane, area=area, state='active')
            return dict(assignment=key, **s['assignments'][key])

    @staticmethod
    def herdr(session, *args):
        result = subprocess.run(['herdr', '--session', session, *args], capture_output=True,
                                text=True, timeout=45, check=True)
        return json.loads(result.stdout)['result']

    @staticmethod
    def returned_id(value, key):
        found = set()
        def visit(v):
            if isinstance(v, dict):
                if isinstance(v.get(key), str): found.add(v[key])
                for child in v.values(): visit(child)
            elif isinstance(v, list):
                for child in v: visit(child)
        visit(value)
        if len(found) != 1:
            raise Conflict(f'expected one returned {key}, got {sorted(found)}')
        return found.pop()

    def launch_helper(self, task, owner, helper, area, prompt, cwd):
        """Bounded Pi launch. No shell, pane splits, recursive delegation or focus.

        Launch requires the registered lead's exact live Herdr pane. This is an
        operating constraint, not authorization to launch outside the user task.
        Failures leave their assignment/topology for reconciliation, never close it.
        """
        cwd = Path(cwd)
        if not cwd.is_absolute() or not cwd.is_dir() or not prompt.startswith('[AGENT MESSAGE]'):
            raise Conflict('absolute existing cwd and flagged coordination prompt required')
        with self.transaction(admission=True) as s:
            self.admit(s)
            t = self.active(s, task)
            top = t['topology']
            if t['owner'] != owner or not top.get('pane') or top.get('pane') != os.environ.get('HERDR_PANE_ID') or top.get('session') != os.environ.get('HERDR_SESSION'):
                raise Conflict('only registered lead in its exact Herdr pane may launch')
            live = [a for a in s['assignments'].values() if a['task'] == task and a['state'] in ('active','launching','launch-failed')]
            if len(live) >= t.get('helper_limit',4) or any(a['helper'] == helper for a in live):
                raise Conflict('bounded helpers exhausted or helper already assigned')
            if t.get('helper_workspace_pending'):
                raise Conflict('dedicated helper workspace creation in progress; retry')
            helper_workspace = t.get('helper_workspace')
            session = top['session']
            generation = t['generation']
            key = uuid.uuid4().hex
            if not helper_workspace:
                t['helper_workspace_pending'] = key
            s['assignments'][key] = dict(task=task,generation=generation,helper=helper,area=area,
                workspace=None,tab=None,pane=None,state='launching')
        def record(**fields):
            with self.transaction() as s:
                a = s['assignments'][key]
                if a['state'] == 'cancelled':
                    fields.pop('state', None)
                a.update(fields)
                t = s['tasks'].get(task)
                valid = bool(t and t['state'] == 'active' and t['generation'] == generation)
            if not valid:
                raise Conflict('task cancelled or superseded during helper launch')
        try:
            workspace = helper_workspace
            if not workspace:
                workspace = self.returned_id(self.herdr(session,'workspace','create','--cwd',str(cwd),
                    '--label',f'helpers-{task}','--no-focus'),'workspace_id')
                with self.transaction() as s:
                    s['tasks'][task]['helper_workspace'] = workspace
                    s['tasks'][task].pop('helper_workspace_pending',None)
            record(workspace=workspace)
            tab = self.returned_id(self.herdr(session,'tab','create','--workspace',workspace,
                '--cwd',str(cwd),'--label',helper,'--no-focus',
                '--env',f'WORKSPACE_COORDINATION_TASK={task}',
                '--env',f'WORKSPACE_COORDINATION_GENERATION={generation}',
                '--env',f'WORKSPACE_COORDINATION_ROOT={self.root}',
                '--env',f'WORKSPACE_COORDINATION_AREAS={area}'),'tab_id')
            record(tab=tab)
            panes = self.herdr(session,'pane','list')['panes']
            matches = [p for p in panes if p['tab_id'] == tab and p['workspace_id'] == workspace]
            if len(matches) != 1:
                raise Conflict('helper tab must have exactly one pane; no splits')
            pane = matches[0]['pane_id']
            record(pane=pane)
            self.herdr(session,'agent','start',helper,'--kind','pi','--pane',pane)
            record(state='active')
            self.herdr(session,'agent','prompt',pane,prompt + '\nBounded helper: do not recursively delegate. Preserve edits/evidence on cancellation; task authority is generation-bound.')
            return dict(assignment=key,task=task,generation=generation,workspace=workspace,tab=tab,pane=pane)
        except Exception as e:
            with self.transaction() as s:
                a = s['assignments'][key]
                s['tasks'][task].pop('helper_workspace_pending',None)
                if a['state'] != 'cancelled': a['state'] = 'launch-failed'
                a['error'] = str(e)[:1000]
            raise

    def result(self, assignment, generation):
        with self.transaction() as s:
            a = s['assignments'].get(assignment)
            if not a or a['state'] != 'active' or a['generation'] != generation:
                raise Conflict('late result rejected')
            self.active(s, a['task'], generation)
            a['state'] = 'complete'
            return dict(accepted=True)

    @staticmethod
    def area(value, cwd):
        if value.startswith(('identity:','resource:','token:')):
            return value
        path = Path(value)
        if not path.is_absolute(): path = Path(cwd) / path
        path = Path(os.path.abspath(path))
        if any(p.is_symlink() for p in (path,*path.parents)):
            raise Conflict('symlink writer area rejected')
        return str(path)

    @staticmethod
    def overlaps(a,b):
        if a == b: return True
        if not a.startswith('/') or not b.startswith('/'): return False
        return Path(a).is_relative_to(b) or Path(b).is_relative_to(a)

    def scope(self, owner):
        s = self.snapshot()
        c = s['turns'].get(owner)
        areas = [a for a in c['areas'] if not a.startswith('identity:')] if c else []
        leases = [k for k,v in s['leases'].items() if v['owner']==owner]
        return dict(valid=bool(c and (areas or leases)),owner=owner,areas=areas,leases=leases)

    def covers(self, owner, path, cwd=None):
        target = self.area(path,cwd or os.getcwd())
        c = self.snapshot()['turns'].get(owner)
        return dict(valid=bool(c and any(a == target or (a.startswith('/') and target.startswith('/')
                    and Path(target).is_relative_to(a)) for a in c['areas'])), owner=owner, path=target)

    def claim(self, owner, areas, task=None, generation=None, topology=None, cwd=None, extend=False,
              actor_pid=None, session_id=None):
        with self.transaction(admission=True) as s:
            self.admit(s)
            if task:
                self.active(s, task, generation)
            cwd = str(Path(cwd or os.getcwd()).absolute())
            areas = [self.area(a,cwd) for a in areas]
            previous = s['turns'].get(owner,{}) if extend else {}
            areas = sorted(set(areas + previous.get('areas',[])))
            for other, c in s['turns'].items():
                if other != owner and any(self.overlaps(a,self.area(b,c.get('cwd',cwd))) for a in areas for b in c['areas']):
                    raise Conflict(f'overlapping writer: {other}')
            s['turns'][owner] = dict(areas=areas, task=task or previous.get('task'), generation=generation or previous.get('generation'),
                                    cwd=cwd, topology=topology or previous.get('topology',{}), since=previous.get('since',time.time()),
                                    actor_identity=previous.get('actor_identity') or (process_identity(actor_pid) if actor_pid is not None else None),
                                    session_id=session_id or previous.get('session_id'))
            return dict(owner=owner, **s['turns'][owner])

    def release(self, owner):
        with self.transaction() as s:
            s['turns'].pop(owner, None)
        return dict(released=owner)

    def lease(self, task, generation, owner, resource):
        with self.transaction(admission=True) as s:
            self.admit(s)
            self.active(s, task, generation)
            if any(l['resource'] == resource for l in s['leases'].values()):
                raise Conflict('resource already leased')
            key = uuid.uuid4().hex
            s['leases'][key] = dict(task=task, generation=generation, owner=owner, resource=resource)
            return dict(lease=key)

    def unlease(self, key, owner):
        with self.transaction() as s:
            if key in s['leases'] and s['leases'][key]['owner'] != owner:
                raise Conflict('foreign lease')
            s['leases'].pop(key, None)
        return dict(released=key)

    @staticmethod
    def unresolved_job(job):
        return (job.get('kind') == 'bounded-facade' and job.get('state') != 'complete') or bool(job.get('surviving_pids')) or process_identity(job['identity']['pid']) == job['identity']

    @staticmethod
    def blockers(s):
        return dict(turns=list(s['turns']), leases=list(s['leases']),
                    tasks=[k for k, v in s['tasks'].items() if v['state'] == 'active'],
                    jobs=[k for k, v in s['jobs'].items() if Coordinator.unresolved_job(v)])

    @staticmethod
    def agents(live_agents=None):
        if live_agents is not None:
            return live_agents
        session = os.environ.get('HERDR_SESSION')
        if not session:
            raise Conflict('Herdr identity unavailable; supply verified live_agents explicitly')
        probe = subprocess.run(['herdr', '--session', session, 'agent', 'list'],
                               capture_output=True, text=True, timeout=10, check=True)
        return json.loads(probe.stdout)['result']['agents']

    def reserve(self, owner, live_agents=None):
        """Quiet admission before a cleanup actor may exclude itself."""
        with self.transaction() as s:
            if s.get('reservation'):
                if s['reservation']['owner'] != owner:
                    raise Conflict('foreign maintenance reservation')
                return dict(reservation=s['reservation'], blockers=self.blockers(s))
            b = self.blockers(s)
            b['agents'] = [a.get('pane_id') for a in self.agents(live_agents)
                if a.get('agent_status', 'unknown') not in ('idle','done')]
            if any(b.values()):
                raise Conflict('not quiet at reservation: ' + json.dumps(b))
            s['reservation'] = dict(owner=owner, identity=process_identity(os.getpid()), since=time.time())
            return dict(reservation=s['reservation'], blockers=b)

    def unreserve(self, owner):
        with self.transaction() as s:
            if s.get('reservation') and s['reservation']['owner'] != owner:
                raise Conflict('foreign maintenance reservation')
            s['reservation'] = None
            (self.root / 'work-requested').unlink(missing_ok=True)
        return dict(released=owner)

    @contextmanager
    def maintenance(self, owner, live_agents=None):
        """Hold admission lock through caller's recheck and mutation; fail closed."""
        with self.transaction() as s:
            if not s.get('reservation') or s['reservation']['owner'] != owner:
                raise Conflict('reserve before maintenance')
            registered_actor = s['reservation'].get('identity') == process_identity(os.getpid())
            busy = [a.get('pane_id') for a in self.agents(live_agents)
                    if a.get('agent_status', 'unknown') not in ('idle','done')
                    and not (registered_actor and a.get('pane_id') == owner)]
            b = self.blockers(s)
            b['agents'] = busy
            if self.maintenance_should_yield():
                raise Conflict('new work requested; release maintenance reservation')
            if any(b.values()):
                raise Conflict('not quiet: ' + json.dumps(b))
            yield s

    def run_job(self, task, generation, owner, operation, argv, cwd, budget_seconds, log):
        """Launch our own bounded supervisor; never adopt guessed external PIDs.

        This call stays alive until the supervisor exits. It may run in an owned
        background launcher, but the supervisor still has this identity-bound parent.
        """
        from bounded_facade import output_path
        import shutil
        cwd = Path(cwd)
        if not cwd.is_absolute() or not cwd.is_dir() or any(p.is_symlink() for p in (cwd,*cwd.parents)):
            raise Conflict('job cwd must be absolute ordinary directory')
        if not argv or not 0 < budget_seconds <= 28800:
            raise Conflict('argv and finite <=8h deadline required')
        # Intentionally narrow named producer adapters, never an unrestricted shell.
        exe = shutil.which(argv[0])
        allowed_python = exe and Path(exe).resolve() == Path(sys.executable).resolve()
        allowed_node = exe and Path(exe).name in ('node','nodejs')
        if operation == 'docs':
            expected = [sys.executable,'-B',str(Path(__file__).resolve().with_name('check_documentation.py'))]
            if argv != expected: raise Conflict('docs adapter accepts only canonical documentation check')
        elif operation == 'synthetic':
            index = 2 if len(argv)>1 and argv[1] == '-B' else 1
            if not (allowed_python or allowed_node) or len(argv)<=index:
                raise Conflict('synthetic adapter accepts Python/Node script files only')
            script = Path(argv[index])
            roots = (Path('/home/dev/.tmp'),Path('/home/dev/workspace_artifacts'),Path(__file__).resolve().parent)
            if not script.is_absolute() or not script.is_file() or not any(script.is_relative_to(r) for r in roots) or any(p.is_symlink() for p in (script,*script.parents)):
                raise Conflict('synthetic script must be ordinary file in approved fixture/source root')
        elif operation == 'dev':
            if argv != [str(Path(__file__).resolve().parent.parent/'test.main.kts'),'dev']:
                raise Conflict('dev adapter accepts only canonical Dev facade')
        else:
            raise Conflict('unsupported job adapter; no inferred Dist/Debug/build authorization')
        log = output_path(log)
        supervisor_log = output_path(log.with_name(log.name+'.supervisor.log'))
        receipt_path = log.with_name(log.name+'.diagnostic.json')
        if log.exists() or supervisor_log.exists() or receipt_path.exists() or receipt_path.is_symlink():
            raise Conflict('job evidence path already exists; use a fresh exact log')
        supervisor = Path(__file__).with_name('bounded_facade.py').resolve()
        with self.transaction(admission=True) as s:
            self.admit(s)
            t = self.active(s,task,generation)
            if operation not in t.get('operations',[]):
                raise Conflict('operation not registered for task')
            if owner != t['owner'] and not any(a['task']==task and a['generation']==generation and
                    a['state']=='active' and a['helper']==owner for a in s['assignments'].values()):
                raise Conflict('job owner is not lead or current assigned helper')
            with supervisor_log.open('xb') as diagnostic:
                p = subprocess.Popen([sys.executable,'-B',str(supervisor),
                    '--budget-seconds',str(budget_seconds),'--cwd',str(cwd),'--log',str(log),
                    '--parent-pid',str(os.getpid()),'--',*argv],stdin=subprocess.DEVNULL,
                    stdout=diagnostic,stderr=subprocess.STDOUT,start_new_session=True)
            identity = process_identity(p.pid)
            if not identity:
                p.wait(timeout=5)
                raise Conflict('supervisor failed before ownership registration')
            key = uuid.uuid4().hex
            s['jobs'][key] = dict(task=task,generation=generation,owner=owner,operation=operation,
                identity=identity,kind='bounded-facade',state='running',log=str(log),
                supervisor_log=str(supervisor_log),deadline=time.time()+budget_seconds)
        status = p.wait()
        diagnostic = log.with_name(log.name+'.diagnostic.json')
        survivors = []
        cleanup_known = type(status) is int and status == 0
        if diagnostic.exists() or diagnostic.is_symlink():
            cleanup_known = False
            try:
                with os.fdopen(os.open(diagnostic,os.O_RDONLY | os.O_NOFOLLOW)) as source:
                    receipt = json.load(source)
                reported = receipt.get('surviving_pids')
                if (type(status) is int and status >= 0
                        and receipt.get('schema') == 'bc.bounded_facade.v1'
                        and type(receipt.get('status')) is int and receipt['status'] == status
                        and isinstance(reported,list)
                        and all(type(pid) is int and pid > 0 for pid in reported)):
                    survivors = reported
                    cleanup_known = True
            except (ValueError,KeyError,OSError,AttributeError,TypeError):
                pass
        with self.transaction() as s:
            job = s['jobs'][key]
            job.update(state='complete' if cleanup_known and not survivors else 'cleanup-unknown',
                       status=status, surviving_pids=survivors,cleanup_complete=cleanup_known and not survivors)
        return dict(job=key,status=status,cleanup_complete=cleanup_known and not survivors,
                    surviving_pids=survivors,log=str(log),supervisor_log=str(supervisor_log))

    def fixture_job(self, task, generation, owner, seconds=30):
        """Only built-in inert sleep fixture, no arbitrary command or shell."""
        if not 0 < seconds <= 120:
            raise Conflict('fixture duration out of bounds')
        with self.transaction(admission=True) as s:
            self.admit(s)
            self.active(s, task, generation)
            argv = [sys.executable, '-B', '-c',
                    'import time; time.sleep(float(__import__("sys").argv[1]))', str(seconds)]
            pid = os.posix_spawn(sys.executable, argv, os.environ, setsid=True,
                file_actions=[(os.POSIX_SPAWN_OPEN, fd, os.devnull, os.O_RDWR, 0o600) for fd in (0,1,2)])
            identity = process_identity(pid)
            if not identity:
                raise Conflict('fixture failed to start')
            key = uuid.uuid4().hex
            s['jobs'][key] = dict(task=task, generation=generation, owner=owner,
                                 identity=identity, kind='inert-sleep', state='running')
            return dict(job=key, pid=pid)

    def cancel(self, task, owner, complete=False):
        with self.transaction() as s:
            t = s['tasks'].get(task)
            if not t or t['owner'] != owner:
                raise Conflict('only task lead may cancel/complete')
            if complete and any(a['task'] == task and a['state'] in ('active','launching','launch-failed')
                                for a in s['assignments'].values()):
                raise Conflict('active helper assignment prevents completion; complete result or cancel explicitly')
            if complete and any(j['task'] == task and self.unresolved_job(j) for j in s['jobs'].values()):
                raise Conflict('live job prevents completion')
            t['state'] = 'complete' if complete else 'cancelled'
            t['generation'] += 1
            helpers_pending = []
            for a in s['assignments'].values():
                if a['task'] == task and a['state'] in ('active','launching','launch-failed'):
                    a['state'] = 'cancelled'
                    helpers_pending.append(dict(helper=a['helper'], pane=a['pane'],
                                                workspace=a['workspace'], tab=a['tab']))
            survivors = []
            for key, j in s['jobs'].items():
                if j['task'] != task or not self.unresolved_job(j):
                    continue
                identity = j['identity']
                try:
                    # pidfd binds signalling to this process, never a recycled PID/group.
                    fd = os.pidfd_open(identity['pid'])
                    try:
                        if process_identity(identity['pid']) == identity:
                            signal.pidfd_send_signal(fd, signal.SIGTERM)
                    finally:
                        os.close(fd)
                except ProcessLookupError:
                    pass
                except (OSError, AttributeError):
                    survivors.append(key)
                j['state'] = 'cancellation-requested'
            # Claims/leases stay owned until real owners release. Evidence is untouched.
            result = dict(task=task, state=t['state'], generation=t['generation'],
                        survivors=survivors, helpers_pending=helpers_pending, jobs_pending=[k for k,j in s['jobs'].items()
                        if j['task'] == task and self.unresolved_job(j)])
            session = t['topology'].get('session')
        # Cooperative cancellation: no agent/session killing. Hooks reject the old
        # generation before the next tool. Already-running unregistered work is a
        # survivor, not something this controller may kill by guessed ownership.
        result['helper_notifications'] = []
        if session and helpers_pending and not complete:
            try:
                agents = self.herdr(session,'agent','list')['agents']
                for helper in helpers_pending:
                    if any(a.get('pane_id') == helper['pane'] and a.get('workspace_id') == helper['workspace']
                           and a.get('tab_id') == helper['tab'] for a in agents):
                        self.herdr(session,'agent','prompt',helper['pane'],
                            f'[AGENT MESSAGE]\nFrom: coordination task {task}\nReply-to: Herdr session {session}, pane {owner}\nSubject: task cancellation\nTask {task} cancelled. Stop owned work gracefully, preserve edits/evidence, release only your turn claim at real turn end. Old generation results are rejected.')
                        result['helper_notifications'].append(helper['pane'])
            except (OSError, ValueError, KeyError, subprocess.SubprocessError) as e:
                result['notification_error'] = str(e)
        return result

    def live(self):
        """Compact active view, not an ever-growing history dump."""
        s = self.snapshot()
        return dict(version=1, reservation=s.get('reservation'),
            work_requested=self.maintenance_should_yield(),
            turns={owner:dict(c,liveness=identity_liveness(c.get('actor_identity'))) for owner,c in s['turns'].items()},
            leases=dict(s['leases']),
            tasks={k:v for k,v in s['tasks'].items() if v['state'] == 'active'},
            assignments={k:v for k,v in s['assignments'].items() if v['state'] in ('active','launching','launch-failed')},
            jobs={k:v for k,v in s['jobs'].items() if self.unresolved_job(v)})


def main(argv=None):
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--root', type=Path, default=DEFAULT_ROOT)
    sub = p.add_subparsers(dest='command', required=True)
    specs = {
        'live': [], 'authority': ['task', 'generation'], 'task-register': ['task', 'owner'],
        'task-cancel': ['task', 'owner'], 'task-complete': ['task', 'owner'],
        'turn-acquire': ['owner', 'area'], 'turn-release': ['owner'], 'turn-check': ['owner','path'], 'turn-scope': ['owner'],
        'maintenance-reserve': ['owner'], 'maintenance-release': ['owner'],
        'lease-acquire': ['task', 'generation', 'owner', 'resource'], 'lease-release': ['lease', 'owner'],
        'fixture-job': ['task', 'generation', 'owner'],
        'assign': ['task', 'owner', 'helper', 'workspace', 'tab', 'pane', 'area'],
        'result': ['assignment', 'generation'],
        'helper-launch': ['task', 'owner', 'helper', 'area', 'prompt', 'cwd']}
    for name, fields in specs.items():
        q = sub.add_parser(name)
        if name == 'authority':
            q.add_argument('--owner')
            q.add_argument('--operation')
        if name == 'turn-acquire':
            q.add_argument('--cwd')
            q.add_argument('--actor-pid',type=int)
            q.add_argument('--session-id')
            q.add_argument('--extend',action='store_true')
            q.add_argument('--task')
            q.add_argument('--generation',type=int)
        if name == 'task-register':
            q.add_argument('--helper-limit', type=int, default=4)
            q.add_argument('--operation', action='append', default=[])
        for f in fields:
            q.add_argument('--' + f, required=True, type=int if f == 'generation' else str,
                           **({'action': 'append'} if f == 'area' and name == 'turn-acquire' else {}))
    q = sub.add_parser('job-run')
    for field in ('task','owner','operation','cwd','log'):
        q.add_argument('--'+field,required=True)
    q.add_argument('--generation',required=True,type=int)
    q.add_argument('--budget-seconds',required=True,type=float)
    q.add_argument('argv',nargs=argparse.REMAINDER)
    a = p.parse_args(argv)
    try:
        c = Coordinator(a.root)
        cmd = a.command
        if cmd == 'live': r = c.live()
        elif cmd == 'authority': r = c.authority(a.task, a.generation, a.owner, a.operation)
        elif cmd == 'task-register': r = c.register(a.task, a.owner, helper_limit=a.helper_limit, operations=a.operation)
        elif cmd in ('task-cancel', 'task-complete'): r = c.cancel(a.task, a.owner, cmd == 'task-complete')
        elif cmd == 'turn-acquire':
            topology = {k: os.environ.get('HERDR_' + k.upper() + ('_ID' if k != 'session' else ''), '') for k in ('session','workspace','tab','pane')}
            r = c.claim(a.owner, a.area, task=a.task,generation=a.generation,topology=topology,cwd=a.cwd,
                        extend=a.extend,actor_pid=a.actor_pid,session_id=a.session_id)
        elif cmd == 'turn-release': r = c.release(a.owner)
        elif cmd == 'turn-check': r = c.covers(a.owner,a.path)
        elif cmd == 'turn-scope': r = c.scope(a.owner)
        elif cmd == 'maintenance-reserve': r = c.reserve(a.owner)
        elif cmd == 'maintenance-release': r = c.unreserve(a.owner)
        elif cmd == 'lease-acquire': r = c.lease(a.task,a.generation,a.owner,a.resource)
        elif cmd == 'lease-release': r = c.unlease(a.lease,a.owner)
        elif cmd == 'fixture-job': r = c.fixture_job(a.task,a.generation,a.owner)
        elif cmd == 'assign': r = c.assign(a.task,a.owner,a.helper,a.workspace,a.tab,a.pane,a.area)
        elif cmd == 'result': r = c.result(a.assignment,a.generation)
        elif cmd == 'helper-launch': r = c.launch_helper(a.task,a.owner,a.helper,a.area,a.prompt,a.cwd)
        elif cmd == 'job-run':
            command = a.argv[1:] if a.argv[:1] == ['--'] else a.argv
            r = c.run_job(a.task,a.generation,a.owner,a.operation,command,a.cwd,a.budget_seconds,a.log)
        print(json.dumps(r, sort_keys=True))
        if cmd == 'job-run':
            return 0 if r['status']==0 and r['cleanup_complete'] else 3
        return 3 if cmd in ('authority','turn-check','turn-scope') and not r['valid'] else 0
    except (Conflict, OSError, ValueError, KeyError, TypeError, AttributeError, subprocess.SubprocessError) as e:
        print(json.dumps(dict(valid=False, error=str(e))), file=sys.stderr)
        return 3

if __name__ == '__main__':
    sys.exit(main())

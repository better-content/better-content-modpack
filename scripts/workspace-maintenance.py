#!/usr/bin/env python3
"""Task-lifetime disposal. No candidate/evidence verdict grants retention."""
from __future__ import annotations
import argparse
import contextlib
import fcntl
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import sys
import time
import uuid

SCHEMA = 'bc.workspace_maintenance.v2'
ID = re.compile(r'^[a-zA-Z0-9][a-zA-Z0-9_.-]{0,119}$')
REPO_OUTPUTS = {'build', '.gradle', '.kotlin', 'generated', 'dist', 'logs',
                'crash-reports', 'screenshots', 'saves', 'world', 'server-instance',
                'server-template', '__pycache__'}
CODEX_OUTPUTS = {'sessions', 'generated_images', 'cache', '.tmp', 'tmp',
                 'shell_snapshots', 'history.jsonl', 'session_index.jsonl',
                 'models_cache.json', 'thread-writer-locks',
                 'tui-thread-reference-capabilities'}
GRADLE_OUTPUTS = {'caches', 'wrapper', 'daemon', 'native', '.tmp', 'workers',
                  'notifications', 'kotlin-profile', 'undefined-build'}
# Installed JDKs, packages, plugins, skills, auth/config and toolchains are inputs.
PROTECTED = ['.codex/auth.json', '.codex/config.toml', '.codex/AGENTS.md',
             '.codex/hooks.json', '.codex/packages', '.codex/plugins', '.codex/skills',
             '.pi/agent/auth.json', '.pi/agent/settings.json', '.pi/agent/extensions',
             '.sdkman', '.local/bin', '.local/lib', '.gradle/jdks',
             '.cache/ms-playwright', '.cache/selenium',
             'better-content-modpack/mods', 'workspace_artifacts/README.md']


def exists(path):
    return os.path.lexists(path)


def beneath(path, root):
    return path == root or root in path.parents


def signature(path):
    s = path.lstat()
    return [s.st_dev, s.st_ino, stat.S_IFMT(s.st_mode)]


def allocated(path):
    if not exists(path):
        return 0
    # du does not follow symbolic links; bytes are allocated rather than apparent.
    r = subprocess.run(['du', '-sk', '--', str(path)], capture_output=True, text=True)
    if r.returncode:
        raise RuntimeError('cannot measure target: ' + str(path))
    return int(r.stdout.split()[0]) * 1024


def git_inputs(repo):
    def git(*args):
        r = subprocess.run(['git', '-C', str(repo), *args], capture_output=True)
        if r.returncode:
            raise RuntimeError('cannot inspect Git inputs: ' + str(repo))
        return [repo / os.fsdecode(p) for p in r.stdout.split(b'\0') if p]
    # Unknown/untracked data outside explicit output roots is never classified.
    return git('ls-files', '-z') + git('ls-files', '--others', '--exclude-standard', '-z')


def discover(workspace):
    pack = workspace / 'better-content-modpack'
    repos = [pack] if (pack / '.git').exists() else []
    source = workspace / 'mod_source'
    if source.is_dir():
        repos += sorted(p for p in source.iterdir() if not p.is_symlink() and (p / '.git').exists())
    targets = {}
    def add(p, category):
        if exists(p):
            targets[p] = category
    for repo in repos:
        for p in repo.iterdir():
            if p.name in REPO_OUTPUTS or p.name == 'run' or p.name.startswith('run-'):
                if p.is_dir() or p.is_symlink():
                    add(p, 'repository output/fixture')
            elif p.suffix in {'.log', '.hprof'}:
                add(p, 'runtime log/dump')
    artifacts = workspace / 'workspace_artifacts'
    if artifacts.is_dir():
        for p in artifacts.iterdir():
            if p.name == 'README.md':
                continue
            if p.is_dir() and not p.is_symlink():
                for child in p.iterdir():
                    add(child, 'disposable review/delivery/evidence')
            else:
                add(p, 'disposable review/delivery/evidence')
    for name in ('.tmp', '.cache'):
        base = workspace / name
        if base.is_dir():
            for p in base.iterdir():
                add(p, 'temporary data/cache')
    for name in GRADLE_OUTPUTS:
        add(workspace / '.gradle' / name, 'shared rebuildable Gradle cache')
    for name in CODEX_OUTPUTS:
        add(workspace / '.codex' / name, 'agent generated state')
    codex = workspace / '.codex'
    if codex.is_dir():
        for p in codex.iterdir():
            if '.sqlite' in p.name or p.name.startswith('auth.json.worklane-backup-'):
                add(p, 'agent database/obsolete credential backup')
    pi = workspace / '.pi/agent'
    for name in ('web-search-cache', 'models-store.json'):
        add(pi / name, 'agent generated cache')
    sessions = pi / 'sessions'
    if sessions.is_dir():
        for p in sessions.rglob('*.jsonl'):
            add(p, 'agent session')
    minecraft = workspace / '.minecraft'
    if minecraft.is_dir():
        for name in ('saves', 'logs', 'crash-reports', 'screenshots'):
            add(minecraft / name, 'development world/runtime data')
    for name in ('forge-1.20.1-47.4.22-installer.jar.log', 'info:-'):
        add(workspace / name, 'abandoned installer/image diagnostic output')
    for name in ('bc-debug-refresh', 'bc-plan-dispatch', 'journal-debug'):
        add(workspace / '.local/state' / name, 'old task orchestration state')
    # Preserve tracked launcher libraries; remove only untracked download files.
    libraries = pack / 'libraries'
    if libraries.is_dir() and pack in repos:
        tracked = set(git_inputs(pack))
        for base, dirs, files in os.walk(libraries, followlinks=False):
            for name in files:
                p = Path(base) / name
                if p not in tracked:
                    add(p, 'rebuildable downloaded dependency')
    add(workspace / 'go/pkg/mod', 'shared rebuildable dependency cache')
    npm = workspace / '.npm'
    for name in ('_cacache', '_logs', '_npx'):
        add(npm / name, 'rebuildable npm cache')
    # Collapse nested targets without ever traversing a symlink.
    result = {}
    for p in sorted(targets, key=lambda p: (len(p.parts), str(p))):
        if not any(parent in result for parent in p.parents):
            result[p] = targets[p]
    return result, repos


def process_references(workspace):
    """Collect live cwd/open files/maps/absolute argv without emitting secrets."""
    ignored = {os.getpid()}
    pid = os.getppid()
    while pid > 1 and pid not in ignored:
        ignored.add(pid)
        try:
            pid = int(Path(f'/proc/{pid}/stat').read_text().rsplit(')', 1)[1].split()[1])
        except (OSError, ValueError):
            break
    refs = []
    for proc in Path('/proc').iterdir():
        if not proc.name.isdigit() or int(proc.name) in ignored:
            continue
        pid = int(proc.name)
        try:
            args = (proc / 'cmdline').read_bytes().split(b'\0')
            for arg in args:
                text = os.fsdecode(arg)
                if 'worklane-git-diff' in text and ' ' not in text:
                    refs.append((pid, workspace / '.cache/worklane'))
                # Only standalone absolute argv paths, not arbitrary shell scripts.
                if text.startswith(str(workspace) + '/') and '\n' not in text and ' ' not in text:
                    refs.append((pid, Path(text)))
                if text.startswith('-cp'):
                    continue
                for part in text.split(':'):
                    if part.startswith(str(workspace / '.gradle') + '/') and ' ' not in part:
                        refs.append((pid, Path(part)))
            for name in ('cwd', 'exe'):
                p = Path(os.readlink(proc / name).removesuffix(' (deleted)'))
                if beneath(p, workspace):
                    refs.append((pid, p))
            for fd in (proc / 'fd').iterdir():
                try:
                    p = Path(os.readlink(fd).removesuffix(' (deleted)'))
                    if p.is_absolute() and beneath(p, workspace):
                        refs.append((pid, p))
                except OSError:
                    pass
            for line in (proc / 'maps').read_text().splitlines():
                fields = line.split(None, 5)
                if len(fields) == 6 and fields[5].startswith(str(workspace) + '/'):
                    refs.append((pid, Path(fields[5].removesuffix(' (deleted)'))))
        except (FileNotFoundError, ProcessLookupError):
            continue
        except PermissionError as error:
            # Fail closed if a same-user process is not inspectable.
            # systemd's credential-isolated PAM helper has root-owned proc FDs and
            # cannot consume user task artifacts. Do not turn it into a workspace lease.
            if (proc / 'comm').read_text().strip() == '(sd-pam)':
                continue
            if proc.stat().st_uid == os.getuid():
                raise RuntimeError('cannot inspect process ' + str(pid)) from error
    # Pi does not continuously hold its session FD open. Herdr publishes identities.
    session = os.environ.get('HERDR_SESSION')
    if session:
        r = subprocess.run(['herdr', '--session', session, 'agent', 'list'], capture_output=True, text=True)
        if r.returncode:
            raise RuntimeError('cannot inspect Herdr agent sessions')
        for agent in json.loads(r.stdout)['result']['agents']:
            identity = agent.get('agent_session') or {}
            if identity.get('kind') == 'path' and identity.get('value'):
                refs.append(('Herdr:' + agent['pane_id'], Path(identity['value'])))
                if agent.get('agent') == 'pi':
                    refs.append(('Herdr:' + agent['pane_id'], workspace / '.pi/agent/models-store.json'))
                    refs.append(('Herdr:' + agent['pane_id'], workspace / '.pi/agent/web-search-cache'))
    return refs


def atomic_json(workspace, path, value):
    safe_path(workspace, path)
    if path.is_symlink():
        raise ValueError('unsafe state file')
    temp = path.with_name(path.name + '.' + uuid.uuid4().hex + '.new')
    fd = os.open(temp, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
    try:
        with os.fdopen(fd, 'w') as stream:
            json.dump(value, stream)
            stream.write('\n')
            stream.flush()
            os.fsync(stream.fileno())
        temp.replace(path)
    finally:
        if exists(temp):
            temp.unlink()


def load_tasks(workspace):
    p = safe_path(workspace, workspace / '.worklane/disposable-tasks.json')
    if p.is_symlink():
        raise ValueError('unsafe task registry')
    if not p.exists():
        return {}
    data = json.loads(p.read_text())
    if data.get('schema') != SCHEMA or not isinstance(data.get('tasks'), dict):
        raise RuntimeError('invalid active-task registry')
    return data['tasks']


def save_tasks(workspace, tasks):
    p = safe_path(workspace, workspace / '.worklane/disposable-tasks.json')
    if p.is_symlink():
        raise ValueError('unsafe task registry')
    if not tasks:
        p.unlink(missing_ok=True)
        return
    p.parent.mkdir(parents=True, exist_ok=True)
    atomic_json(workspace, p, {'schema': SCHEMA, 'tasks': tasks})


def safe_path(workspace, value):
    p = Path(value)
    if not p.is_absolute():
        p = workspace / p
    if '..' in p.parts or p == workspace or not beneath(p, workspace):
        raise ValueError('target escapes workspace or names workspace root')
    parent = p.parent
    while parent != workspace:
        if parent.is_symlink():
            raise ValueError('symbolic-link ancestor: ' + str(parent))
        parent = parent.parent
    return p


def scan_boundary(path, workspace):
    """Preflight mounts before deleting even the first child."""
    if path.is_symlink():
        return
    device = workspace.stat().st_dev
    # Same-device bind mounts are not reliably detected by os.path.ismount.
    for line in Path('/proc/self/mountinfo').read_text().splitlines():
        encoded = line.split()[4]
        mount = Path(re.sub(r'\\([0-7]{3})', lambda match: chr(int(match[1], 8)), encoded))
        if mount != workspace and beneath(mount, path):
            raise ValueError('unexpected mount boundary: ' + str(mount))
    for base, dirs, files in os.walk(path, followlinks=False):
        for p in [Path(base)] + [Path(base) / n for n in dirs + files]:
            s = p.lstat()
            if not stat.S_ISLNK(s.st_mode) and (s.st_dev != device or os.path.ismount(p)):
                raise ValueError('unexpected mount boundary: ' + str(p))


def shared_build_output(path, category):
    return category in {'shared rebuildable Gradle cache', 'shared rebuildable dependency cache'} or (
        category == 'repository output/fixture' and path.name in {'build', '.gradle', '.kotlin'})


def plan(workspace, refs=None, tasks=None):
    targets, repos = discover(workspace)
    refs = process_references(workspace) if refs is None else refs
    tasks = load_tasks(workspace) if tasks is None else tasks
    boundaries = [p for _, p in refs]
    leased_roots = []
    for task in tasks.values():
        until = task.get('pin_until')
        active = until > time.time() if until is not None else task.get('state', 'active') == 'active'
        if active:
            leased_roots += [safe_path(workspace, p) for p in task.get('paths', [])]
    boundaries += leased_roots
    # A live frame directory or exact recipe input does not protect sibling ZIPs/worlds.
    expanded = {}
    pending = list(targets.items())
    while pending:
        path, category = pending.pop()
        descendants = any(beneath(p, path) for p in boundaries)
        fully_leased = path in boundaries or any(beneath(path, p) for p in leased_roots)
        if category == 'disposable review/delivery/evidence' and descendants and not fully_leased \
                and path.is_dir() and not path.is_symlink():
            pending.extend((p, category) for p in path.iterdir())
        else:
            expanded[path] = category
    targets = dict(sorted(expanded.items(), key=lambda item: str(item[0])))
    inputs = [p for repo in repos for p in git_inputs(repo)]
    operating = [workspace / value for value in PROTECTED if exists(workspace / value)]
    inputs += operating
    protected_by_target = {}
    for p in inputs:
        for boundary in (p, *p.parents):
            if boundary in targets:
                protected_by_target.setdefault(boundary, []).append(str(p))
    decisions = []
    for path, category in targets.items():
        safe_path(workspace, path)
        reasons = []
        protected = protected_by_target.get(path, []) + [str(p) for p in operating if beneath(path, p)]
        owners = [str(pid) for pid, p in refs if beneath(p, path)]
        # A mapped Gradle distribution/cache implies a shared build consumer.
        if category == 'shared rebuildable Gradle cache':
            owners += [str(pid) for pid, p in refs if beneath(p, workspace / '.gradle')]
        # SQLite WAL/SHM belong to the database, not independent disposable files.
        if '.sqlite' in path.name:
            base = path.name.split('.sqlite')[0] + '.sqlite'
            owners += [str(pid) for pid, p in refs if p.parent == path.parent and p.name.startswith(base)]
        for task_id, task in tasks.items():
            until = task.get('pin_until')
            active = (until > time.time()) if until is not None else task.get('state', 'active') == 'active'
            for value in task.get('paths', []):
                p = safe_path(workspace, value)
                if active and (beneath(p, path) or beneath(path, p)):
                    owners.append('task:' + task_id)
            if active and shared_build_output(path, category) and task.get('uses_build_cache', True):
                owners.append('task:' + task_id)
        action = 'protect_input' if protected else 'defer_active' if owners else 'delete'
        if protected:
            reasons.append('authored, tracked or installed operating inputs inside output tree')
        if owners:
            reasons.append('active consumer')
        try:
            size = allocated(path)
        except RuntimeError:
            if not owners:
                raise
            size = 0
            reasons.append('active changing payload: size unavailable')
        decisions.append({'path': str(path), 'category': category, 'action': action,
                          'owners': sorted(set(owners)), 'protected_inputs': protected,
                          'reasons': reasons, 'bytes': size, 'identity': signature(path)})
    for relative in PROTECTED:
        p = workspace / relative
        if exists(p) and not any(d['path'] == str(p) for d in decisions):
            decisions.append({'path': str(p), 'category': 'operating/source input',
                              'action': 'protect_input', 'owners': [], 'reasons': ['required input'],
                              'protected_inputs': [], 'bytes': 0, 'identity': signature(p)})
    return decisions


def delete_checked(path, workspace, expected):
    safe_path(workspace, path)
    if not exists(path):
        return
    if signature(path) != expected:
        raise ValueError('target substituted: ' + str(path))
    scan_boundary(path, workspace)
    # fd-relative recursion plus O_NOFOLLOW: a concurrent symlink swap cannot escape.
    def mount_id(fd):
        return next(line for line in Path(f'/proc/self/fdinfo/{fd}').read_text().splitlines()
                    if line.startswith('mnt_id:'))
    anchor_mount = None
    def remove(parent_fd, name, identity=None):
        try:
            s = os.stat(name, dir_fd=parent_fd, follow_symlinks=False)
        except FileNotFoundError:
            return
        if identity is not None and [s.st_dev, s.st_ino, stat.S_IFMT(s.st_mode)] != identity:
            raise ValueError('target substituted during deletion')
        if stat.S_ISDIR(s.st_mode):
            fd = os.open(name, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW, dir_fd=parent_fd)
            try:
                opened = os.fstat(fd)
                if (opened.st_dev, opened.st_ino) != (s.st_dev, s.st_ino) or \
                        opened.st_dev != workspace.stat().st_dev or mount_id(fd) != anchor_mount:
                    raise ValueError('directory substituted or mount boundary')
                if not opened.st_mode & stat.S_IWUSR:
                    os.fchmod(fd, (opened.st_mode & 0o777) | 0o700)
                for child in os.listdir(fd):
                    remove(fd, child)
            finally:
                os.close(fd)
            after = os.stat(name, dir_fd=parent_fd, follow_symlinks=False)
            if (after.st_dev, after.st_ino) != (s.st_dev, s.st_ino):
                raise ValueError('directory substituted before removal')
            os.rmdir(name, dir_fd=parent_fd)
        else:
            os.unlink(name, dir_fd=parent_fd)
    # Anchor every parent component rather than following a substituted ancestor.
    fd = os.open(workspace, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)
    anchor_mount = mount_id(fd)
    try:
        for part in path.parent.relative_to(workspace).parts:
            next_fd = os.open(part, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW, dir_fd=fd)
            if mount_id(next_fd) != anchor_mount:
                os.close(next_fd)
                raise ValueError('mount boundary in target ancestor')
            os.close(fd)
            fd = next_fd
        remove(fd, path.name, expected)
    finally:
        os.close(fd)


def apply(workspace, decisions, transaction=None):
    state = workspace / '.worklane/disposal'
    if state.parent.is_symlink() or state.is_symlink():
        raise ValueError('unsafe disposal state directory')
    state.mkdir(parents=True, exist_ok=True)
    if transaction and not re.fullmatch(r'[a-f0-9]{32}', transaction):
        raise ValueError('invalid transaction ID')
    record = state / ((transaction or uuid.uuid4().hex) + '.json')
    if record.is_symlink():
        raise ValueError('unsafe transaction record')
    if transaction:
        data = json.loads(record.read_text())
        if data.get('schema') != SCHEMA or data.get('workspace') != str(workspace):
            raise ValueError('invalid disposal transaction')
        old = {d['path']: d for d in data['pending']}
        current = {d['path']: d for d in decisions}
        pending = []
        for path, d in old.items():
            if exists(Path(path)):
                if path not in current or current[path]['action'] != 'delete':
                    raise ValueError('resumed target now active or unclassified: ' + path)
                if signature(Path(path)) != d['identity']:
                    raise ValueError('resumed target substituted: ' + path)
                pending.append(d)
    else:
        pending = [d for d in decisions if d['action'] == 'delete']
    def checkpoint():
        atomic_json(workspace, record, {'schema': SCHEMA, 'workspace': str(workspace), 'pending': pending})
    checkpoint()
    removed = []
    while pending:
        d = pending[0]
        # Check live consumers and task leases again immediately before each target.
        fresh_refs = process_references(workspace)
        tasks = load_tasks(workspace)
        path = Path(d['path'])
        consumers = any(beneath(p, path) for _, p in fresh_refs)
        if d['category'] == 'shared rebuildable Gradle cache':
            consumers |= any(beneath(p, workspace / '.gradle') for _, p in fresh_refs)
        if '.sqlite' in path.name:
            base = path.name.split('.sqlite')[0] + '.sqlite'
            consumers |= any(p.parent == path.parent and p.name.startswith(base) for _, p in fresh_refs)
        if consumers:
            raise RuntimeError('target became active; resume after consumer exits: ' + d['path'])
        for task in tasks.values():
            until = task.get('pin_until')
            active = until > time.time() if until is not None else task.get('state', 'active') == 'active'
            if active and (any(beneath(safe_path(workspace, p), path) or beneath(path, safe_path(workspace, p))
                               for p in task.get('paths', [])) or
                           (shared_build_output(path, d['category']) and task.get('uses_build_cache', True))):
                raise RuntimeError('target leased during deletion')
        # Concurrent source authoring changes an output tree into an input boundary.
        repo = next((p for p in path.parents if (p / '.git').exists()), None)
        if repo and any(beneath(p, path) for p in git_inputs(repo)):
            raise RuntimeError('target now contains authored input: ' + str(path))
        delete_checked(Path(d['path']), workspace, d['identity'])
        removed.append(d['path'])
        pending.pop(0)
        if len(removed) % 10 == 0:
            checkpoint()
    record.unlink()
    return removed


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--workspace', type=Path, default=Path(__file__).resolve().parents[2])
    sub = parser.add_subparsers(dest='command', required=True)
    sub.add_parser('audit')
    prune = sub.add_parser('prune')
    group = prune.add_mutually_exclusive_group(required=True)
    group.add_argument('--apply', action='store_true')
    group.add_argument('--resume')
    begin = sub.add_parser('begin')
    begin.add_argument('--task', required=True)
    begin.add_argument('--path', action='append', default=[])
    begin.add_argument('--pin-until', type=float)
    begin.add_argument('--no-build-cache', action='store_true', help='read-only artifact consumer; no build-cache lease')
    finish = sub.add_parser('finish')
    finish.add_argument('--task', required=True)
    finish.add_argument('--apply', action='store_true', required=True)
    args = parser.parse_args(argv)
    workspace = args.workspace.absolute()
    if workspace.is_symlink() or not workspace.is_dir():
        raise ValueError('workspace must be a real directory')
    workspace = workspace.resolve()
    # Audit never creates lock/state files. Mutations serialize registry and deletion.
    if args.command == 'audit':
        decisions = plan(workspace)
        print(json.dumps({'schema': SCHEMA, 'workspace': str(workspace), 'decisions': decisions,
                          'reclaimable_bytes': sum(d['bytes'] for d in decisions if d['action'] == 'delete')}, indent=2))
        return 0
    lock_root = workspace / '.worklane'
    lock_root.mkdir(exist_ok=True)
    if lock_root.is_symlink():
        raise ValueError('unsafe state directory')
    lock_path = lock_root / 'disposal.lock'
    fd = os.open(lock_path, os.O_RDWR | os.O_CREAT | os.O_NOFOLLOW, 0o600)
    try:
        fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
        tasks = load_tasks(workspace)
        if args.command in {'begin', 'finish'} and not ID.fullmatch(args.task):
            raise ValueError('invalid task ID')
        if args.command == 'begin':
            if args.task in tasks:
                raise ValueError('task already registered')
            paths = [str(safe_path(workspace, p)) for p in args.path]
            if args.pin_until is not None and args.pin_until <= time.time():
                raise ValueError('pin expiration must be in the future')
            tasks[args.task] = {'state': 'active', 'paths': paths, 'pin_until': args.pin_until,
                                'uses_build_cache': not args.no_build_cache and args.pin_until is None}
            save_tasks(workspace, tasks)
            print(json.dumps({'schema': SCHEMA, 'registered': args.task}))
            return 0
        if args.command == 'finish':
            if args.task not in tasks:
                raise ValueError('task is not registered')
            tasks[args.task]['state'] = 'finished'
            save_tasks(workspace, tasks)
        decisions = plan(workspace)
        removed = apply(workspace, decisions, getattr(args, 'resume', None))
        if args.command == 'finish':
            tasks = load_tasks(workspace)
            # An explicit pin survives finish only until its declared expiration.
            if (tasks[args.task].get('pin_until') or 0) <= time.time():
                del tasks[args.task]
            save_tasks(workspace, tasks)
        if args.command == 'prune':
            tasks = load_tasks(workspace)
            tasks = {task_id: task for task_id, task in tasks.items()
                     if (task.get('pin_until') is not None and task['pin_until'] > time.time()) or
                     (task.get('pin_until') is None and task.get('state', 'active') == 'active')}
            save_tasks(workspace, tasks)
        remaining = [d for d in decisions if d['action'] == 'defer_active']
        print(json.dumps({'schema': SCHEMA, 'removed_count': len(removed),
                          'removed_bytes': sum(d['bytes'] for d in decisions if d['action'] == 'delete'),
                          'deferred': remaining}, indent=2))
        return 3 if remaining else 0
    finally:
        os.close(fd)


if __name__ == '__main__':
    try:
        sys.exit(main())
    except (OSError, ValueError, RuntimeError, json.JSONDecodeError) as error:
        print('maintenance failed: ' + str(error), file=sys.stderr)
        sys.exit(1)

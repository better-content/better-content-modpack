#!/usr/bin/env python3
"""Synthetic only; all fixture outputs deliberately retained for review."""
import json
import multiprocessing
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import threading
import time
import unittest
from unittest.mock import patch
from workspace_coordination import Coordinator, Conflict, process_identity

SCRIPT = Path(__file__).with_name('workspace_coordination.py')


def claim_worker(root, owner, event, results):
    event.wait()
    try:
        Coordinator(root).claim(owner, ['same-file'])
        results.put(True)
    except Conflict:
        results.put(False)


class CoordinationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        base = Path('/home/dev/.tmp')
        base.mkdir(parents=True, exist_ok=True)
        cls.fixtures = Path(tempfile.mkdtemp(prefix='coordination-tests-', dir=base))
        print(f'RETAINED_FIXTURES={cls.fixtures}', flush=True)

    def setUp(self):
        self.root = self.fixtures / self._testMethodName
        self.c = Coordinator(self.root)

    def test_generation_and_lead(self):
        t = self.c.register('task', 'lead', {'workspace':'lead-workspace'})
        self.assertEqual(t['generation'], 1)
        with self.assertRaises(Conflict): self.c.register('task', 'other')
        a = self.c.assign('task','lead','helper','helper-workspace','tab','pane','file')
        with self.assertRaises(Conflict): self.c.assign('task','helper','nested','other','t2','p2','file')
        with self.assertRaises(Conflict): self.c.assign('task','lead','h2','lead-workspace','t3','p3','f')
        self.assertFalse(self.c.authority('task',1,'wrong')['valid'])
        self.c.cancel('task','lead')
        with self.assertRaises(Conflict): self.c.result(a['assignment'],1)
        self.assertFalse(self.c.authority('task',1)['valid'])
        self.assertEqual(self.c.register('task','lead')['generation'],3)
        self.assertFalse(self.c.authority('task',1)['valid'])

    def test_helper_bound_and_result(self):
        self.c.register('t','lead')
        assignments=[]
        for i in range(4):
            assignments.append(self.c.assign('t','lead',f'h{i}','helpers',f't{i}',f'p{i}','f'))
        with self.assertRaises(Conflict): self.c.assign('t','lead','h4','helpers','t4','p4','f')
        self.assertTrue(self.c.result(assignments[0]['assignment'],1)['accepted'])
        with self.assertRaises(Conflict): self.c.result(assignments[0]['assignment'],1)

    def test_atomic_writer_claims(self):
        event = multiprocessing.Event()
        results = multiprocessing.Queue()
        workers=[multiprocessing.Process(target=claim_worker,args=(self.root,f'o{i}',event,results)) for i in range(6)]
        for w in workers: w.start()
        event.set()
        answers=[results.get(timeout=10) for _ in workers]
        for w in workers:
            w.join(10)
            self.assertEqual(w.exitcode,0)
        self.assertEqual(sum(answers),1)
        self.assertEqual(len(self.c.live()['turns']),1)

    def test_turn_release_does_not_end_assignment_or_lease(self):
        self.c.register('t','lead')
        a=self.c.assign('t','lead','helper','helpers','tab','pane','f')
        lease=self.c.lease('t',1,'helper','build-cache')['lease']
        self.c.claim('helper',['f'],'t',1)
        self.c.release('helper')
        live=self.c.live()
        self.assertIn(lease,live['leases'])
        self.assertIn(a['assignment'],live['assignments'])
        self.assertTrue(self.c.authority('t',1)['valid'])
        with self.assertRaises(Conflict): self.c.unlease(lease,'other')
        self.c.unlease(lease,'helper')

    def test_reservation_and_nonblocking_new_work_yield(self):
        self.c.reserve('maintenance-pane', live_agents=[])
        with self.c.maintenance('maintenance-pane',live_agents=[]):
            p=subprocess.run([sys.executable,'-B',str(SCRIPT),'--root',str(self.root),
                'turn-acquire','--owner','new','--area','f'],capture_output=True,text=True,timeout=5)
            self.assertEqual(p.returncode,3)
            self.assertTrue(self.c.maintenance_should_yield())
        self.c.unreserve('maintenance-pane')
        self.c.claim('new',['f'])
        self.assertFalse(self.c.maintenance_should_yield())

    def test_reservation_denies_admission_even_without_held_lock(self):
        self.c.reserve('m', live_agents=[])
        with self.assertRaises(Conflict): self.c.register('t','lead')
        self.assertTrue(self.c.maintenance_should_yield())
        with self.assertRaises(Conflict): self.c.unreserve('wrong')
        self.c.unreserve('m')
        self.c.register('t','lead')
        with self.assertRaises(Conflict):
            self.c.reserve('m', live_agents=[])

    def test_herdr_quiet_gate(self):
        with self.assertRaises(Conflict):
            self.c.reserve('m',live_agents=[{'pane_id':'m','agent_status':'working'}])
        self.c.reserve('m', live_agents=[])
        for state in ('working','unknown','blocked'):
            with self.assertRaises(Conflict):
                with self.c.maintenance('m',live_agents=[{'pane_id':'foreign','agent_status':state}]): pass
        with self.c.maintenance('m',live_agents=[{'pane_id':'foreign','agent_status':'idle'}, {'pane_id':'m','agent_status':'working'}]):
            pass

    def test_owned_cancellation_preserves_evidence_and_foreign_job(self):
        evidence=self.root/'evidence.txt'
        self.c.register('t','lead')
        self.c.register('other','foreign')
        evidence.write_text('retain exactly')
        owned=self.c.fixture_job('t',1,'lead')
        foreign=self.c.fixture_job('other',1,'foreign')
        try:
            self.c.cancel('t','lead')
            for _ in range(100):
                if process_identity(owned['pid']) is None: break
                time.sleep(.01)
            self.assertIsNone(process_identity(owned['pid']))
            self.assertIsNotNone(process_identity(foreign['pid']))
            self.assertEqual(evidence.read_text(),'retain exactly')
        finally:
            self.c.cancel('other','foreign')
            for pid in (owned['pid'],foreign['pid']):
                try: os.waitpid(pid,0)
                except ChildProcessError: pass

    def test_identity_mismatch_does_not_signal(self):
        self.c.register('t','lead')
        job=self.c.fixture_job('t',1,'lead')
        try:
            with patch('workspace_coordination.process_identity',return_value={'pid':job['pid'],'start':'different','boot':'different'}), patch('workspace_coordination.signal.pidfd_send_signal') as send:
                self.c.cancel('t','lead')
                send.assert_not_called()
            self.assertIsNotNone(process_identity(job['pid']))
        finally:
            self.c.cancel('t','lead')
            os.waitpid(job['pid'],0)

    def test_symlink_state_and_request_rejected(self):
        self.c.prepare()
        target=self.root/'input'
        target.write_text('protected')
        (self.root/'work-requested').symlink_to(target)
        with self.assertRaises(OSError): self.c.request_work()
        self.assertEqual(target.read_text(),'protected')
        (self.root/'state.json').symlink_to(target)
        with self.assertRaises((Conflict, OSError)): self.c.live()
        linked=self.root/'alias'
        linked.symlink_to(self.root, target_is_directory=True)
        with self.assertRaises(Conflict): Coordinator(linked/'state').live()

    def test_path_alias_parent_overlap_and_atomic_extension(self):
        work=self.root/'repo'
        work.mkdir(parents=True)
        self.c.claim('parent',['dir'],cwd=work)
        with self.assertRaises(Conflict): self.c.claim('child',[str(work/'dir'/'file.py')])
        with self.assertRaises(Conflict): self.c.claim('alias',['./dir/../dir/file.py'],cwd=work)
        self.c.claim('other',['sibling'],cwd=work)
        self.c.claim('other',['sibling/child'],cwd=work,extend=True)
        self.assertTrue(self.c.covers('other',str(work/'sibling/child/file'))['valid'])
        self.assertFalse(self.c.covers('other',str(work))['valid'])
        self.c.release('parent')
        self.c.claim('child',['dir/file.py'],cwd=work)
        with self.assertRaises(Conflict): self.c.claim('parent',['dir'],cwd=work)
        self.c.claim('semantic',['token:project'])
        self.c.claim('not-semantic',['token:project-other'])
        with self.assertRaises(Conflict): self.c.claim('same-token',['token:project'])

    def test_operation_bound_authority_and_explicit_six_helpers(self):
        self.c.register('t','lead',{'workspace':'lead'},helper_limit=6,operations=['source-edit','dev','docs','synthetic'])
        self.assertTrue(self.c.authority('t',1,'lead','synthetic')['valid'])
        self.assertFalse(self.c.authority('t',1,None,'synthetic')['valid'])
        self.assertFalse(self.c.authority('t',1,'wrong','synthetic')['valid'])
        self.assertFalse(self.c.authority('t',1,'lead','pack-test:dist')['valid'])
        assignments=[self.c.assign('t','lead',f'h{i}','helpers',f't{i}',f'p{i}','f') for i in range(6)]
        with self.assertRaises(Conflict): self.c.assign('t','lead','extra','helpers','tx','px','f')
        with self.assertRaises(Conflict): self.c.cancel('t','lead',complete=True)
        self.c.result(assignments[0]['assignment'],1)
        self.c.assign('t','lead','extra','helpers','tx','px','f')

    def test_live_and_authority_are_read_only_during_maintenance(self):
        missing=Coordinator(self.root/'missing')
        self.assertFalse(missing.authority('t',1)['valid'])
        self.assertFalse((self.root/'missing').exists())
        self.c.reserve('m',live_agents=[])
        before=(self.root/'state.json').stat().st_mtime_ns
        with self.c.maintenance('m',live_agents=[]):
            p=subprocess.run([sys.executable,'-B',str(SCRIPT),'--root',str(self.root),'live'],capture_output=True,text=True,timeout=3)
            self.assertEqual(p.returncode,0)
            self.assertEqual(json.loads(p.stdout)['reservation']['owner'],'m')
        self.assertEqual(before,(self.root/'state.json').stat().st_mtime_ns)

    def test_production_supervisor_cancels_owned_descendant(self):
        self.c.register('t','lead',operations=['synthetic'])
        fixture=self.root/'producer.py'
        fixture.write_text('''import subprocess,sys,time,os,json
from pathlib import Path
p=subprocess.Popen([sys.executable,"-c","import time;time.sleep(60)"],start_new_session=True)
Path(sys.argv[1]).write_text(json.dumps({"parent":os.getpid(),"child":p.pid}))
time.sleep(60)
''')
        identities=self.root/'identities.json'
        result={}
        errors=[]
        def run():
            try: result.update(self.c.run_job('t',1,'lead','synthetic',[sys.executable,str(fixture),str(identities)],self.root,30,self.root/'job.log'))
            except Exception as e: errors.append(e)
        thread=threading.Thread(target=run)
        thread.start()
        try:
            for _ in range(300):
                if identities.exists(): break
                time.sleep(.01)
            self.assertTrue(identities.exists())
            pids=json.loads(identities.read_text())
            self.assertIsNotNone(process_identity(pids['child']))
            cancel=self.c.cancel('t','lead')
            self.assertEqual(cancel['state'],'cancelled')
            thread.join(10)
            self.assertFalse(thread.is_alive())
            self.assertEqual(errors,[])
            self.assertTrue(result['cleanup_complete'])
            self.assertEqual(result['surviving_pids'],[])
            for pid in pids.values(): self.assertIsNone(process_identity(pid))
            self.assertTrue((self.root/'job.log.diagnostic.json').exists())
        finally:
            self.c.cancel('t','lead')
            thread.join(10)

    def test_live_classifies_exact_actor_identity_without_releasing_state(self):
        self.c.register('t','lead')
        lease=self.c.lease('t',1,'actor','token:retained-resource')['lease']
        self.c.claim('manual',['token:manual'])
        self.assertEqual(self.c.live()['turns']['manual']['liveness'],'unknown')
        process=subprocess.Popen([sys.executable,'-B','-c','import time;time.sleep(30)'])
        try:
            self.c.claim('actor',['token:actor'],actor_pid=process.pid,session_id='stable-conversation')
            before=(self.root/'state.json').read_bytes()
            live=self.c.live()
            self.assertEqual(live['turns']['actor']['liveness'],'live')
            self.assertEqual(live['turns']['actor']['session_id'],'stable-conversation')
            self.assertEqual(live['turns']['actor']['actor_identity'],process_identity(process.pid))
            with patch('workspace_coordination.process_identity',return_value={
                    'pid':process.pid,'start':'reused-start','boot':'same-or-different-boot'}):
                self.assertEqual(self.c.live()['turns']['actor']['liveness'],'stale')
            with patch('workspace_coordination.process_identity',return_value=None):
                # A still-existing but unreadable identity is unknown, not guessed dead.
                self.assertEqual(self.c.live()['turns']['actor']['liveness'],'unknown')
            process.terminate()
            process.wait(timeout=5)
            live=self.c.live()
            self.assertEqual(live['turns']['actor']['liveness'],'stale')
            self.assertIn('actor',live['turns'])
            self.assertIn(lease,live['leases'])
            self.assertEqual(before,(self.root/'state.json').read_bytes())
            self.assertTrue(self.c.authority('t',1)['valid'],'actor death does not end assigned task')
        finally:
            if process.poll() is None:
                process.terminate()
                process.wait(timeout=5)

    def test_supervisor_failure_requires_valid_status_bound_cleanup_receipt(self):
        self.c.register('t','lead',operations=['synthetic'])
        script=self.root/'inert.py'
        script.write_text('pass\n')
        original=subprocess.Popen
        cases=[
            (1,None,False), (2,None,False), (0,None,True),
            (1,{'schema':'bc.bounded_facade.v1','status':1,'surviving_pids':[]},True),
            (1,{'schema':'wrong','status':1,'surviving_pids':[]},False),
            (1,{'schema':'bc.bounded_facade.v1','status':2,'surviving_pids':[]},False),
            (1,{'schema':'bc.bounded_facade.v1','surviving_pids':[]},False),
            (1,{'schema':'bc.bounded_facade.v1','status':1},False),
            (1,{'schema':'bc.bounded_facade.v1','status':True,'surviving_pids':[]},False),
            (1,{'schema':'bc.bounded_facade.v1','status':1,'surviving_pids':'none'},False),
            (1,{'schema':'bc.bounded_facade.v1','status':1,'surviving_pids':[True]},False),
            (-15,{'schema':'bc.bounded_facade.v1','status':-15,'surviving_pids':[]},False),
            (0,'malformed',False),
            (1,{'schema':'bc.bounded_facade.v1','status':1,'surviving_pids':[99999999]},False),
        ]
        for index,(status,receipt,expected) in enumerate(cases):
            def supervisor_failure(argv,**kwargs):
                log=Path(argv[argv.index('--log')+1])
                if receipt is not None:
                    log.with_name(log.name+'.diagnostic.json').write_text(
                        '{malformed' if receipt=='malformed' else json.dumps(receipt))
                code='import time,os,signal;time.sleep(.02);'+(
                    'os.kill(os.getpid(),signal.SIGTERM)' if status<0 else f'raise SystemExit({status})')
                return original([sys.executable,'-B','-c',code],**kwargs)
            with self.subTest(status=status,receipt=receipt), patch('workspace_coordination.subprocess.Popen',side_effect=supervisor_failure):
                result=self.c.run_job('t',1,'lead','synthetic',[sys.executable,str(script)],self.root,5,self.root/f'case-{index}.log')
                self.assertEqual(result['cleanup_complete'],expected)
                self.assertEqual(result['status'],status)
                job=self.c.snapshot()['jobs'][result['job']]
                self.assertEqual(job['state'],'complete' if expected else 'cleanup-unknown')
                if not expected:
                    self.assertIn(result['job'],self.c.live()['jobs'])

    def test_helper_launch_and_cancellation_use_inert_herdr(self):
        self.root.mkdir(parents=True)
        fake=self.root/'bin'
        fake.mkdir()
        executable=fake/'herdr'
        state=self.root/'herdr.json'
        executable.write_text('#!'+sys.executable+'\n'+'''import json,sys
from pathlib import Path
path=Path('''+repr(str(state))+''')
s=json.loads(path.read_text()) if path.exists() else {'calls':[],'tabs':0}
a=sys.argv[1:];s['calls'].append(a);cmd=a[2:]
r={}
if cmd[:2]==['workspace','create']:r={'workspace_id':'helpers'}
elif cmd[:2]==['tab','create']:
 s['tabs']+=1;r={'tab_id':'tab'+str(s['tabs'])}
elif cmd[:2]==['pane','list']:r={'panes':[{'workspace_id':'helpers','tab_id':'tab'+str(i),'pane_id':'pane'+str(i)} for i in range(1,s['tabs']+1)]}
elif cmd[:2]==['agent','list']:r={'agents':[{'workspace_id':'helpers','tab_id':'tab'+str(i),'pane_id':'pane'+str(i)} for i in range(1,s['tabs']+1)]}
path.write_text(json.dumps(s));print(json.dumps({'result':r}))
''')
        executable.chmod(0o700)
        env={'PATH':str(fake)+os.pathsep+os.environ['PATH'],'HERDR_SESSION':'fixture','HERDR_PANE_ID':'lead-pane'}
        with patch.dict(os.environ,env):
            self.c.register('t','lead',{'session':'fixture','workspace':'lead-workspace','pane':'lead-pane'})
            one=self.c.launch_helper('t','lead','h1','token:a','[AGENT MESSAGE]\nFrom: lead\nReply-to: fixture',self.root)
            two=self.c.launch_helper('t','lead','h2','token:b','[AGENT MESSAGE]\nFrom: lead\nReply-to: fixture',self.root)
            self.assertEqual(one['workspace'],two['workspace'])
            self.assertNotEqual(one['tab'],two['tab'])
            result=self.c.cancel('t','lead')
            self.assertEqual(set(result['helper_notifications']),{'pane1','pane2'})
            calls=json.loads(state.read_text())['calls']
            self.assertEqual(sum(a[2:4]==['workspace','create'] for a in calls),1)
            self.assertEqual(sum(a[2:4]==['tab','create'] for a in calls),2)
            self.assertFalse(any('split' in a for a in calls))
            with self.assertRaises(Conflict): self.c.result(one['assignment'],1)

    def recovery_fixture(self, name='', helper=False):
        c = Coordinator(self.root / name) if name else self.c
        top = dict(session='fixture', workspace='lead-workspace', tab='lead-tab', pane='lead-pane')
        c.register('recover', 'old' if not helper else 'lead', top, operations=['dev'])
        if helper:
            top = dict(top, workspace='helpers', tab='helper-tab', pane='helper-pane')
            c.assign('recover', 'lead', 'helper', top['workspace'], top['tab'], top['pane'], 'token:old')
        identity = dict(process_identity(os.getpid()), boot='00000000-0000-0000-0000-000000000000')
        with patch('workspace_coordination.process_identity', return_value=identity):
            c.claim('old', ['token:old', 'identity:old'], 'recover' if helper else None,
                    1 if helper else None, top, actor_pid=os.getpid(), session_id='conversation')
        c.claim('new', ['token:new'], topology=top, actor_pid=os.getpid(), session_id='conversation')
        env = {'HERDR_'+k.upper()+('_ID' if k != 'session' else ''):v for k,v in top.items()}
        agent = dict(pane_id=top['pane'], tab_id=top['tab'], workspace_id=top['workspace'],
                     agent_status='working', agent_session={'kind':'path', 'value':'/fixture/start_conversation.jsonl'})
        return c, env, agent

    def recover(self, c, env, agent):
        with patch.dict(os.environ, env), patch.object(Coordinator, 'agents', return_value=[agent]):
            return c.reboot_recover('recover', 1, 'old', 'new', 'explicit reboot recovery')

    def test_reboot_lead_transfer_audited_preserves_task_assignments_foreign_state(self):
        c, env, agent = self.recovery_fixture()
        c.assign('recover', 'old', 'helper', 'helpers', 'helper-tab', 'helper-pane', 'token:helper')
        c.register('unrelated', 'foreign')
        c.claim('foreign', ['token:foreign'])
        before = c.snapshot()
        result = self.recover(c, env, agent)
        after = c.snapshot()
        self.assertTrue(result['lead_transfer'])
        self.assertEqual(after['tasks']['recover'], dict(before['tasks']['recover'], owner='new'))
        self.assertEqual(after['tasks']['unrelated'], before['tasks']['unrelated'])
        self.assertEqual(after['assignments'], before['assignments'])
        self.assertEqual(after['turns']['foreign'], before['turns']['foreign'])
        self.assertNotIn('old', after['turns'])
        self.assertEqual(after['turns']['new']['areas'], ['token:new', 'token:old'])
        self.assertEqual(after['recoveries'][result['recovery']]['previous_claim'], before['turns']['old'])
        with self.assertRaises(Conflict): self.recover(c, env, agent)

    def test_reboot_helper_restores_claim_without_changing_lead_or_assignment(self):
        c, env, agent = self.recovery_fixture(helper=True)
        before = c.snapshot()
        self.assertFalse(self.recover(c, env, agent)['lead_transfer'])
        after = c.snapshot()
        self.assertEqual(after['tasks'], before['tasks'])
        self.assertEqual(after['assignments'], before['assignments'])
        self.assertEqual(after['turns']['new']['actor_identity'], before['turns']['new']['actor_identity'])
        self.assertEqual(after['turns']['new']['task'], 'recover')
        self.assertEqual(after['turns']['new']['generation'], 1)

    def test_reboot_helper_survives_second_boot_with_preserved_binding(self):
        self.second_boot_helper(legacy=False)

    def test_reboot_helper_legacy_unbound_claim_requires_exact_audit(self):
        self.second_boot_helper(legacy=True)

    def second_boot_helper(self, legacy):
        c, env, agent = self.recovery_fixture(helper=True)
        self.recover(c, env, agent)
        if legacy:
            # Reproduce only the initial implementation's recorded omission.
            with c.transaction() as s:
                s['turns']['new']['task'] = None
                s['turns']['new']['generation'] = None
        identity = dict(process_identity(os.getpid()), boot='11111111-1111-1111-1111-111111111111')
        with patch('workspace_coordination.process_identity', return_value=identity):
            c.claim('newer', ['token:second'], topology=c.snapshot()['turns']['new']['topology'],
                    actor_pid=os.getpid(), session_id='conversation')
            with patch.dict(os.environ, env), patch.object(Coordinator, 'agents', return_value=[agent]):
                if legacy:
                    before = (c.root/'state.json').read_bytes()
                    with c.transaction() as s:
                        audit = next(iter(s['recoveries'].values()))
                        audit['current_claim_before']['session_id'] = 'wrong'
                    with self.assertRaises(Conflict): c.reboot_recover('recover', 1, 'new', 'newer', 'second boot')
                    with c.transaction() as s:
                        next(iter(s['recoveries'].values()))['current_claim_before']['session_id'] = 'conversation'
                    self.assertEqual(before, (c.root/'state.json').read_bytes())
                c.reboot_recover('recover', 1, 'new', 'newer', 'second boot')
        after = c.snapshot()
        self.assertEqual(after['turns']['newer']['task'], 'recover')
        self.assertEqual(after['turns']['newer']['generation'], 1)
        self.assertEqual(len(after['recoveries']), 2)
        self.assertEqual(after['tasks']['recover']['owner'], 'lead')

    def test_reboot_rejects_live_unknown_and_same_boot_actors_atomically(self):
        for kind in ('live', 'unknown', 'same-boot', 'new-dead'):
            with self.subTest(kind=kind):
                c, env, agent = self.recovery_fixture(kind)
                with c.transaction() as s:
                    if kind == 'live': s['turns']['old']['actor_identity'] = process_identity(os.getpid())
                    elif kind == 'unknown': s['turns']['old']['actor_identity'] = None
                    elif kind == 'new-dead': s['turns']['new']['actor_identity']['boot'] = '00000000-0000-0000-0000-000000000000'
                    else: s['turns']['old']['actor_identity'] = dict(process_identity(os.getpid()), start='different')
                before = (c.root/'state.json').read_bytes()
                with self.assertRaises(Conflict): self.recover(c, env, agent)
                self.assertEqual(before, (c.root/'state.json').read_bytes())

    def test_reboot_rejects_wrong_session_pane_and_live_conversation(self):
        for kind in ('conversation', 'pane', 'herdr-session', 'live-conversation', 'unknown-agent'):
            with self.subTest(kind=kind):
                c, env, agent = self.recovery_fixture(kind)
                if kind == 'conversation':
                    with c.transaction() as s: s['turns']['new']['session_id'] = 'wrong'
                elif kind == 'pane': env['HERDR_PANE_ID'] = 'wrong'
                elif kind == 'herdr-session': env['HERDR_SESSION'] = 'wrong'
                elif kind == 'unknown-agent': agent['agent_status'] = 'unknown'
                else: agent['agent_session']['value'] = '/fixture/wrong.jsonl'
                before = (c.root/'state.json').read_bytes()
                with self.assertRaises(Conflict): self.recover(c, env, agent)
                self.assertEqual(before, (c.root/'state.json').read_bytes())

    def test_reboot_rejects_leases_jobs_reservations_and_foreign_membership(self):
        for kind in ('lease', 'job', 'reservation', 'foreign-task', 'generation', 'overlap'):
            with self.subTest(kind=kind):
                c, env, agent = self.recovery_fixture(kind)
                with c.transaction() as s:
                    if kind == 'lease': s['leases']['held'] = dict(task='recover', owner='old', resource='cache')
                    elif kind == 'job': s['jobs']['pending'] = dict(task='recover', owner='old', kind='bounded-facade', state='interrupted')
                    elif kind == 'reservation': s['reservation'] = dict(owner='maintenance')
                    elif kind == 'foreign-task': s['turns']['old']['task'] = 'foreign'
                    elif kind == 'generation': s['tasks']['recover']['generation'] = 2
                    else: s['turns']['foreign'] = dict(s['turns']['old'], areas=['token:old'])
                before = (c.root/'state.json').read_bytes()
                with self.assertRaises(Conflict): self.recover(c, env, agent)
                self.assertEqual(before, (c.root/'state.json').read_bytes())

    def test_reboot_transfer_conflicting_requests_have_one_atomic_winner(self):
        c, env, agent = self.recovery_fixture()
        results = []
        def attempt():
            try: c.reboot_recover('recover', 1, 'old', 'new', 'race fixture'); results.append(True)
            except Conflict: results.append(False)
        with patch.dict(os.environ, env), patch.object(Coordinator, 'agents', return_value=[agent]):
            threads = [threading.Thread(target=attempt) for _ in range(2)]
            for thread in threads: thread.start()
            for thread in threads: thread.join(10); self.assertFalse(thread.is_alive())
        self.assertEqual(sorted(results), [False, True])
        self.assertEqual(len(c.snapshot()['recoveries']), 1)

    def test_reboot_cli_uses_injected_identity_and_actual_herdr_probe(self):
        c, env, agent = self.recovery_fixture()
        executable = c.root/'bin/herdr'
        executable.parent.mkdir()
        executable.write_text('#!'+sys.executable+'\nimport json\nprint('+repr(json.dumps({'result':{'agents':[agent]}}))+')\n')
        executable.chmod(0o700)
        env = dict(os.environ, **env, PATH=str(executable.parent)+os.pathsep+os.environ['PATH'])
        result = subprocess.run([sys.executable, '-B', str(SCRIPT), '--root', str(c.root),
            'reboot-recover', '--task', 'recover', '--generation', '1', '--old-owner', 'old',
            '--owner', 'new', '--reason', 'explicit CLI fixture'], env=env, capture_output=True, text=True, timeout=10)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertTrue(json.loads(result.stdout)['lead_transfer'])

    def test_cli_authority_fail_closed_and_corrupt_state(self):
        def cli(*args):
            return subprocess.run([sys.executable,'-B',str(SCRIPT),'--root',str(self.root),*args],capture_output=True,text=True)
        r=cli('authority','--task','missing','--generation','1')
        self.assertEqual(r.returncode,3)
        self.assertEqual(json.loads(r.stdout),dict(valid=False,task='missing',state='missing',generation=None,owner=None,operation=None))
        self.c.register('t','lead')
        r=cli('authority','--task','t','--generation','1','--owner','lead')
        self.assertEqual(r.returncode,0)
        (self.root/'state.json').write_text('{corrupt')
        r=cli('authority','--task','t','--generation','1')
        self.assertEqual(r.returncode,3)
        self.assertFalse(json.loads(r.stderr)['valid'])

if __name__=='__main__':
    unittest.main(verbosity=2)

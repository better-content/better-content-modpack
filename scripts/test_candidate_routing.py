#!/usr/bin/env python3
"""Inert candidate/routing/kernel-lock regression tests; never launches Minecraft."""
import fcntl
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

REPO = Path(__file__).resolve().parent.parent
spec = importlib.util.spec_from_file_location('candidate_paths', REPO / 'scripts/candidate_paths.py')
paths = importlib.util.module_from_spec(spec)
spec.loader.exec_module(paths)


class CandidateRoutingTests(unittest.TestCase):
    def setUp(self):
        self.root = Path(tempfile.mkdtemp(dir=Path.home() / '.tmp', prefix='candidate-routing-'))
        print('synthetic fixture:', self.root, flush=True)
        subprocess.run(['git', 'init', '-q', str(self.root)], check=True, capture_output=True)
        (self.root / 'scripts').mkdir()
        for name in ('BoundedFacade.main.kts', 'bounded_facade.py', 'timing_report.py', 'runner_lock.py', 'candidate_paths.py', 'workspace_coordination.py', 'debug_coverage.py', 'debug_source_registration.py', 'testing_session.py'):
            shutil.copy2(REPO / 'scripts' / name, self.root / 'scripts' / name)
            if name.endswith('.main.kts'):
                with (self.root / 'scripts' / name).open('a') as stream:
                    stream.write(f'\n// isolated fixture {self.root}\n')
        for name in ('test.main.kts', 'pack-test-lock.main.kts', 'pack-test-queue.main.kts'):
            shutil.copy2(REPO / name, self.root / name)
            with (self.root / name).open('a') as stream:
                stream.write(f'\n// isolated fixture {self.root}\n')
        (self.root / 'gradle').mkdir()
        for name in ('active-custom-mods.json', 'debug-interactions.json'):
            shutil.copy2(REPO / 'gradle' / name, self.root / 'gradle' / name)
        self.env = dict(os.environ, BC_PACK_TEST_STATE_ROOT=str(self.root / 'runner-state'), WORKSPACE_COORDINATION_ROOT=str(self.root),
                        BC_TIMING_FILE=str(self.root / 'generated/timing/fixture.jsonl'))
        for name in ('BC_PACK_TEST_LOCK_TOKEN', 'BC_PACK_TEST_LOCK_OWNER', 'BC_PACK_TEST_HANDOFF', 'BC_PRIOR_DIST_RUN_ID', 'BC_CANDIDATE_BINDING'):
            self.env.pop(name, None)
        (self.root / 'pack.toml').write_text('version = "Build 10"\n')
        self.pair(9)
        self.pair(10)
        self.binding = self.root / 'binding.json'
        self.binding.write_text(json.dumps({'candidates': self.selected(9)}))
        self.handoff = self.root / 'handoff.json'
        self.handoff.write_text(json.dumps(self.manifest()))

    def pair(self, build):
        for side in ('client', 'server'):
            path = self.root / f'dist/build-{build}/{side}/better-content.zip'
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(f'{side}-{build}')

    def selected(self, build):
        import hashlib
        result = {}
        for side in ('client', 'server'):
            path = self.root / f'dist/build-{build}/{side}/better-content.zip'
            result[side] = str(path)
            result[side + '_sha256'] = hashlib.sha256(path.read_bytes()).hexdigest()
        return result

    def manifest(self):
        selected = self.selected(9)
        return dict(schema='bc.pack_test_handoff.v1', task_id='fixture', task_generation=1, task_owner='owner',
                    request_id='request-1', producer={'agent': 'nonliteral-owner'}, authorization={'explicit': True},
                    callback={'agent': 'fixture', 'pane_id': 'w1:p1'}, modpack={'head': 'stub', 'status': []},
                    repositories=[], validations=[], artifacts=[{'path': '/explicit-support', 'sha256': 'a' * 64}],
                    dependencies=[], scenarios=[], prior_evidence=[], selector='dist',
                    candidate={side: {'path': selected[side], 'sha256': selected[side + '_sha256']} for side in ('client', 'server')})

    def run_command(self, command, env=None):
        return subprocess.run(command, cwd=self.root, env=env or self.env, text=True, capture_output=True, timeout=90)

    def test_pins_precede_current_version_and_reject_hash_drift(self):
        self.assertEqual(self.selected(10), paths.select(self.root))
        (self.root / 'pack.toml').write_text('invalid current version')
        self.assertEqual(self.selected(9), paths.select(self.root, self.binding))
        self.assertEqual(self.selected(9), paths.select(self.root, handoff=self.handoff))
        self.assertEqual(self.selected(9), paths.select(self.root, self.binding, self.handoff))
        (self.root / 'dist/build-9/client/better-content.zip').write_text('drift')
        with self.assertRaises(ValueError):
            paths.select(self.root, self.binding)

    def test_ancestor_symlinks_and_cross_release_sides_fail(self):
        actual = self.root / 'dist/build-9/client'
        moved = self.root / 'client-real'
        actual.rename(moved)
        actual.symlink_to(moved, target_is_directory=True)
        with self.assertRaises(ValueError):
            paths.select(self.root, self.binding)
        with self.assertRaises(ValueError):
            paths.select(self.root, handoff=self.handoff)

    def test_pinned_dist_debug_and_retry_never_prepare_release(self):
        log = self.root / 'dispatch.log'
        for script, text in [('release.main.kts', '#!/bin/sh\necho RELEASE >> dispatch.log\nexit 90\n'),
                             ('gradlew', '#!/bin/sh\necho "GRADLE $BC_TEST_RETRY_OF $BC_PACK_TEST_HANDOFF" >> dispatch.log\nexit 17\n')]:
            path = self.root / script
            path.write_text(text)
            path.chmod(0o755)
        retry = '20261017T010101Z-123'
        (self.root / 'generated/test-evidence' / retry).mkdir(parents=True)
        for tier in ('dist', 'debug'):
            env = dict(self.env, BC_PACK_TEST_HANDOFF=str(self.handoff))
            result = self.run_command([str(self.root / 'test.main.kts'), tier, '--retry-of', retry], env)
            self.assertNotEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertTrue(log.exists())
        text = log.read_text()
        self.assertNotIn('RELEASE', text)
        self.assertIn(retry, text)
        self.assertIn(str(self.handoff), text)

    def test_full_debug_selection_is_exact_but_inert_gradle_cannot_fabricate_acceptance(self):
        release = self.root / 'release.main.kts'
        release.write_text('#!/bin/sh\necho RELEASE >> dispatch.log\nexit 90\n')
        release.chmod(0o755)
        gradle = self.root / 'gradlew'
        gradle.write_text('''#!/usr/bin/env python3
import json,os,sys
from pathlib import Path
sys.path.insert(0, str(Path.cwd() / 'scripts'))
from candidate_paths import select
suite = os.environ.get('BC_TEST_SELECTOR')
if suite:
    run = os.environ['BC_TEST_RUN_ID']
    directory = Path('generated/test-evidence') / run / suite
    directory.mkdir(parents=True, exist_ok=True)
    (directory / 'run.json').write_text(json.dumps(dict(run_id=run, suite=suite, status='passed', tier=os.environ['BC_TEST_TIER'])))
    if suite == 'candidate':
        selected = select(Path.cwd(), os.environ.get('BC_CANDIDATE_BINDING'), os.environ.get('BC_PACK_TEST_HANDOFF'))
        (directory / 'events.jsonl').write_text(json.dumps(dict(type='candidate_selected', **selected), separators=(',', ':')) + '\\n')
''')
        gradle.chmod(0o755)
        # Unbound current Build 10 coexists with older Build 9: no global single() scan.
        result = self.run_command([str(self.root / 'test.main.kts'), 'debug', '--existing-candidate'])
        self.assertEqual(1, result.returncode, result.stdout + result.stderr)
        self.assertIn('prior-dist: passed', result.stdout)
        self.assertIn('debug-coverage: failed', result.stdout)
        self.assertIn('missing required native scenario', result.stdout)
        # Explicit Build 9 remains authoritative even with broken current version metadata.
        (self.root / 'pack.toml').write_text('invalid current version')
        result = self.run_command([str(self.root / 'test.main.kts'), 'debug', '--candidate-binding', str(self.binding)])
        self.assertEqual(1, result.returncode, result.stdout + result.stderr)
        self.assertIn('prior-dist: passed', result.stdout)
        self.assertIn('missing required native scenario', result.stdout)
        env = dict(self.env, BC_PACK_TEST_HANDOFF=str(self.handoff))
        result = self.run_command([str(self.root / 'test.main.kts'), 'debug'], env)
        self.assertEqual(1, result.returncode, result.stdout + result.stderr)
        self.assertIn('prior-dist: passed', result.stdout)
        self.assertIn('missing required native scenario', result.stdout)
        self.assertFalse((self.root / 'dispatch.log').exists())

    def test_queue_authority_is_registered_not_literal_and_rechecked_at_dispatch(self):
        register = self.run_command([sys.executable, '-B', str(self.root / 'scripts/workspace_coordination.py'), '--root', str(self.root), 'task-register', '--task', 'fixture', '--owner', 'owner', '--operation', 'pack-test:dist'])
        self.assertEqual(0, register.returncode, register.stderr)
        original = json.loads(self.handoff.read_text())
        for changes in ({'task_owner': 'impostor'}, {'selector': 'debug'}, {'authorization': {'explicit': False}}):
            self.handoff.write_text(json.dumps(dict(original, **changes)))
            rejected = self.run_command([str(self.root / 'pack-test-queue.main.kts'), 'request', str(self.handoff)])
            self.assertNotEqual(0, rejected.returncode, rejected.stdout + rejected.stderr)
        self.handoff.write_text(json.dumps(original))
        request = self.run_command([str(self.root / 'pack-test-queue.main.kts'), 'request', str(self.handoff)])
        self.assertEqual(0, request.returncode, request.stdout + request.stderr)
        duplicate = self.run_command([str(self.root / 'pack-test-queue.main.kts'), 'request', str(self.handoff)])
        self.assertNotEqual(0, duplicate.returncode)
        queued = next((self.root / 'runner-state/queued').glob('*.json'))
        data = json.loads(queued.read_text())
        data['task_generation'] = 999
        queued.write_text(json.dumps(data))
        result = self.run_command([str(self.root / 'pack-test-queue.main.kts'), 'run-next'])
        self.assertNotEqual(0, result.returncode)
        self.assertEqual(1, len(list((self.root / 'runner-state/failed').glob('*.json'))))

    def test_kernel_verification_rejects_forged_token_and_requires_live_lock(self):
        state = self.root / 'runner-state'
        state.mkdir()
        token = 'test-token'
        (state / 'owner.json').write_text(json.dumps({'pid': os.getpid(), 'token': token}))
        lock = state / 'runner.lock'
        with lock.open('w') as stream:
            env = dict(self.env, BC_PACK_TEST_LOCK_TOKEN=token)
            command = [sys.executable, '-B', str(self.root / 'scripts/runner_lock.py')]
            self.assertEqual(75, self.run_command(command, env).returncode)
            fcntl.lockf(stream, fcntl.LOCK_EX | fcntl.LOCK_NB)
            result = self.run_command(command, env)
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertEqual(75, self.run_command(command, dict(env, BC_PACK_TEST_LOCK_TOKEN='forged')).returncode)


if __name__ == '__main__':
    unittest.main()

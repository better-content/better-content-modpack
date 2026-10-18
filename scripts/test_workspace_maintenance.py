#!/usr/bin/env python3
"""Small synthetic disposal fixtures only; never touch real workspace outputs."""
import contextlib
import importlib.util
import io
import json
import os
from pathlib import Path
import subprocess
import tempfile
import time
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('maintenance', Path(__file__).with_name('workspace-maintenance.py'))
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)


class MaintenanceFixture(unittest.TestCase):
    def setUp(self):
        base = Path.home() / '.tmp'
        base.mkdir(exist_ok=True)
        # Retain synthetic evidence as well; no handoff-driven deletion.
        self.root = Path(tempfile.mkdtemp(prefix='disposal-test-', dir=base))
        self.pack = self.root / 'better-content-modpack'
        self.pack.mkdir()
        self.git('init', '-q')
        self.file('better-content-modpack/.gitignore', 'build/\ngenerated/\ndist/\nrun*/\n.gradle/\nsaves/\n')
        self.file('better-content-modpack/source.txt', 'authored')
        self.git('add', '.')
        self.file('.codex/auth.json', 'secret')
        self.file('.codex/config.toml', 'configuration')
        self.file('.codex/packages/tool.js', 'installed')

    def git(self, *args):
        subprocess.run(['git', '-C', str(self.pack), *args], check=True, capture_output=True)

    def file(self, name, value='disposable'):
        p = self.root / name
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(value)
        return p

    def plan(self, refs=None, tasks=None):
        # Legacy deletion-safety scenarios explicitly mark their synthetic outputs obsolete.
        # New default-retention tests call m.plan directly without this opt-in fixture helper.
        data = m.load_retention(self.root)
        for path in m.discover(self.root)[0]:
            data['records'].setdefault(str(path), {'kind': 'reclaimable', 'reason': 'synthetic obsolete fixture'})
        m.save_retention(self.root, data)
        return m.plan(self.root, refs=[] if refs is None else refs, tasks={} if tasks is None else tasks)

    def action(self, path, **kwargs):
        return next(d['action'] for d in self.plan(**kwargs) if d['path'] == str(path))

    def apply(self, decisions=None, transaction=None):
        with patch.object(m, 'process_references', return_value=[]):
            return m.apply(self.root, self.plan() if decisions is None else decisions, transaction,
                           admission=lambda: None)


class DisposalTests(MaintenanceFixture):
    def test_credential_backups_are_inputs_not_generated_cleanup_targets(self):
        backup = self.file('.codex/auth.json.worklane-backup-synthetic', 'inert fixture')
        database = self.file('.codex/state.sqlite', 'generated database fixture')
        targets, _ = m.discover(self.root)
        self.assertNotIn(backup, targets)
        self.assertIn(database, targets)
        self.apply()
        self.assertEqual('inert fixture', backup.read_text())
        self.assertFalse(database.exists())

    def test_nested_authored_module_outputs_are_discovered_and_inputs_preserved(self):
        source = self.file('better-content-modpack/planner/build.gradle.kts', '// authored module')
        output = self.file('better-content-modpack/planner/build/result.bin', 'generated')
        self.git('add', 'planner/build.gradle.kts')
        targets, _ = m.discover(self.root)
        self.assertIn(output.parent, targets)
        self.assertEqual('delete', self.action(output.parent))
        self.apply()
        self.assertFalse(output.exists())
        self.assertEqual('// authored module', source.read_text())

    def test_latest_complete_candidate_and_support_survive_explicit_pruning(self):
        for number in (8, 9):
            for side in ('client', 'server'):
                self.file(f'better-content-modpack/dist/build-{number}/{side}/better-content.zip')
        staging = self.file('better-content-modpack/dist/build-9/client/staging/world/data')
        self.file('better-content-modpack/dist/build-10/client/better-content.zip')
        pair = m.latest_build_outputs(self.root)
        self.assertEqual(2, len(pair))
        self.assertEqual('protect_input', self.action(pair[0].parents[1]))
        self.apply()
        self.assertTrue(all(p.exists() for p in pair))
        self.assertFalse((self.pack / 'dist/build-8').exists())
        self.assertFalse((self.pack / 'dist/build-10').exists())
        self.assertTrue(staging.exists())

    def test_new_complete_pair_supersedes_prior_pair_without_reading_verdicts(self):
        for side in ('client', 'server'):
            self.file(f'better-content-modpack/dist/build-9/{side}/better-content.zip')
        self.file('better-content-modpack/generated/run.json', 'failed or malformed')
        for side in ('client', 'server'):
            self.file(f'better-content-modpack/dist/build-10/{side}/better-content.zip')
        self.apply()
        self.assertFalse((self.pack / 'dist/build-9').exists())
        self.assertTrue(all(p.exists() for p in m.latest_build_outputs(self.root)))

    def test_partial_build_completed_after_plan_is_not_deleted(self):
        for side in ('client', 'server'):
            self.file(f'better-content-modpack/dist/build-9/{side}/better-content.zip')
        self.file('better-content-modpack/dist/build-10/client/better-content.zip')
        decisions = self.plan()
        self.file('better-content-modpack/dist/build-10/server/better-content.zip')
        with self.assertRaisesRegex(RuntimeError, 'latest retained build'):
            self.apply(decisions)
        self.assertTrue(all(p.exists() for p in m.latest_build_outputs(self.root)))

    def test_explicitly_obsolete_fixture_outputs_remain_safely_reclaimable(self):
        for name in ('build', 'generated', 'dist', 'run-gametest', 'saves'):
            self.file('better-content-modpack/' + name + '/world/data', 'failed/current/sealed')
            self.assertEqual('delete', self.action(self.pack / name))
        self.file('better-content-modpack/generated/run.json', 'not-json')
        self.apply()
        self.assertFalse((self.pack / 'generated').exists())
        self.assertEqual('authored', (self.pack / 'source.txt').read_text())

    def test_absent_candidate_repeat_and_no_historical_transaction(self):
        self.file('workspace_artifacts/reviews/old/world/data')
        self.apply()
        self.apply()
        self.assertEqual([], list((self.root / '.worklane/disposal').glob('*.json')))

    def test_dry_run_does_not_create_state_or_change_files(self):
        p = self.file('better-content-modpack/build/file')
        with patch.object(m, 'process_references', return_value=[]), contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(0, m.main(['--workspace', str(self.root), 'audit']))
        self.assertTrue(p.exists())
        self.assertFalse((self.root / '.worklane').exists())

    def test_operating_inputs_and_nonignored_source_are_protected(self):
        self.file('better-content-modpack/generated/authored.txt')
        self.git('add', '-f', 'generated/authored.txt')
        self.assertEqual('protect_input', self.action(self.pack / 'generated'))
        self.apply()
        for path in ('.codex/auth.json', '.codex/config.toml', '.codex/packages/tool.js',
                     'better-content-modpack/generated/authored.txt'):
            self.assertTrue((self.root / path).exists())

    def test_inactive_pi_history_is_protected_while_pi_caches_remain_disposable(self):
        history = self.file('.pi/agent/sessions/--home-dev--/old.jsonl', 'user conversation')
        attachment = self.file('.pi/agent/sessions/--home-dev--/images/capture.png', 'chat attachment')
        cache = self.file('.pi/agent/web-search-cache/result.json')
        models = self.file('.pi/agent/models-store.json')
        root = self.root / '.pi/agent/sessions'
        targets, _ = m.discover(self.root)
        self.assertFalse(any(m.beneath(p, root) or m.beneath(root, p) for p in targets))
        self.assertEqual('protect_input', self.action(root))
        self.apply()
        self.assertEqual('user conversation', history.read_text())
        self.assertEqual('chat attachment', attachment.read_text())
        self.assertFalse(cache.exists())
        self.assertFalse(models.exists())

    def test_old_pi_deletion_plan_and_resume_cannot_delete_history(self):
        history = self.file('.pi/agent/sessions/--home-dev--/old.jsonl', 'keep')
        old = {'path': str(history), 'category': 'agent session', 'action': 'delete',
               'identity': m.signature(history)}
        with self.assertRaisesRegex(ValueError, 'protected Pi conversation history'):
            self.apply([old])
        self.assertEqual('keep', history.read_text())
        record = next((self.root / '.worklane/disposal').glob('*.json'))
        with self.assertRaisesRegex(ValueError, 'now active or unclassified'):
            self.apply(transaction=record.stem)
        self.assertEqual('keep', history.read_text())
        for path in (history, history.parent, self.root / '.pi/agent'):
            with self.assertRaisesRegex(ValueError, 'protected Pi conversation history'):
                m.delete_checked(path, self.root, m.signature(path))
        self.assertEqual('keep', history.read_text())

    def test_active_fd_cwd_and_sqlite_family_defer(self):
        p = self.file('workspace_artifacts/reviews/scoped/world/data')
        self.assertEqual('defer_active', self.action(p, refs=[(42, p)]))
        db = self.file('.codex/state.sqlite')
        wal = self.file('.codex/state.sqlite-wal')
        self.assertEqual('defer_active', self.action(wal, refs=[(42, db)]))

    def test_all_task_outcomes_retain_at_handoff_and_finish_never_plans_deletion(self):
        for outcome in ('success', 'failure', 'cancellation', 'blocked'):
            self.file('workspace_artifacts/reviews/' + outcome + '/world')
            tasks = {outcome: {'state': 'active', 'owner': 'test-owner',
                               'paths': [str(self.root / 'workspace_artifacts')]}}
            m.save_tasks(self.root, tasks)
            self.assertEqual('defer_active', self.action(self.root / 'workspace_artifacts/reviews' / outcome, tasks=tasks))
            with patch.object(m, 'plan', side_effect=AssertionError('finish must not plan deletion')), \
                    contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(0, m.main(['--workspace', str(self.root), 'finish', '--task', outcome,
                                           '--owner', 'test-owner', '--apply']))
            self.assertTrue((self.root / 'workspace_artifacts/reviews' / outcome / 'world').exists())
            self.assertEqual('finished', m.load_tasks(self.root)[outcome]['state'])

    def test_shared_cache_deferred_until_last_task_finishes(self):
        p = self.file('.gradle/caches/provider.jar')
        tasks = {'other': {'state': 'active', 'paths': []}}
        self.assertEqual('defer_active', self.action(p.parent, tasks=tasks))
        self.assertEqual('defer_active', self.action(p.parent, refs=[(42, self.root / '.gradle/wrapper/java.jar')]))
        self.assertEqual('delete', self.action(p.parent, tasks={}))

    def test_explicit_pin_expires_without_permanent_retention(self):
        p = self.file('workspace_artifacts/deliverables/scoped/pair.zip')
        task = {'state': 'finished', 'retention': 'released', 'paths': [str(p)], 'pin_until': time.time() + 100}
        self.assertEqual('defer_active', self.action(p, tasks={'delivery': task}))
        task['pin_until'] = time.time() - 1
        self.assertEqual('delete', self.action(p.parent, tasks={'delivery': task}))

    def test_symlinks_unlinked_not_followed_and_path_escape_rejected(self):
        input = self.file('authored/keep')
        link = self.root / 'workspace_artifacts/reviews'
        link.parent.mkdir()
        link.symlink_to(input.parent, target_is_directory=True)
        self.apply()
        self.assertFalse(link.is_symlink())
        self.assertTrue(input.exists())
        for value in ('../escape', str(self.root.parent / 'escape'), str(self.root)):
            with self.assertRaises(ValueError):
                m.safe_path(self.root, value)
        (self.root / 'alias').symlink_to(input.parent, target_is_directory=True)
        with self.assertRaises(ValueError):
            m.safe_path(self.root, self.root / 'alias/keep')

    def test_substitution_and_mount_preflight_rejected(self):
        p = self.file('workspace_artifacts/reviews/scoped/old')
        identity = m.signature(p.parent)
        p.parent.rename(p.parent.with_name('moved'))
        p.parent.mkdir()
        with self.assertRaises(ValueError):
            m.delete_checked(p.parent, self.root, identity)
        p = self.file('workspace_artifacts/reviews/scoped/new')
        original = os.path.ismount
        with patch.object(os.path, 'ismount', side_effect=lambda x: Path(x) == p or original(x)):
            with self.assertRaises(ValueError):
                m.delete_checked(p.parent, self.root, m.signature(p.parent))
        self.assertTrue(p.exists())

    def test_interruption_resume_and_unsafe_transaction(self):
        self.file('workspace_artifacts/reviews/world')
        decisions = self.plan()
        with patch.object(m, 'delete_checked', side_effect=OSError('interrupted')):
            with self.assertRaises(OSError):
                self.apply(decisions)
        record = next((self.root / '.worklane/disposal').glob('*.json'))
        self.apply(self.plan(), record.stem)
        self.assertFalse(record.exists())
        with self.assertRaises(ValueError):
            self.apply(transaction='../../escape')

    def test_consumer_starting_after_audit_blocks_apply(self):
        p = self.file('workspace_artifacts/reviews/world')
        decisions = self.plan()
        with patch.object(m, 'process_references', return_value=[(42, p)]):
            with self.assertRaises(RuntimeError):
                m.apply(self.root, decisions, admission=lambda: None)
        self.assertTrue(p.exists())

    def test_lease_starting_after_audit_blocks_apply(self):
        p = self.file('.gradle/caches/provider.jar')
        decisions = self.plan()
        m.save_tasks(self.root, {'active': {'state': 'active', 'paths': [], 'uses_build_cache': True}})
        with self.assertRaises(RuntimeError):
            self.apply(decisions)
        self.assertTrue(p.exists())

    def test_exact_active_input_does_not_preserve_sibling_worlds(self):
        active = self.file('workspace_artifacts/reviews/scoped/input/recipes.json')
        old = self.file('workspace_artifacts/reviews/scoped/old/world')
        tasks = {'recipe': {'state': 'active', 'paths': [str(active.parent)], 'uses_build_cache': False}}
        m.save_tasks(self.root, tasks)
        decisions = self.plan(tasks=tasks)
        self.assertEqual('defer_active', next(d['action'] for d in decisions if d['path'] == str(active.parent)))
        self.assertEqual('delete', next(d['action'] for d in decisions if d['path'] == str(old.parent)))
        self.apply(decisions)
        self.assertTrue(active.exists())
        self.assertFalse(old.exists())

    def test_live_cwd_from_a_real_process_is_deferred(self):
        import sys
        directory = self.root / 'workspace_artifacts/reviews/live'
        directory.mkdir(parents=True)
        process = subprocess.Popen([sys.executable, '-c', 'import time; time.sleep(30)'], cwd=directory)
        try:
            refs = m.process_references(self.root)
            self.assertIn((process.pid, directory), refs)
            self.assertEqual('defer_active', self.action(directory, refs=refs))
        finally:
            process.terminate()
            process.wait(timeout=5)

    def test_same_device_bind_mount_in_mountinfo_is_rejected(self):
        p = self.file('workspace_artifacts/reviews/scoped/world')
        original = Path.read_text
        def read(path, *args, **kwargs):
            if path == Path('/proc/self/mountinfo'):
                return f'1 0 1:1 / {p.parent} rw - ext4 none rw\n'
            return original(path, *args, **kwargs)
        with patch.object(Path, 'read_text', read):
            with self.assertRaises(ValueError):
                m.delete_checked(p.parent, self.root, m.signature(p.parent))
        self.assertTrue(p.exists())

    def test_registry_and_predictable_temporary_symlinks_do_not_redirect_state(self):
        source = self.file('authored-input.txt', 'keep')
        state = self.root / '.worklane'
        state.mkdir()
        registry = state / 'disposable-tasks.json'
        registry.symlink_to(source)
        with self.assertRaises(ValueError):
            m.load_tasks(self.root)
        with self.assertRaises(ValueError):
            m.save_tasks(self.root, {'task': {'state': 'active'}})
        registry.unlink()
        (state / 'disposable-tasks.new').symlink_to(source)
        m.save_tasks(self.root, {'task': {'state': 'active'}})
        self.assertEqual('keep', source.read_text())
        self.assertEqual('active', m.load_tasks(self.root)['task']['state'])

    def test_readonly_dependency_cache_is_disposed_without_touching_installed_tools(self):
        p = self.file('go/pkg/mod/vendor/package/source.go')
        p.parent.chmod(0o555)
        browser = self.file('.cache/ms-playwright/chromium/bin/browser')
        self.assertEqual('protect_input', self.action(browser.parents[2]))
        self.apply(self.plan())
        self.assertFalse(p.exists())
        self.assertTrue(browser.exists())

    def test_live_unix_socket_is_deferred_and_namespace_is_operating_input(self):
        import socket
        path = self.root / '.tmp/service.sock'
        path.parent.mkdir()
        endpoint = socket.socket(socket.AF_UNIX)
        endpoint.bind(str(path))
        try:
            self.assertEqual('defer_active', self.action(path, refs=m.process_references(self.root)))
        finally:
            endpoint.close()
        self.assertEqual('delete', self.action(path))
        namespace = self.file('.tmp/.X11-unix/operating-socket')
        self.assertEqual('protect_input', self.action(namespace.parent))
        lock = self.file('.tmp/.X42-lock', str(os.getpid()))
        original = Path.read_text
        def read(path, *args, **kwargs):
            return 'Xvfb' if path == Path(f'/proc/{os.getpid()}/comm') else original(path, *args, **kwargs)
        with patch.object(Path, 'read_text', read):
            self.assertEqual('defer_active', self.action(lock, refs=m.process_references(self.root)))

    def test_parent_consumer_is_not_ignored(self):
        import sys
        p = self.file('.cache/parent/output')
        code = ('import importlib.util,json,sys; from pathlib import Path; '
                's=importlib.util.spec_from_file_location("m",sys.argv[1]); '
                'm=importlib.util.module_from_spec(s); s.loader.exec_module(m); '
                'print(json.dumps([(pid,str(p)) for pid,p in m.process_references(Path(sys.argv[2]))]))')
        with p.open() as handle:
            result = subprocess.check_output([sys.executable, '-B', '-c', code, m.__file__, str(self.root)], text=True)
        self.assertIn([os.getpid(), str(p)], json.loads(result))

    def test_permission_blocker_does_not_prevent_other_idle_disposal(self):
        blocked = self.file('.tmp/blocked/output')
        idle = self.file('.tmp/idle/output')
        original = m.delete_checked
        def delete(path, *args, **kwargs):
            if path == blocked.parent:
                raise PermissionError('foreign owner')
            return original(path, *args, **kwargs)
        with patch.object(m, 'delete_checked', delete), patch.object(m, 'process_references', return_value=[]):
            with self.assertRaises(m.DisposalBlocked) as result:
                m.apply(self.root, self.plan(), admission=lambda: None)
        self.assertTrue(blocked.exists())
        self.assertFalse(idle.exists())
        self.assertEqual([str(idle.parent)], result.exception.removed)
        self.assertEqual(str(blocked.parent), result.exception.blocked[0]['path'])
        self.apply(self.plan(), transaction=result.exception.transaction)
        self.assertFalse(blocked.exists())
        self.assertEqual([], list((self.root / '.worklane/disposal').glob('*.json')))

    def test_audit_reports_permission_blocker_without_calling_it_an_input(self):
        import contextlib
        import io
        p = self.file('.tmp/foreign/output')
        out = io.StringIO()
        self.plan()  # Explicitly reclaimable synthetic fixture, not unknown legacy data.
        with patch.object(m, 'permission_blocker', return_value='foreign owner'), \
                patch.object(m, 'process_references', return_value=[]), contextlib.redirect_stdout(out):
            m.main(['--workspace', str(self.root), 'audit'])
        report = json.loads(out.getvalue())
        decision = next(d for d in report['decisions'] if d['path'] == str(p.parent))
        self.assertEqual('delete', decision['action'])
        self.assertEqual('foreign owner', decision['blocked_permission'])
        self.assertEqual(0, report['reclaimable_bytes'])
        self.assertTrue(p.exists())

    def test_provider_build_outputs_deleted_source_graph_untouched(self):
        p = self.file('better-content-modpack/build/providers/api.jar')
        graph = self.file('better-content-modpack/gradle/active-custom-mods.json', '{"dependsOn":["provider"]}')
        self.git('add', 'gradle')
        self.apply()
        self.assertFalse(p.exists())
        self.assertEqual('{"dependsOn":["provider"]}', graph.read_text())


class RetentionTests(MaintenanceFixture):
    # Exercise the public lifecycle independently of explicit disposal-safety fixtures.
    def run_command(self, *args):
        with contextlib.redirect_stdout(io.StringIO()) as out:
            result = m.main(['--workspace', str(self.root), *args])
        return result, json.loads(out.getvalue())

    def test_default_unknown_and_caches_are_retained_without_classification(self):
        world = self.file('better-content-modpack/run/world/data')
        cache = self.file('.gradle/caches/api.jar')
        decisions = m.plan(self.root, refs=[], tasks={})
        self.assertEqual({'retain'}, {d['action'] for d in decisions
                                     if d['path'] in {str(world.parents[1]), str(cache.parent)}})
        self.assertFalse((self.root / '.worklane').exists())
        with self.assertRaisesRegex(RuntimeError, 'admission'):
            m.apply(self.root, decisions)
        self.assertTrue(world.exists())
        self.assertTrue(cache.exists())

    def test_expired_pin_does_not_release_retention(self):
        p = self.file('workspace_artifacts/reviews/retry/world')
        decisions = self.plan(tasks={'retry': {'state': 'finished', 'pin_until': time.time() - 1,
                                              'paths': [str(p.parent)]}})
        self.assertEqual('retain', next(d['action'] for d in decisions if d['path'] == str(p.parent)))

    def test_finish_preserves_other_tasks_and_requires_owner(self):
        a = self.file('workspace_artifacts/reviews/a/evidence')
        b = self.file('workspace_artifacts/reviews/b/evidence')
        self.run_command('begin', '--task', 'a', '--owner', 'alice', '--path', str(a.parent))
        self.run_command('begin', '--task', 'b', '--owner', 'bob', '--path', str(b.parent))
        with self.assertRaisesRegex(ValueError, 'owner mismatch'):
            self.run_command('finish', '--task', 'a', '--owner', 'bob')
        self.run_command('finish', '--task', 'a', '--owner', 'alice')
        tasks = m.load_tasks(self.root)
        self.assertEqual('active', tasks['b']['state'])
        self.assertEqual('finished', tasks['a']['state'])
        self.assertTrue(a.exists() and b.exists())
        self.assertNotEqual('released', tasks['a']['retention'])

    def test_legacy_registrations_migrate_only_by_explicit_reconciliation(self):
        path = self.file('workspace_artifacts/reviews/legacy/world')
        state = self.file('.worklane/disposable-tasks.json', json.dumps({
            'schema': m.LEGACY_SCHEMA, 'tasks': {'legacy': {'state': 'active', 'paths': [str(path.parent)]}}}))
        original = state.read_bytes()
        with self.assertRaisesRegex(ValueError, 'legacy owner unknown'):
            self.run_command('finish', '--task', 'legacy', '--owner', 'lead')
        self.assertEqual(original, state.read_bytes())
        self.run_command('finish', '--task', 'legacy', '--owner', 'lead', '--reconcile-legacy',
                         '--reason', 'synthetic owner stopped; preserve unfinished diagnosis')
        self.assertTrue(path.exists())
        self.assertEqual(m.SCHEMA, json.loads(state.read_text())['schema'])

    def test_cached_status_does_not_scan_or_create_state(self):
        with patch.object(m, 'plan', side_effect=AssertionError('no scan')):
            code, status = self.run_command('status', '--cached')
        self.assertEqual(0, code)
        self.assertFalse(status['stability_revalidated'])
        self.assertIn('filesystem', status['capacity_scope'])
        self.assertFalse((self.root / '.worklane').exists())

    def make_good(self):
        self.file('better-content-modpack/pack.toml', 'name="synthetic"\n')
        artifact = self.file('better-content-modpack/dist/build-1/client/better-content.zip')
        evidence = self.file('workspace_artifacts/reviews/good/native.json', '{"synthetic": true}')
        self.run_command('stability', '--state', 'good', '--basis', 'synthetic fixture, not actual acceptance',
                         '--artifact', str(artifact), '--evidence', str(evidence))
        return artifact, evidence

    def test_current_identity_and_evidence_are_required_for_stability(self):
        artifact, evidence = self.make_good()
        data = m.load_retention(self.root)
        self.assertTrue(m.stable_now(self.root, data))
        self.file('better-content-modpack/config/change.toml', 'changed=true')
        self.assertFalse(m.stable_now(self.root, data))
        artifact.write_text('changed artifact')
        self.assertFalse(m.stable_now(self.root, data))
        self.assertTrue(evidence.exists())

    def test_failed_new_candidate_does_not_displace_last_known_good_or_evidence(self):
        artifact, evidence = self.make_good()
        for side in ('client', 'server'):
            self.file(f'better-content-modpack/dist/build-2/{side}/better-content.zip', 'untested')
        self.run_command('stability', '--state', 'broken', '--basis', 'new candidate failed')
        self.apply()
        self.assertTrue(artifact.exists())
        self.assertTrue(evidence.exists())
        self.assertTrue(all(p.exists() for p in m.latest_build_outputs(self.root)))
        self.assertFalse(m.stable_now(self.root, m.load_retention(self.root)))

    def test_old_transaction_cannot_resume_under_new_policy(self):
        p = self.file('workspace_artifacts/reviews/old/world')
        decisions = self.plan()
        record = self.file('.worklane/disposal/' + 'a' * 32 + '.json', json.dumps({
            'schema': m.LEGACY_SCHEMA, 'workspace': str(self.root), 'pending': decisions}))
        with self.assertRaisesRegex(ValueError, 'invalid disposal transaction'):
            self.apply(decisions, record.stem)
        self.assertTrue(p.exists() and record.exists())

    def test_new_work_interrupts_large_directory_between_bounded_batches(self):
        directory = self.root / 'workspace_artifacts/reviews/large'
        for number in range(150):
            self.file(str(directory.relative_to(self.root) / str(number)))
        calls = []
        def admission():
            calls.append(1)
            if len(calls) == 2:
                raise RuntimeError('new work requested')
        with self.assertRaisesRegex(RuntimeError, 'new work'):
            m.delete_checked(directory, self.root, m.signature(directory), checkpoint=admission)
        self.assertTrue(directory.exists())
        self.assertGreater(len(list(directory.iterdir())), 0)

    def test_new_unknown_descendant_overrides_reclaimable_parent_during_batch(self):
        p = self.file('workspace_artifacts/reviews/obsolete/child/world')
        self.plan()
        data = m.load_retention(self.root)
        data['records'][str(p.parent)] = {'kind': 'unknown', 'reason': 'newly unresolved material'}
        m.save_retention(self.root, data)
        with patch.object(m, 'process_references', return_value=[]):
            with self.assertRaisesRegex(RuntimeError, 'classification changed'):
                m.revalidate_batch(self.root, p.parents[1], 'disposable review/delivery/evidence')
        self.assertTrue(p.exists())

    def test_finished_released_task_cannot_admit_unleased_new_work(self):
        self.run_command('begin', '--task', 'old', '--owner', 'lead')
        self.run_command('finish', '--task', 'old', '--owner', 'lead')
        self.run_command('release-retention', '--task', 'old', '--owner', 'lead', '--reason', 'done')
        fresh = self.file('workspace_artifacts/reviews/new/world')
        with self.assertRaisesRegex(ValueError, 'cannot update'):
            self.run_command('update', '--task', 'old', '--owner', 'lead', '--path', str(fresh.parent))
        self.assertEqual([], m.load_tasks(self.root)['old']['paths'])

    def test_nonfinite_pins_are_rejected_and_updates_preserve_existing_pin(self):
        for value in ('nan', 'inf', '-inf'):
            with self.assertRaisesRegex(ValueError, 'finite'):
                self.run_command('begin', '--task', 'pin', '--owner', 'lead', '--pin-until=' + value)
        deadline = time.time() + 1000
        self.run_command('begin', '--task', 'pin', '--owner', 'lead', '--pin-until', str(deadline))
        self.run_command('update', '--task', 'pin', '--owner', 'lead')
        self.assertEqual(deadline, m.load_tasks(self.root)['pin']['pin_until'])

    def test_real_consumer_opening_inside_target_interrupts_next_batch(self):
        import sys
        p = self.file('workspace_artifacts/reviews/batch/world')
        decisions = self.plan()
        original = m.process_references
        processes = []
        def delete(path, workspace, identity, *, checkpoint):
            process = subprocess.Popen([sys.executable, '-c',
                'import sys,time; f=open(sys.argv[1]); print("ready",flush=True); time.sleep(30)', str(p)],
                stdout=subprocess.PIPE, text=True)
            processes.append(process)
            self.assertEqual('ready', process.stdout.readline().strip())
            checkpoint()
        try:
            with patch.object(m, 'delete_checked', delete):
                with self.assertRaisesRegex(RuntimeError, 'consumer appeared'):
                    m.apply(self.root, decisions, admission=lambda: None)
        finally:
            for process in processes:
                process.terminate()
                process.wait(timeout=5)
                process.stdout.close()
        self.assertTrue(p.exists())

    def test_prune_detects_stability_drift_inside_target(self):
        import workspace_coordination as coordination
        from types import SimpleNamespace
        self.make_good()
        p = self.file('workspace_artifacts/reviews/obsolete/world')
        self.run_command('classify', '--path', str(p.parent), '--kind', 'reclaimable', '--reason', 'obsolete')
        self.run_command('configure', '--messy-bytes', '1')
        fake = SimpleNamespace(reserve=lambda owner: None, unreserve=lambda owner: None,
                               maintenance=lambda owner: contextlib.nullcontext(),
                               maintenance_should_yield=lambda: False)
        def delete(path, workspace, identity, *, checkpoint):
            (self.pack / 'pack.toml').write_text('changed=true')
            checkpoint()
        with patch.object(m, 'working_agents', return_value=[]), \
                patch.object(m, 'process_references', return_value=[]), \
                patch.object(coordination, 'Coordinator', return_value=fake), \
                patch.object(m, 'delete_checked', delete):
            with self.assertRaisesRegex(RuntimeError, 'stability changed'):
                self.run_command('prune', '--apply', '--owner', 'maintenance')
        self.assertTrue(p.exists())

    def test_prune_refuses_working_agents_before_any_deletion(self):
        p = self.file('workspace_artifacts/reviews/old/world')
        with patch.object(m, 'working_agents', return_value=['other']), \
                patch.object(m, 'apply', side_effect=AssertionError('no deletion')):
            with self.assertRaisesRegex(RuntimeError, 'not quiet'):
                self.run_command('prune', '--apply', '--owner', 'maintenance')
        self.assertTrue(p.exists())

    def test_prune_refuses_unknown_stability_and_insufficient_clutter(self):
        import workspace_coordination as coordination
        from types import SimpleNamespace
        fake = SimpleNamespace(reserve=lambda owner: None, unreserve=lambda owner: None,
                               maintenance=lambda owner: contextlib.nullcontext(),
                               maintenance_should_yield=lambda: False)
        with patch.object(m, 'working_agents', return_value=[]), \
                patch.object(m, 'process_references', return_value=[]), \
                patch.object(coordination, 'Coordinator', return_value=fake):
            with self.assertRaisesRegex(RuntimeError, 'stability'):
                self.run_command('prune', '--apply', '--owner', 'maintenance')
            self.make_good()
            with self.assertRaisesRegex(RuntimeError, 'not messy'):
                self.run_command('prune', '--apply', '--owner', 'maintenance')

    def test_only_quiet_stable_messy_explicit_reclaimable_targets_are_pruned(self):
        import workspace_coordination as coordination
        from types import SimpleNamespace
        self.make_good()
        p = self.file('workspace_artifacts/reviews/obsolete/world')
        self.run_command('classify', '--path', str(p.parent), '--kind', 'reclaimable', '--reason', 'synthetic obsolete')
        self.run_command('configure', '--messy-bytes', '1')
        fake = SimpleNamespace(reserve=lambda owner: None, unreserve=lambda owner: None,
                               maintenance=lambda owner: contextlib.nullcontext(),
                               maintenance_should_yield=lambda: False)
        with patch.object(m, 'working_agents', return_value=[]), \
                patch.object(m, 'process_references', return_value=[]), \
                patch.object(coordination, 'Coordinator', return_value=fake):
            code, result = self.run_command('prune', '--apply', '--owner', 'maintenance')
        self.assertEqual(0, code)
        self.assertEqual(1, result['removed_count'])
        self.assertFalse(p.exists())


if __name__ == '__main__':
    unittest.main(verbosity=2)

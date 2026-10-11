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


class DisposalTests(unittest.TestCase):
    def setUp(self):
        base = Path.home() / '.tmp'
        base.mkdir(exist_ok=True)
        self.temp = tempfile.TemporaryDirectory(prefix='disposal-test-', dir=base)
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
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
        return m.plan(self.root, refs=[] if refs is None else refs, tasks={} if tasks is None else tasks)

    def action(self, path, **kwargs):
        return next(d['action'] for d in self.plan(**kwargs) if d['path'] == str(path))

    def apply(self, decisions=None, transaction=None):
        with patch.object(m, 'process_references', return_value=[]):
            return m.apply(self.root, self.plan() if decisions is None else decisions, transaction)

    def test_worlds_failures_current_candidates_and_malformed_reports_are_disposable(self):
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

    def test_active_fd_cwd_and_sqlite_family_defer(self):
        p = self.file('workspace_artifacts/reviews/scoped/world/data')
        self.assertEqual('defer_active', self.action(p, refs=[(42, p)]))
        db = self.file('.codex/state.sqlite')
        wal = self.file('.codex/state.sqlite-wal')
        self.assertEqual('defer_active', self.action(wal, refs=[(42, db)]))

    def test_all_task_outcomes_dispose_at_handoff(self):
        for outcome in ('success', 'failure', 'cancellation', 'blocked'):
            self.file('workspace_artifacts/reviews/' + outcome + '/world')
            tasks = {outcome: {'state': 'active', 'paths': [str(self.root / 'workspace_artifacts')]}}
            m.save_tasks(self.root, tasks)
            self.assertEqual('defer_active', self.action(self.root / 'workspace_artifacts/reviews' / outcome, tasks=tasks))
            with patch.object(m, 'process_references', return_value=[]), contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(0, m.main(['--workspace', str(self.root), 'finish', '--task', outcome, '--apply']))
            self.assertEqual([], list((self.root / 'workspace_artifacts/reviews').iterdir()))
            self.assertEqual({}, m.load_tasks(self.root))

    def test_shared_cache_deferred_until_last_task_finishes(self):
        p = self.file('.gradle/caches/provider.jar')
        tasks = {'other': {'state': 'active', 'paths': []}}
        self.assertEqual('defer_active', self.action(p.parent, tasks=tasks))
        self.assertEqual('defer_active', self.action(p.parent, refs=[(42, self.root / '.gradle/wrapper/java.jar')]))
        self.assertEqual('delete', self.action(p.parent, tasks={}))

    def test_explicit_pin_expires_without_permanent_retention(self):
        p = self.file('workspace_artifacts/deliverables/scoped/pair.zip')
        task = {'state': 'finished', 'paths': [str(p)], 'pin_until': time.time() + 100}
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
                m.apply(self.root, decisions)
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
                m.apply(self.root, self.plan())
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


if __name__ == '__main__':
    unittest.main(verbosity=2)

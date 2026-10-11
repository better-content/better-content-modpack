#!/usr/bin/env python3
"""Public control receipt tests, without source builds or pack preparation."""
import contextlib
import io
import json
import os
import subprocess
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import testing_session as sessions


class SessionTests(unittest.TestCase):
    def setUp(self):
        output = Path(__file__).resolve().parent.parent / 'build/tmp/session-control-tests'
        output.mkdir(parents=True, exist_ok=True)
        self.temp = tempfile.TemporaryDirectory(dir=output)
        self.root = Path(self.temp.name)

    def tearDown(self):
        self.temp.cleanup()

    def test_begin_is_pending_and_owner_cannot_be_adopted(self):
        receipt = sessions.begin(self.root, 'conversation')
        self.assertEqual('pending', receipt['baseline'])
        self.assertEqual({}, receipt['mods'])
        session = receipt['session_id']
        self.assertEqual(receipt, sessions.load(self.root, session, 'conversation'))
        with self.assertRaises(ValueError):
            sessions.load(self.root, session, 'new-conversation')
        self.assertFalse((self.root / 'mods').exists())

    def test_end_and_path_bounds(self):
        receipt = sessions.begin(self.root, 'conversation')
        path = sessions.session_path(self.root, receipt['session_id'])
        path.unlink()
        with self.assertRaises(OSError):
            sessions.load(self.root, receipt['session_id'], 'conversation')
        for invalid in ('../receipt', '', 'A' * 32):
            with self.assertRaises(ValueError):
                sessions.session_path(self.root, invalid)

    def test_atomic_receipt_has_no_historical_catalogue(self):
        receipt = sessions.begin(self.root, 'conversation')
        path = sessions.session_path(self.root, receipt['session_id'])
        receipt['generation'] = 2
        sessions.atomic_write(path, receipt)
        self.assertEqual(2, json.loads(path.read_text())['generation'])
        self.assertEqual({path.name, '.lock'}, {p.name for p in path.parent.iterdir()})

    def test_symlink_receipt_and_directory_are_rejected(self):
        receipt = sessions.begin(self.root, 'conversation')
        path = sessions.session_path(self.root, receipt['session_id'])
        target = self.root / 'authored.txt'
        target.write_text('preserve')
        path.unlink(); path.symlink_to(target)
        with self.assertRaises(ValueError):
            sessions.load(self.root, receipt['session_id'], 'conversation')
        self.assertEqual('preserve', target.read_text())

    def test_interrupted_receipt_requires_owner_confirmation_and_dead_coordinator(self):
        receipt = sessions.begin(self.root, 'conversation')
        run = '20261011T000000Z-' + str(os.getpid())
        receipt['active_run'] = run
        path = sessions.session_path(self.root, receipt['session_id'])
        sessions.atomic_write(path, receipt)
        base = ['testing_session.py', '--root', str(self.root), 'end', receipt['session_id']]
        with patch.dict(os.environ, {'PI_SESSION_ID': 'conversation'}):
            with patch('sys.argv', base):
                with self.assertRaises(ValueError):
                    sessions.main()
            with patch('sys.argv', base + ['--confirm-terminated-run', run]):
                with self.assertRaises(ValueError):
                    sessions.main()
            self.assertTrue(path.exists())
            with patch('sys.argv', base + ['--confirm-terminated-run', run]), patch.object(os, 'kill', side_effect=ProcessLookupError):
                with contextlib.redirect_stdout(io.StringIO()):
                    sessions.main()
            self.assertFalse(path.exists())

    def test_helpers_require_owner_admission_and_cannot_end_or_replace_owner(self):
        receipt = sessions.begin(self.root, 'conversation')
        sid = receipt['session_id']
        base = ['testing_session.py', '--root', str(self.root)]
        def invoke(owner, args):
            with patch.dict(os.environ, {'PI_SESSION_ID': owner}), patch('sys.argv', base + args), contextlib.redirect_stdout(io.StringIO()):
                sessions.main()
        with self.assertRaises(ValueError):
            sessions.load(self.root, sid, 'helper', allow_helpers=True)
        invoke('conversation', ['admit', sid, '--helper-session', 'helper'])
        self.assertEqual('conversation', sessions.load(self.root, sid, 'helper', allow_helpers=True)['owner'])
        with self.assertRaises(ValueError):
            invoke('helper', ['end', sid])
        with self.assertRaises(ValueError):
            invoke('helper', ['begin', '--owner-session', 'helper', '--replace', sid])
        invoke('conversation', ['revoke', sid, '--helper-session', 'helper'])
        with self.assertRaises(ValueError):
            sessions.load(self.root, sid, 'helper', allow_helpers=True)
        invoke('conversation', ['begin', '--owner-session', 'conversation', '--replace', sid])
        self.assertFalse(sessions.session_path(self.root, sid).exists())

    def test_live_status_detects_missing_artifacts_source_changes_and_shared_changes(self):
        root = self.root / 'workspace/pack'
        source = root.parent / 'mod_source/fixture'
        root.mkdir(parents=True); source.mkdir(parents=True)
        for repo in (root, source):
            subprocess.run(['git', 'init', '-q'], cwd=repo, check=True, timeout=5)
            (repo / 'source.txt').write_text('authored')
            subprocess.run(['git', 'add', '.'], cwd=repo, check=True, timeout=5)
            subprocess.run(['git', '-c', 'user.email=test@example.invalid', '-c', 'user.name=Test', 'commit', '-qm', 'fixture'], cwd=repo, check=True, timeout=5)
        (root / 'gradle').mkdir(); (root / 'mods').mkdir()
        manifest = {'verificationInputs': ['source.txt'], 'mods': [{'repository': 'fixture', 'artifact': 'fixture.jar', 'tasks': ['verifyFull']}]}
        (root / 'gradle/active-custom-mods.json').write_text(json.dumps(manifest))
        jar = root / 'mods/fixture.jar'; jar.write_text('verified artifact bytes')
        receipt = dict(baseline='complete', shared_context={'_patterns': 'source.txt', '_java_home': '/nonexistent',
            'source.txt': sessions.file_identity(root / 'source.txt'), 'manifest': sessions.file_identity(root / 'gradle/active-custom-mods.json'),
            'sdk': 'missing', 'toolchain': 'missing'}, mods={'fixture': {'source': sessions.source_identity(source),
                'tasks': ['verifyFull'], 'sha256': sessions.file_identity(jar)}})
        self.assertTrue(sessions.inspect_live(root, receipt)['valid_reuse'])
        jar.unlink()
        self.assertEqual(['fixture'], sessions.inspect_live(root, receipt)['affected_providers'])
        jar.write_text('verified artifact bytes'); (source / 'source.txt').write_text('changed source')
        self.assertEqual(['fixture'], sessions.inspect_live(root, receipt)['affected_providers'])
        (source / 'source.txt').write_text('authored'); (root / 'source.txt').write_text('changed contract')
        self.assertIn('full baseline required: shared verification inputs changed', sessions.inspect_live(root, receipt)['invalidations'])

    def test_identity_is_stable_conversation_not_pane(self):
        with patch.dict(os.environ, {'PI_SESSION_ID': 'stable-conversation', 'HERDR_PANE_ID': 'new-pane'}):
            self.assertEqual('stable-conversation', sessions.caller_owner())


if __name__ == '__main__':
    unittest.main()

#!/usr/bin/env python3
"""Public control receipt tests, without source builds or pack preparation."""
import contextlib
import io
import json
import os
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

    def test_identity_is_stable_conversation_not_pane(self):
        with patch.dict(os.environ, {'PI_SESSION_ID': 'stable-conversation', 'HERDR_PANE_ID': 'new-pane'}):
            self.assertEqual('stable-conversation', sessions.caller_owner())


if __name__ == '__main__':
    unittest.main()

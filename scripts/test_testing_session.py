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
        self.root = Path(tempfile.mkdtemp(prefix='retained-', dir=output))
        # Retain receipts, artifact snapshots and failure state on every test outcome.

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
            self.assertTrue(path.exists())
            self.assertEqual('ended', json.loads(path.read_text())['state'])

    def recovery_fixture(self):
        receipt = sessions.begin(self.root, 'conversation')
        candidate = self.root / 'generated/checkpoint.jar'
        candidate.parent.mkdir(parents=True)
        candidate.write_bytes(b'verified checkpoint bytes')
        receipt.update(baseline='verifying', active_run='20261017T000000Z-123456', helpers=['helper'],
            checkpoints={'provider': {'verification_id': 'original-run', 'sha256': sessions.file_identity(candidate),
                                      'staged_path': 'generated/checkpoint.jar'}},
            shared_fingerprint='unchanged', mods={'prior': {'verification_id': 'prior-run'}})
        path = sessions.session_path(self.root, receipt['session_id'])
        sessions.atomic_write(path, receipt)
        return receipt, path, candidate

    def invoke_recover(self, receipt, owner='conversation', confirmation=None):
        args = ['testing_session.py', '--root', str(self.root), 'recover', receipt['session_id']]
        if confirmation is not None:
            args += ['--confirm-terminated-run', confirmation]
        with patch.dict(os.environ, {'PI_SESSION_ID': owner}), patch('sys.argv', args), contextlib.redirect_stdout(io.StringIO()) as output:
            sessions.main()
        return json.loads(output.getvalue())

    def test_recover_dead_owned_run_preserves_checkpoints_bytes_and_origin(self):
        receipt, path, candidate = self.recovery_fixture()
        before = candidate.read_bytes()
        with patch.object(os, 'kill', side_effect=ProcessLookupError) as probe:
            result = self.invoke_recover(receipt, confirmation=receipt['active_run'])
        probe.assert_called_once_with(123456, 0)
        expected = dict(receipt, baseline='failed', active_run=None)
        self.assertEqual(expected, result)
        self.assertEqual(expected, sessions.load(self.root, receipt['session_id'], 'conversation'))
        self.assertEqual(before, candidate.read_bytes())
        self.assertTrue(path.exists())

    def test_recover_refuses_missing_or_mismatched_confirmation_without_liveness_inference(self):
        receipt, path, candidate = self.recovery_fixture()
        before = path.read_bytes()
        for confirmation in (None, '20261017T000001Z-123456', 'malformed', '20261017T000000Z-0'):
            with self.subTest(confirmation=confirmation), patch.object(os, 'kill') as probe:
                with self.assertRaises(ValueError):
                    self.invoke_recover(receipt, confirmation=confirmation)
                probe.assert_not_called()
                self.assertEqual(before, path.read_bytes())
        self.assertEqual(b'verified checkpoint bytes', candidate.read_bytes())

    def test_recover_refuses_invalid_stored_run_and_pid_even_with_exact_confirmation(self):
        receipt, path, _ = self.recovery_fixture()
        for run in ('not-a-run', '20261017T000000Z-0', '20261017T000000Z-2147483648'):
            value = dict(receipt, active_run=run)
            sessions.atomic_write(path, value)
            before = path.read_bytes()
            with self.subTest(run=run), patch.object(os, 'kill') as probe:
                with self.assertRaises(ValueError):
                    self.invoke_recover(value, confirmation=run)
                probe.assert_not_called()
                self.assertEqual(before, path.read_bytes())

    def test_recover_refuses_live_or_uncertain_coordinator(self):
        receipt, path, candidate = self.recovery_fixture()
        before = path.read_bytes()
        for failure in (None, PermissionError('uncertain coordinator access')):
            with self.subTest(failure=failure), patch.object(os, 'kill', side_effect=failure):
                with self.assertRaises((ValueError, PermissionError)):
                    self.invoke_recover(receipt, confirmation=receipt['active_run'])
                self.assertEqual(before, path.read_bytes())
        self.assertEqual(b'verified checkpoint bytes', candidate.read_bytes())

    def test_recover_is_owner_only_even_for_admitted_helpers(self):
        receipt, path, candidate = self.recovery_fixture()
        before = path.read_bytes()
        for owner in ('foreign', 'helper'):
            with self.subTest(owner=owner), patch.object(os, 'kill') as probe:
                with self.assertRaises(ValueError):
                    self.invoke_recover(receipt, owner=owner, confirmation=receipt['active_run'])
                probe.assert_not_called()
                self.assertEqual(before, path.read_bytes())
        self.assertEqual(b'verified checkpoint bytes', candidate.read_bytes())

    def test_recover_refuses_idle_or_complete_receipts(self):
        receipt, path, _ = self.recovery_fixture()
        for baseline, active in (('pending', None), ('failed', None), ('complete', None), ('complete', receipt['active_run'])):
            value = dict(receipt, baseline=baseline, active_run=active)
            sessions.atomic_write(path, value)
            before = path.read_bytes()
            with self.subTest(baseline=baseline, active=active), patch.object(os, 'kill') as probe:
                with self.assertRaises(ValueError):
                    self.invoke_recover(value, confirmation=receipt['active_run'])
                probe.assert_not_called()
                self.assertEqual(before, path.read_bytes())

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
        self.assertEqual('ended', json.loads(sessions.session_path(self.root, sid).read_text())['state'])

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
        mod = manifest['mods'][0]
        mod['modId'] = 'fixture'
        (root / 'gradle/active-custom-mods.json').write_text(json.dumps(manifest))
        context = {'_patterns': 'source.txt', '_java_home': os.environ.get('JAVA_HOME', '/nonexistent'), 'java': 'synthetic'}
        shared = sessions.current_shared_entries(root, ['source.txt'], context)
        checkpoint = dict(owner='original-conversation', verification_id='original-run',
            source=sessions.source_identity(source), tasks=mod['tasks'], sha256=sessions.file_identity(jar),
            dependencies={}, support_hashes={}, shared_fingerprint=sessions.contract_identity(root, mod, shared))
        immutable = sessions.checkpoint_path(root, mod, checkpoint)
        immutable.parent.mkdir(parents=True)
        immutable.write_bytes(jar.read_bytes())
        checkpoint['staged_path'] = str(immutable.relative_to(root))
        receipt = dict(schema=sessions.SCHEMA, baseline='failed', shared_context=context,
                       mods={}, checkpoints={'fixture': checkpoint})
        self.assertTrue(sessions.inspect_live(root, receipt)['valid_reuse'])
        self.assertEqual({'fixture': 'original-run'}, sessions.inspect_live(root, receipt)['verification_origins'])
        jar.unlink()  # Missing disposable build/deployed outputs do not invalidate retained immutable bytes.
        self.assertTrue(sessions.inspect_live(root, receipt)['valid_reuse'])
        immutable.write_bytes(b'corrupt snapshot')
        self.assertEqual(['fixture'], sessions.inspect_live(root, receipt)['affected_providers'])
        immutable.write_text('verified artifact bytes'); (source / 'source.txt').write_text('changed source')
        self.assertEqual(['fixture'], sessions.inspect_live(root, receipt)['affected_providers'])
        (source / 'source.txt').write_text('authored'); (root / 'source.txt').write_text('changed contract')
        self.assertEqual(['fixture'], sessions.inspect_live(root, receipt)['affected_providers'])

    def test_scoped_partial_checkpoints_history_and_fresh_epoch(self):
        root = self.root
        (root / 'gradle').mkdir()
        (root / 'provider-contract.txt').write_text('provider contract')
        (root / 'unrelated.md').write_text('docs may change')
        mods = [dict(repository='provider', modId='provider', artifact='provider.jar', tasks=['verify'],
                     verificationInputs=['provider-contract.txt'], buildInputs=[], supportArtifacts=['build/support.jar']),
                dict(repository='consumer', modId='consumer', artifact='consumer.jar', tasks=['verify'], dependsOn=['provider']),
                dict(repository='independent', modId='independent', artifact='independent.jar', tasks=['verify'])]
        manifest = dict(verificationInputs=[], mods=mods)
        (root / 'gradle/active-custom-mods.json').write_text(json.dumps(manifest))
        context = dict(_java_home=os.environ.get('JAVA_HOME', '/nonexistent'), java='synthetic')
        receipt = dict(schema=sessions.SCHEMA, baseline='failed', shared_context=context, checkpoints={}, mods={})
        sources = {mod['repository']: mod['repository'] for mod in mods}
        with patch.object(sessions, 'git_text', return_value='provider-contract.txt\0unrelated.md\0'), \
                patch.object(sessions, 'source_identity', side_effect=lambda repo: sources[repo.name]):
            shared = sessions.current_shared_entries(root, [], context)
            for mod in mods:
                checkpoint = dict(owner='historical-owner', verification_id='historical-run',
                    source=mod['repository'], tasks=mod['tasks'], dependencies={}, support_hashes={},
                    shared_fingerprint=sessions.contract_identity(root, mod, shared),
                    build_fingerprint=sessions.build_identity(root, mod, shared), build_id='original-build')
                path = sessions.checkpoint_path(root, mod, checkpoint)
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(mod['repository'])
                checkpoint.update(sha256=sessions.file_identity(path), staged_path=str(path.relative_to(root)))
                if mod['repository'] == 'provider':
                    support = path.with_name(path.name + '.support') / 'build/support.jar'
                    support.parent.mkdir(parents=True); support.write_text('support bytes')
                    checkpoint['support_hashes'] = {'build/support.jar': sessions.file_identity(support)}
                if mod.get('dependsOn'):
                    checkpoint['dependencies'] = {'provider': receipt['checkpoints']['provider']['sha256']}
                receipt['checkpoints'][mod['repository']] = checkpoint
            self.assertTrue(sessions.inspect_live(root, receipt)['valid_reuse'])
            (root / 'unrelated.md').write_text('unrelated edit')
            self.assertTrue(sessions.inspect_live(root, receipt)['valid_reuse'])
            archive = root / '.worklane/testing-sessions/completed'
            archive.mkdir()
            (archive / 'historical-run.json').write_text(json.dumps(receipt))
            fresh = dict(schema=sessions.SCHEMA, baseline='pending', shared_context=context)
            self.assertTrue(sessions.inspect_live(root, fresh)['valid_reuse'])
            provider_snapshot = sessions.checkpoint_path(root, mods[0], receipt['checkpoints']['provider'])
            support = provider_snapshot.with_name(provider_snapshot.name + '.support') / 'build/support.jar'
            support.write_text('corrupt support bytes')
            self.assertEqual(['consumer', 'provider'], sessions.inspect_live(root, fresh)['affected_providers'])
            support.write_text('support bytes')
            self.assertEqual(['consumer', 'independent', 'provider'],
                             sessions.inspect_live(root, dict(fresh, reuse_history=False))['affected_providers'])
            (root / 'provider-contract.txt').write_text('changed contract')
            self.assertEqual(['consumer', 'provider'], sessions.inspect_live(root, fresh)['affected_providers'])
            self.assertEqual('original-build', sessions.inspect_live(root, fresh)['build_reuse_candidates']['provider'])
            changed_toolchain = dict(fresh, shared_context=dict(context, java='different resolved Java identity'))
            self.assertEqual({}, sessions.inspect_live(root, changed_toolchain)['build_reuse_candidates'])
            sources['provider'] = 'changed production identity'
            self.assertNotIn('provider', sessions.inspect_live(root, fresh)['build_reuse_candidates'])
            sources['provider'] = 'provider'
            (root / 'provider-contract.txt').write_text('provider contract')
            mods[0]['tasks'] = ['verify', 'newRequiredGate']
            (root / 'gradle/active-custom-mods.json').write_text(json.dumps(manifest))
            self.assertEqual(['consumer', 'provider'], sessions.inspect_live(root, fresh)['affected_providers'])
            self.assertIn('provider', sessions.inspect_live(root, fresh)['build_reuse_candidates'])
            mods[0]['tasks'] = ['verify']
            mods[0]['verificationProfiles'] = [dict(name='optional', tasks=['verify'], properties={'optionalRuntime': 'true'}),
                                                dict(name='default', tasks=['verify'])]
            (root / 'gradle/active-custom-mods.json').write_text(json.dumps(manifest))
            self.assertEqual(['consumer', 'provider'], sessions.inspect_live(root, fresh)['affected_providers'])
            self.assertIn('provider', sessions.inspect_live(root, fresh)['build_reuse_candidates'])
            original_profile_contract = sessions.contract_identity(root, mods[0], shared)
            mods[0]['verificationProfiles'][0]['properties']['optionalRuntime'] = 'false'
            self.assertNotEqual(original_profile_contract, sessions.contract_identity(root, mods[0], shared))
            del mods[0]['verificationProfiles']
            mods[0]['buildInputs'] = ['provider-contract.txt']
            (root / 'gradle/active-custom-mods.json').write_text(json.dumps(manifest))
            self.assertNotIn('provider', sessions.inspect_live(root, fresh)['build_reuse_candidates'])
            mods[0]['buildInputs'] = []
            (root / 'gradle/active-custom-mods.json').write_text(json.dumps(manifest))
            active = dict(receipt, active_run='live-run')
            (archive / 'historical-run.json').write_text(json.dumps(active))
            self.assertFalse(sessions.inspect_live(root, fresh)['valid_reuse'])
            legacy = dict(receipt, schema='bc.testing_session.v1')
            (archive / 'historical-run.json').write_text(json.dumps(legacy))
            self.assertFalse(sessions.inspect_live(root, fresh)['valid_reuse'])
            self.assertEqual(legacy, json.loads((archive / 'historical-run.json').read_text()))

    def test_profile_identity_matches_kotlin_separator_contract(self):
        profiles = [dict(name='present', tasks=['verifyFull', 'stageRuntimeJar'], properties={'z': 'false', 'a': 'true'}),
                    dict(name='default', tasks=['stageRuntimeJar'], clean=False)]
        self.assertEqual('present\0true\0verifyFull\x01stageRuntimeJar\0a=true\x01z=false\ndefault\0false\0stageRuntimeJar\0',
                         sessions.verification_profile_identity(profiles))

    def test_identity_is_stable_conversation_not_pane(self):
        with patch.dict(os.environ, {'PI_SESSION_ID': 'stable-conversation', 'HERDR_PANE_ID': 'new-pane'}):
            self.assertEqual('stable-conversation', sessions.caller_owner())


if __name__ == '__main__':
    unittest.main()

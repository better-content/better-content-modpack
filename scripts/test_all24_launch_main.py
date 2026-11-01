#!/usr/bin/env python3
"""Negative gates for the nonshipping one-shot Build22 controller; no JVM launch."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from types import SimpleNamespace
import os

SOURCE = Path(__file__).with_name('all24_launch_main.py')
spec = importlib.util.spec_from_file_location('all24_launch_main_tested', SOURCE)
launch = importlib.util.module_from_spec(spec)
spec.loader.exec_module(launch)


class GateTests(unittest.TestCase):
    def test_duplicate_json_rejected(self):
        with self.assertRaisesRegex(ValueError, 'Duplicate'):
            launch.strict(b'{"phase":"source","phase":"target"}')

    def test_nonfinite_json_rejected(self):
        with self.assertRaisesRegex(ValueError, 'Nonfinite'):
            launch.strict(b'{"accepted":NaN}')

    def test_symlink_rejected(self):
        with tempfile.TemporaryDirectory(dir='/home/dev/.tmp') as temp:
            root = Path(temp)
            (root / 'actual').write_text('x')
            (root / 'link').symlink_to(root / 'actual')
            with self.assertRaisesRegex(ValueError, 'Unsafe'):
                launch.safe(root / 'link')

    def test_wrong_contract_generation_rejected(self):
        with tempfile.TemporaryDirectory(dir='/home/dev/.tmp') as temp:
            root = Path(temp)
            state = root / '.better_world_management'
            state.mkdir()
            (state / 'lineage-v5.tsv').write_text('BC_PRESTIGE_LINEAGE_V5\nlineage\tlineage-' + 'a'*32 +
                                                   '\ngeneration\t23\ntotal_prestiges\t24\n')
            with patch.object(launch, 'bootstrap_module', return_value=SimpleNamespace(LINEAGE='lineage-all24-main22')):
                with self.assertRaisesRegex(ValueError, 'Wrong native lineage'):
                    launch.lineage(root, 24)

    def test_fresh_nonce_requires_explicit_matching_name(self):
        root = launch.RUNTIMES / 'new-one-shot'
        with self.assertRaisesRegex(ValueError, 'Explicit fresh nonce'):
            launch.runtime_identity(root, 'a'*64, 'b'*64, None)
        with self.assertRaisesRegex(ValueError, 'Explicit fresh nonce'):
            launch.runtime_identity(root, 'a'*64, 'b'*64, 'different')

    def test_target_rechecks_live_release_and_source_build_admission(self):
        runtime = launch.RUNTIMES / 'fresh-nonce'
        material = {'schema': 'world_axioms_main_build26_materialization_v1',
                    'status': 'materialized_unaccepted', 'destination': str(runtime),
                    'accepted_zip_expected_sha256':
                        'c18aebc94e5b5d14860c5ef72725f65003d6f313c37295f7833216ec40d0c6fa',
                    'client_zip_expected_sha256':
                        'e6bad4c0e1598dfd917e8494d3640a18b11d8268e7dcf26687c7743596145af9',
                    'pinned_release': {'ready_sha256': 'old'}}
        live = SimpleNamespace(parent=SimpleNamespace(materializer=lambda:
                SimpleNamespace(admit=lambda: {'ready_sha256': 'changed'})))
        def digest(path):
            name = str(path)
            if name.endswith('runtime-inputs-v1.json'): return 'a'*64
            if name.endswith('all24-source-bootstrap-v1/result.json'): return 'b'*64
            if name.endswith('native-fixtures.jar'): return launch.FIXTURE_SHA
            raise AssertionError('Unexpected digest path: ' + name)
        with patch.object(launch, 'runtime_identity'), patch.object(launch, 'bootstrap_module', return_value=live), \
             patch.object(launch, 'sha', side_effect=digest), patch.object(launch, 'read', return_value=material):
            with self.assertRaisesRegex(ValueError, 'Live ready/Smoke/49-mod/source-build admission changed'):
                launch.validate_pins(runtime, 'a'*64, 'b'*64, 'fresh-nonce')

    def test_health_rejects_unrequested_or_inconsistent_biome(self):
        choices = ('minecraft:plains', 'minecraft:forest', 'minecraft:meadow')
        reset = {'biome_' + str(i): choice for i, choice in enumerate(choices, 1)}
        successor = dict(reset)
        health = {'requested_biome_' + str(i): choice for i, choice in enumerate(choices, 1)}
        health.update(resolved_biome='minecraft:desert', actual_biome='minecraft:desert')
        with self.assertRaisesRegex(ValueError, 'actually requested'):
            launch.health_preferences(health, reset, successor, {})
        health.update(resolved_biome='minecraft:forest', actual_biome='minecraft:plains')
        with self.assertRaisesRegex(ValueError, 'actually requested'):
            launch.health_preferences(health, reset, successor, {})

    def test_target_requires_native_selected_biome_arrival(self):
        proof = {'fixture': {'target_phase': 'claims_and_disk_proof_pass',
                  'target_process': '2@2026-10-31T00:00:00Z',
                  'source_process': '1@2026-10-31T00:00:00Z'},
                 'health': {'resolved_biome': 'minecraft:forest'}}
        with self.assertRaisesRegex(ValueError, 'selected-biome arrival'):
            launch.target_disk(Path('/home/dev/.tmp'), proof)

    def test_config_paths_are_attested_separately_not_misreported_immutable(self):
        runtime = launch.RUNTIMES / 'unstarted-attestation-test'
        material = {'final_files': {'config/TFTH.toml': {}, 'mods/better-celestial-events-0.1.0.jar': {}}}
        paths = launch.immutable_paths(runtime, material, 'source', runtime / launch.OUT / 'source')
        self.assertNotIn(runtime / 'config/TFTH.toml', paths)
        self.assertIn(runtime / 'mods/better-celestial-events-0.1.0.jar', paths)
        self.assertIn(launch.ROOT / 'scripts/all24_config_attestation_main.py', paths)

    def test_unknown_cleanup_still_signals_only_exact_tagged_pidfd(self):
        with tempfile.TemporaryDirectory(dir='/home/dev/.tmp') as temp:
            root = Path(temp); entry = root / '700020'; entry.mkdir()
            (entry / 'stat').write_text('700020 (java) S ' + '0 '*18 + '123\n')
            (entry / 'environ').write_bytes((launch.OWNER_KEY + '=exact-tag\0').encode())
            launch.TOKEN_SEEN_PIDS['exact-tag'] = {}
            original_exists = Path.exists
            with patch.object(launch, 'Path', side_effect=lambda p: root if p == '/proc' else Path(p)), \
                 patch.object(Path, 'exists', lambda p: False if p == entry else original_exists(p)), \
                 patch.object(launch.os, 'pidfd_open', return_value=42) as open_fd, \
                 patch.object(launch.os, 'close'), \
                 patch.object(launch.signal, 'pidfd_send_signal') as sent:
                result = launch.cleanup_known_tagged_after_unknown('exact-tag')
            self.assertEqual(result['exact_owner_identities'], [{'pid': 700020, 'start_tick': 123}])
            self.assertFalse(result['clean_census_claimed'])
            self.assertEqual(open_fd.call_count, 2)
            self.assertEqual(sent.call_count, 2)

    def test_owned_readable_empty_zombie_is_not_clean(self):
        with tempfile.TemporaryDirectory(dir='/home/dev/.tmp') as temp:
            proc = Path(temp); entry = proc / '700001'; entry.mkdir()
            (entry / 'status').write_text(f'Name:\tz\nState:\tZ (zombie)\nUid:\t{os.getuid()} {os.getuid()} {os.getuid()} {os.getuid()}\n')
            (entry / 'environ').write_bytes(b'')
            (entry / 'stat').write_text('700001 (z) Z ' + '0 '*18 + '123\n')
            token = 'zombie-token'
            launch.TOKEN_START[token] = 0
            launch.TOKEN_SEEN_PIDS[token] = {700001: 123}
            with patch.object(launch, 'Path', side_effect=lambda p: proc if p == '/proc' else Path(p)):
                self.assertIn(700001, launch.owned(token))

    def test_owned_unreadable_same_uid_post_token_is_unknown(self):
        with tempfile.TemporaryDirectory(dir='/home/dev/.tmp') as temp:
            proc = Path(temp); entry = proc / '700002'; entry.mkdir()
            (entry / 'status').write_text(f'Name:\tp\nState:\tS (sleeping)\nUid:\t{os.getuid()} {os.getuid()} {os.getuid()} {os.getuid()}\n')
            (entry / 'environ').write_bytes(b'')
            (entry / 'stat').write_text('700002 (p) S ' + '0 '*18 + '123\n')
            token = 'unreadable-token'
            launch.TOKEN_START[token] = 0
            launch.TOKEN_SEEN_PIDS[token] = {}
            original = Path.read_bytes
            def refuse(path):
                if path.name == 'environ': raise PermissionError('test denied')
                return original(path)
            with patch.object(launch, 'Path', side_effect=lambda p: proc if p == '/proc' else Path(p)), \
                 patch.object(Path, 'read_bytes', refuse):
                with self.assertRaisesRegex(RuntimeError, 'UNKNOWN: post-token same-UID'):
                    launch.owned(token)

    def test_prior_owner_requires_exact_recorded_mint_tick_not_wall_time(self):
        from datetime import datetime, timezone
        started = datetime.now(timezone.utc).isoformat()
        with self.assertRaisesRegex(ValueError, 'Exact prior owner monotonic mint tick required'):
            launch.mark_token('missing-mint-probe', started)
        token = 'exact-mint-probe'
        now = int(float(Path('/proc/uptime').read_text().split()[0]) * launch.CLOCK_TICKS)
        launch.mark_token(token, started, now - 10)
        self.assertEqual(launch.TOKEN_START[token], now - 10)
        with self.assertRaisesRegex(ValueError, 'Owner token mint time cannot be rebound'):
            launch.mark_token(token, started, now - 5)
        with self.assertRaisesRegex(ValueError, 'Exact prior owner monotonic mint tick required'):
            launch.mark_token('future-mint-probe', started, now + 5000)

    def test_unreadable_short_lived_process_exits_before_identity_recheck(self):
        with tempfile.TemporaryDirectory(dir='/home/dev/.tmp') as temp:
            proc = Path(temp); entry = proc / '700004'; entry.mkdir()
            (entry / 'status').write_text(f'Name:\tgit\nState:\tR (running)\nUid:\t{os.getuid()} {os.getuid()} {os.getuid()} {os.getuid()}\n')
            (entry / 'environ').write_bytes(b'')
            token = 'transient-token'
            launch.TOKEN_START[token] = 120
            launch.TOKEN_SEEN_PIDS[token] = {}
            original = Path.read_bytes
            def refuse(path):
                if path.name == 'environ': raise PermissionError('process exited')
                return original(path)
            with patch.object(launch, 'Path', side_effect=lambda p: proc if p == '/proc' else Path(p)), \
                 patch.object(Path, 'read_bytes', refuse), \
                 patch.object(launch, 'process_start_ticks', side_effect=[123, FileNotFoundError()]):
                self.assertEqual(launch.owned(token), {})

    def test_unreadable_zombie_only_exempt_with_exact_older_untagged_parent(self):
        with tempfile.TemporaryDirectory(dir='/home/dev/.tmp') as temp:
            root = Path(temp); parent = root / '700010'; child = root / '700011'
            parent.mkdir(); child.mkdir()
            (parent / 'stat').write_text('700010 (pi) S ' + '0 '*18 + '100\n')
            (parent / 'status').write_text(f'Name:\tpi\nState:\tS (sleeping)\nPPid:\t0\nUid:\t{os.getuid()} {os.getuid()} {os.getuid()} {os.getuid()}\n')
            (parent / 'environ').write_bytes(b'PI_SESSION=test\0')
            (child / 'stat').write_text('700011 (rg) Z ' + '700010 ' + '0 '*17 + '123\n')
            (child / 'status').write_text(f'Name:\trg\nState:\tZ (zombie)\nPPid:\t700010\nUid:\t{os.getuid()} {os.getuid()} {os.getuid()} {os.getuid()}\n')
            (child / 'environ').write_bytes(b'')
            token = 'external-zombie-token'
            launch.TOKEN_START[token] = 120
            launch.TOKEN_SEEN_PIDS[token] = {}
            launch.TOKEN_EXTERNAL_ZOMBIES[token] = {}
            original = Path.read_bytes
            def refuse(path):
                if path == child / 'environ': raise PermissionError('zombie env cleared')
                return original(path)
            with patch.object(launch, 'Path', side_effect=lambda p: root if p == '/proc' else Path(p)), \
                 patch.object(Path, 'read_bytes', refuse):
                self.assertEqual(launch.owned(token), {})
                self.assertEqual(launch.TOKEN_EXTERNAL_ZOMBIES[token][700011]['parent_start_tick'], 100)
                (parent / 'environ').write_bytes((launch.OWNER_KEY + '=other-owner\0').encode())
                with self.assertRaisesRegex(RuntimeError, 'UNKNOWN: post-token same-UID'):
                    launch.owned(token)
                (parent / 'environ').write_bytes(b'PI_SESSION=test\0')
                launch.TOKEN_SEEN_PIDS[token][700011] = 123
                self.assertIn(700011, launch.owned(token))

    def test_refused_protected_process_diagnostic_never_includes_environ(self):
        with tempfile.TemporaryDirectory(dir='/home/dev/.tmp') as temp:
            proc = Path(temp) / '700003'; proc.mkdir()
            (proc / 'stat').write_text('700003 (diagnostic) S ' + '0 '*18 + '123\n')
            (proc / 'cmdline').write_bytes(b'/usr/lib/systemd/systemd\0--user\0')
            (proc / 'cgroup').write_text('0::/user.slice/user-1001.slice\n')
            status = f'Name:\tdiagnostic\nState:\tS (sleeping)\nPPid:\t1\nUid:\t{os.getuid()} {os.getuid()} {os.getuid()} {os.getuid()}\n'
            launch.TOKEN_START['diagnostic-token'] = 120
            proof = launch.protected_process_identity(proc, status, 'diagnostic-token')
            self.assertEqual(proof['process']['start_tick'], 123)
            self.assertEqual(proof['ppid'], '1')
            self.assertNotIn('environ', json.dumps(proof))
            self.assertEqual(proof['process']['command0'], '/usr/lib/systemd/systemd')

    def test_native_false_save_rejected(self):
        row = {'schema': 'world_axioms_native_fixtures_v1', 'status': 'PASS',
               'actual_dedicated': True, 'world_saved': False,
               'forge_gametest_enabled': False, 'save_mode': 'normal_shutdown',
               'classes': [launch.CLASS], 'cases': [{'name': launch.TESTS['target'],
               'required': True, 'completed': True, 'passed': True, 'failed': False}],
               'expected': 1, 'completed': 1, 'required': 1, 'failed': 0, 'process_id': 1,
               'process_started_at': '2026-10-31T00:00:00Z',
               'last_case_completed_at': '2026-10-31T00:00:01Z'}
        with self.assertRaisesRegex(ValueError, 'normal-save'):
            launch.native_pass(row, launch.TESTS['target'])

    def test_native_wrong_case_rejected(self):
        row = {'schema': 'world_axioms_native_fixtures_v1', 'status': 'PASS',
               'actual_dedicated': True, 'world_saved': True,
               'forge_gametest_enabled': False, 'save_mode': 'normal_shutdown',
               'classes': [launch.CLASS], 'cases': [{'name': launch.TESTS['source'],
               'required': True, 'completed': True, 'passed': True, 'failed': False}],
               'expected': 1, 'completed': 1, 'required': 1, 'failed': 0, 'process_id': 1,
               'process_started_at': '2026-10-31T00:00:00Z',
               'last_case_completed_at': '2026-10-31T00:00:01Z'}
        with self.assertRaisesRegex(ValueError, 'Wrong, incomplete'):
            launch.native_pass(row, launch.TESTS['target'])

    def test_bundle_rejects_fixture_class_collision_before_install(self):
        import zipfile
        with tempfile.TemporaryDirectory(dir='/home/dev/.tmp') as temp:
            root = Path(temp)
            main, fixture, out = root / 'main.jar', root / 'fixture.jar', root / 'out'
            out.mkdir()
            base = b'modLoader="javafml"\nloaderVersion="[47,)"\nlicense="MIT"\n[[mods]]\nmodId="better_world_management"\n'
            test = b'modLoader="javafml"\nloaderVersion="[47,)"\nlicense="MIT"\n[[mods]]\nmodId="better_world_management_feasibility"\n[[dependencies.better_world_management_feasibility]]\nmodId="better_world_management"\n'
            with zipfile.ZipFile(main, 'w') as z:
                z.writestr('META-INF/mods.toml', base)
                z.writestr('com/example/Collide.class', b'A')
            with zipfile.ZipFile(fixture, 'w') as z:
                z.writestr('META-INF/mods.toml', test)
                z.writestr('com/example/Collide.class', b'B')
            original = main.read_bytes()
            with patch.object(launch, 'PRODUCTION_SHA', launch.sha(main)), patch.object(launch, 'FIXTURE_SHA', launch.sha(fixture)):
                with self.assertRaisesRegex(ValueError, 'overrides production'):
                    launch.bundle(main, fixture, out)
            self.assertEqual(main.read_bytes(), original)

    def test_checkpoint_rejects_failed_result_before_reading_world(self):
        with tempfile.TemporaryDirectory(dir='/home/dev/.tmp') as temp:
            root = Path(temp)
            path = root / launch.OUT / 'source/result.json'
            path.parent.mkdir(parents=True)
            path.write_text(json.dumps({'schema': launch.SCHEMA, 'phase': 'source',
                                        'status': 'FAILED', 'phase_accepted': False,
                                        'runtime': str(root), 'exit_code': 0}))
            with self.assertRaisesRegex(ValueError, 'not an accepted'):
                launch.checkpoint(root, 'target', launch.sha(path))

    def test_restart_rejects_changed_journal(self):
        with tempfile.TemporaryDirectory(dir='/home/dev/.tmp') as temp:
            root = Path(temp)
            (root / 'postclaim-restart-observation-v1.json').write_text(json.dumps({
                'schema': 'world_axiom_postclaim_restart_observation_v1', 'manifest_sha256': 'a'*64,
                'transaction': 'tx', 'journal_sha256': 'b'*64,
                'prior_target_process': '2@2026-10-31T00:00:00Z',
                'process': '3@2026-10-31T00:00:00Z',
                'case_observation': 'PASS_AWAITING_NORMAL_SHUTDOWN_AND_POSTEXIT_SAVE_VERIFICATION'}))
            (root / 'world/data/better_world_management').mkdir(parents=True)
            (root / 'world/data/better_world_management/world-axiom-journal-v1.json').write_text('{}')
            proof = {'transaction': 'tx', 'fixture': {'source_process': '1@2026-10-31T00:00:00Z',
                     'target_process': '2@2026-10-31T00:00:00Z'}}
            target = {'target_native_disk_proof': {'journal_sha256': 'b'*64}, 'promotion': proof}
            result = {'native_results': {'report': {'evidence': {'process_id': 3,
                      'process_started_at': '2026-10-31T00:00:00Z'}}}}
            with self.assertRaisesRegex(ValueError, 'released claim journal'):
                launch.verify_restart(root, target, proof, result, 'a'*64)


if __name__ == '__main__':
    unittest.main()

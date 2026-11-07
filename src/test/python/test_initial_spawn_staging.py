"""Policy and saved-state fixtures for the closed dedicated initial-spawn transaction.

Do not run without the project owner's explicit testing approval.
"""
import gzip
import importlib.util
from pathlib import Path
import struct
import tempfile
import unittest
import zipfile
from unittest.mock import patch

SCRIPT = Path(__file__).resolve().parents[3] / 'better-world-management-initial-spawn.py'
spec = importlib.util.spec_from_file_location('initial_spawn_staging', SCRIPT)
spawn = importlib.util.module_from_spec(spec)
spec.loader.exec_module(spawn)


def tag(tag_id, name, payload):
    encoded = name.encode('utf-8')
    return struct.pack('>BH', tag_id, len(encoded)) + encoded + payload


def compound(*entries):
    return b''.join(entries) + b'\0'


def number(name, value, tag_id=3):
    return tag(tag_id, name, struct.pack('>q' if tag_id == 4 else '>i', value))


class DedicatedSpawnStageTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(dir=Path(__file__).resolve().parents[3])
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name) / 'candidate'
        (self.root / spawn.RECEIPT.parent).mkdir(parents=True)
        (self.root / spawn.STATUS).write_text('pending\n')
        nbt = tag(10, '', compound(tag(10, 'Data', compound(
            number('SpawnX', 128), number('SpawnY', 72), number('SpawnZ', 128),
            tag(10, 'WorldGenSettings', compound(number('seed', 17, 4)))))))
        (self.root / 'level.dat').write_bytes(gzip.compress(nbt))
        self.receipt = (self.root / spawn.RECEIPT)
        self.receipt.write_text('\n'.join((
            'BC_INITIAL_SPAWN_ATTEMPT_V3', 'attempt\trun-1', 'target\tlineage',
            'number\t1', 'seed\t17', 'status\tresolved', 'provenance\trandom',
            'spawn\t128\t72\t128', 'biome\tminecraft:plains',
            'base_temp_mc\t0.6', 'min_temp_mc\t0.3', 'max_temp_mc\t0.8', '')))

    def test_fresh_world_requires_a_companion_mod_with_the_pre_entry_gate(self):
        (self.root / 'mods').mkdir()
        with self.assertRaisesRegex(RuntimeError, 'exactly one'):
            spawn.require_companion_admission_gate(self.root)
        with zipfile.ZipFile(self.root / 'mods' / 'better-world-management-0.1.0.jar', 'w') as compiled:
            compiled.writestr('com/bettercontent/betterworldmanagement/InitialSpawnCreation.class', b'')
        with self.assertRaisesRegex(RuntimeError, 'lacks the pre-entry admission gate'):
            spawn.require_companion_admission_gate(self.root)

    def test_raw_seed_provenance_is_conservative(self):
        self.assertEqual('random', spawn.seed_mode(''))
        self.assertEqual('random', spawn.seed_mode(' \t '))
        self.assertEqual('explicit', spawn.seed_mode('0'))
        self.assertEqual('explicit', spawn.seed_mode('custom text'))
        self.assertEqual('unknown', spawn.seed_mode(None))

    def test_random_seed_retry_cannot_repeat_a_candidate_seed(self):
        used = {17}
        with patch.object(spawn.secrets, 'token_bytes', side_effect=[
                (17).to_bytes(8, 'big', signed=True), (19).to_bytes(8, 'big', signed=True)]):
            self.assertEqual(19, spawn.new_seed(used))
        self.assertEqual({17, 19}, used)

    def test_server_properties_preserve_explicit_input_and_close_all_public_listeners(self):
        raw = b'# original settings\nlevel-name=lineage\nlevel-seed=My Seed\nserver-ip=\nserver-port=25565\n'
        result = spawn.properties(spawn.staged_properties(raw, 'bc-initial-one', 'My Seed'))
        self.assertEqual('My Seed', result['level-seed'])
        self.assertEqual('127.0.0.1', result['server-ip'])
        self.assertEqual('0', result['server-port'])
        self.assertEqual('false', result['enable-rcon'])
        self.assertEqual('false', result['enable-query'])
        self.assertEqual('bc-initial-one', result['level-name'])
        self.assertNotIn('level-seed', spawn.properties(spawn.staged_properties(b'level-name=lineage\n', 'stage', None)))
        for ambiguous in (b'level-seed:My Seed\n', b' level-seed=My Seed\n',
                          b'level-seed=\nlevel-seed:Hidden Explicit\n'):
            with self.subTest(ambiguous=ambiguous), self.assertRaisesRegex(RuntimeError, 'ambiguous'):
                spawn.properties(ambiguous)

    def test_v3_receipt_matches_saved_state_without_a_biome_whitelist(self):
        # No config/better_world_management-biomes.txt exists in this fixture.
        self.assertEqual('resolved', spawn.receipt_info(
            self.root, 'run-1', 'lineage', 'random', 1))
        self.assertEqual('pending', (self.root / spawn.STATUS).read_text().strip())

    def test_v3_receipt_requires_exact_identity_saved_state_and_resolved_status(self):
        original = self.receipt.read_text()
        for before, after, error in (
                ('spawn\t128\t72\t128', 'spawn\t0\t72\t128', 'differs'),
                ('seed\t17', 'seed\t18', 'differs'),
                ('attempt\trun-1', 'attempt\tother', 'identity'),
                ('biome\tminecraft:plains', 'biome\tinvalid\tbiome', 'biome is malformed'),
                ('status\tresolved', 'status\tfallback', 'resolved V3 status'),
                ('number\t1', 'number\t1\textra', 'identity'),
                ('seed\t17', 'seed\t17\textra', 'seed is malformed')):
            with self.subTest(after=after):
                self.receipt.write_text(original.replace(before, after))
                with self.assertRaisesRegex(RuntimeError, error):
                    spawn.receipt_info(self.root, 'run-1', 'lineage', 'random', 1)
        self.receipt.write_text(original.replace('provenance\trandom', 'provenance\texplicit')
                                .replace('status\tresolved', 'status\tfallback'))
        with self.assertRaisesRegex(RuntimeError, 'resolved V3 status'):
            spawn.receipt_info(self.root, 'run-1', 'lineage', 'explicit', 1)
        self.receipt.write_text(original + 'extra\tline\n')
        with self.assertRaisesRegex(RuntimeError, 'line count'):
            spawn.receipt_info(self.root, 'run-1', 'lineage', 'random', 1)

    def test_v3_temperature_fields_are_finite_ordered_and_inclusive(self):
        original = self.receipt.read_text()
        for base in ('0.3', '0.8', '6.0E-1'):
            with self.subTest(base=base):
                self.receipt.write_text(original.replace('base_temp_mc\t0.6', 'base_temp_mc\t' + base))
                self.assertEqual('resolved', spawn.receipt_info(self.root, 'run-1', 'lineage', 'random', 1))
        for before, after, error in (
                ('base_temp_mc\t0.6', 'base_temp_mc\t0.2', 'inclusive range'),
                ('max_temp_mc\t0.8', 'max_temp_mc\t0.4', 'inclusive range'),
                ('min_temp_mc\t0.3', 'min_temp_mc\t0.9', 'inclusive range'),
                ('base_temp_mc\t0.6', 'base_temp_mc\tNaN', 'malformed'),
                ('base_temp_mc\t0.6', 'base_temp_mc\t1e999', 'not finite'),
                ('min_temp_mc\t0.3', 'min_temp_mc\tInfinity', 'malformed'),
                ('max_temp_mc\t0.8', 'max_temp_mc\t-1e999', 'not finite'),
                ('base_temp_mc\t0.6', 'min_temp_mc\t0.6', 'base_temp_mc is malformed'),
                ('max_temp_mc\t0.8', 'max_temp_mc\t0.8\t0.9', 'max_temp_mc is malformed')):
            with self.subTest(after=after):
                self.receipt.write_text(original.replace(before, after))
                with self.assertRaisesRegex(RuntimeError, error):
                    spawn.receipt_info(self.root, 'run-1', 'lineage', 'random', 1)

    def test_historical_v2_is_opt_in_and_never_accepted_for_new_candidates(self):
        old = self.receipt.read_text().splitlines()[:9]
        old[0] = 'BC_INITIAL_SPAWN_ATTEMPT_V2'
        self.receipt.write_text('\n'.join(old) + '\n')
        with self.assertRaisesRegex(RuntimeError, 'requires a V3'):
            spawn.receipt_info(self.root, 'run-1', 'lineage', 'random', 1)
        self.assertEqual('resolved', spawn.receipt_info(
            self.root, 'run-1', 'lineage', 'random', 1, legacy_v2=True))
        old[5] = 'status\tfallback'
        self.receipt.write_text('\n'.join(old) + '\n')
        with self.assertRaisesRegex(RuntimeError, 'unsafe fallback'):
            spawn.receipt_info(self.root, 'run-1', 'lineage', 'random', 1, legacy_v2=True)
        old[6] = 'provenance\texplicit'
        self.receipt.write_text('\n'.join(old) + '\n')
        self.assertEqual('fallback', spawn.receipt_info(
            self.root, 'run-1', 'lineage', 'explicit', 1, legacy_v2=True))

    def test_publication_never_replaces_an_occupied_world_name(self):
        with tempfile.TemporaryDirectory(dir=Path(__file__).resolve().parents[3]) as temp:
            source, target = Path(temp) / 'closed-candidate', Path(temp) / 'player-world'
            source.mkdir()
            target.mkdir()
            (source / 'level.dat').write_bytes(b'candidate')
            (target / 'level.dat').write_bytes(b'keep this world')
            with self.assertRaises(FileExistsError):
                spawn.rename_noreplace(source, target)
            self.assertEqual(b'candidate', (source / 'level.dat').read_bytes())
            self.assertEqual(b'keep this world', (target / 'level.dat').read_bytes())

    def test_login_marker_or_player_data_forbids_candidate_promotion(self):
        (self.root / spawn.LOGIN).write_text('BC_INITIAL_SPAWN_LOGIN_V1\n')
        with self.assertRaisesRegex(RuntimeError, 'login marker'):
            spawn.ensure_uninhabited(self.root)
        (self.root / spawn.LOGIN).unlink()
        (self.root / 'playerdata').mkdir()
        (self.root / 'playerdata' / 'someone.dat').write_bytes(b'')
        with self.assertRaisesRegex(RuntimeError, 'player data'):
            spawn.ensure_uninhabited(self.root)


if __name__ == '__main__':
    unittest.main()

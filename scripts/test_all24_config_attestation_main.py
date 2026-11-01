#!/usr/bin/env python3
"""Negative Build22 config reference and finite-output controls; no JVM or writes."""
import importlib.util
import json
from pathlib import Path
import unittest

SOURCE = Path(__file__).with_name('all24_config_attestation_main.py')
spec = importlib.util.spec_from_file_location('all24_config_attestation_tested', SOURCE)
config = importlib.util.module_from_spec(spec)
spec.loader.exec_module(config)


class ConfigAttestationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.original, cls.smoke, cls.witness = config.anchors()

    def test_historical_witness_is_only_a_bounded_config_comparison(self):
        report = config.verify(self.witness, self.original, self.smoke, self.witness)
        self.assertEqual(report['status'], 'CONFIG_ONLY_MATCH')
        self.assertIs(report['native_acceptance'], False)
        self.assertEqual(report['candidate_config_files'], 1962)
        candidate = dict(self.witness)
        name = 'globalgamerules-common.toml'
        candidate[name] = candidate[name].replace(b'do_patrol_spawning = false', b'do_patrol_spawning = "unexpected"')
        with self.assertRaisesRegex(ValueError, 'Source gamerule differs from packaged, same-pair Smoke and observed successor spawn'):
            config.verify(candidate, self.original, self.smoke, self.witness)

    def test_closed_disabled_biome_is_only_optional_new_path(self):
        candidate = dict(self.smoke)
        name = 'forgeendertech/Biomes/ae2$spatial_storage.cfg'
        baseline = self.witness['forgeendertech/Biomes/creatingspace$venus_hellground.cfg']
        candidate[name] = baseline.replace(b'creatingspace:venus_hellground', b'ae2:spatial_storage')
        self.assertTrue(config.disabled_generated_biome(name, candidate[name]))
        report = config.verify(candidate, self.original, self.smoke, self.witness)
        self.assertEqual(report['classifications'][name], 'exact_inert_generated_biome_template')
        candidate[name] = candidate[name].replace(b'B:enabled=false', b'B:enabled=true')
        with self.assertRaisesRegex(ValueError, 'non-inert generated biome'):
            config.verify(candidate, self.original, self.smoke, self.witness)
        candidate[name] = baseline
        with self.assertRaisesRegex(ValueError, 'non-inert generated biome'):
            config.verify(candidate, self.original, self.smoke, self.witness)

    def test_live_registered_biome_only_admits_exact_disabled_template(self):
        registry = sorted(['minecraft:biome_' + str(i) for i in range(50)] + ['minecraft:plains'])
        name = 'forgeendertech/Biomes/minecraft$plains.cfg'
        self.assertNotIn(name, self.smoke)
        candidate = dict(self.smoke)
        candidate[name] = config.DISABLED_BIOME_TEMPLATE.replace(
            b'BIOME_NAMESPACE:BIOME_PATH', b'minecraft:plains')
        report = config.verify(candidate, self.original, self.smoke, self.witness,
                               registered_biomes=registry)
        self.assertEqual(report['classifications'][name], 'exact_inert_generated_biome_template')
        self.assertIsNotNone(report['native_biome_registry_sha256'])
        with self.assertRaisesRegex(ValueError, 'Unknown new config path'):
            config.verify(candidate, self.original, self.smoke, self.witness)
        with self.assertRaisesRegex(ValueError, 'unregistered biome ID'):
            config.verify(candidate, self.original, self.smoke, self.witness,
                          registered_biomes=sorted('minecraft:biome_' + str(i) for i in range(50)))
        candidate[name] = candidate[name].replace(b'B:enabled=false', b'B:enabled=true')
        with self.assertRaisesRegex(ValueError, 'non-inert generated biome'):
            config.verify(candidate, self.original, self.smoke, self.witness,
                          registered_biomes=registry)
        with self.assertRaisesRegex(ValueError, 'Invalid native registered biome roster'):
            config.verify(self.smoke, self.original, self.smoke, self.witness,
                          registered_biomes=list(reversed(registry)))

    def test_emi_loot_skipped_keys_permutation_is_not_a_new_policy(self):
        candidate = dict(self.smoke)
        name = 'emi_loot_config.toml'
        original = candidate[name]
        candidate[name] = original.replace(b'"emi_loot.function.set_count_add",\n',
                                           b'"emi_loot.function.unobserved",\n', 1)
        with self.assertRaisesRegex(ValueError, 'EMI skipped key membership changed'):
            config.verify(candidate, self.original, self.smoke, self.witness)

    def test_unknown_new_config_path_rejected(self):
        candidate = dict(self.witness)
        candidate['unexpected-plugin/native.toml'] = b'enabled = true\n'
        with self.assertRaisesRegex(ValueError, 'Unknown new config path'):
            config.verify(candidate, self.original, self.smoke, self.witness)
        candidate.pop('unexpected-plugin/native.toml')
        name = 'forgeendertech/Biomes/new_provider$unobserved_biome.cfg'
        candidate[name] = config.DISABLED_BIOME_TEMPLATE.replace(
            b'BIOME_NAMESPACE:BIOME_PATH', b'new_provider:unobserved_biome')
        self.assertTrue(config.disabled_generated_biome(name, candidate[name]))
        with self.assertRaisesRegex(ValueError, 'Unknown new config path'):
            config.verify(candidate, self.original, self.smoke, self.witness)

    def test_unknown_value_in_known_toml_rejected(self):
        candidate = dict(self.witness)
        old = candidate['c2me.toml']
        self.assertIn(b'globalExecutorParallelism = "default"', old)
        candidate['c2me.toml'] = old.replace(b'globalExecutorParallelism = "default"',
                                             b'globalExecutorParallelism = "999"', 1)
        with self.assertRaisesRegex(ValueError, 'typed TOML changed'):
            config.verify(candidate, self.original, self.smoke, self.witness)

    def test_unknown_value_in_previously_equal_config_rejected(self):
        candidate = dict(self.witness)
        name = 'endermanoverhaul.jsonc'
        candidate[name] = candidate[name].replace(b'"allowSpawning": true',
                                                   b'"allowSpawning": false', 1)
        with self.assertRaisesRegex(ValueError, 'Enderman replacement JSONC differs'):
            config.verify(candidate, self.original, self.smoke, self.witness)

    def test_missing_required_config_rejected(self):
        candidate = dict(self.witness)
        candidate.pop('c2me.toml')
        with self.assertRaisesRegex(ValueError, 'Missing required Smoke config'):
            config.verify(candidate, self.original, self.smoke, self.witness)

    def test_pair_mismatch_rejected(self):
        ready = config.strict_json(config.READY.read_bytes())
        run = config.strict_json(config.RUN.read_bytes())
        ready['candidates']['server_sha256'] = '0' * 64
        with self.assertRaisesRegex(ValueError, 'pair mismatch'):
            config.validate_receipts(ready, run)

    def test_smoke_reference_change_rejected(self):
        changed = dict(self.smoke)
        changed['c2me.toml'] += b'\n# injected\n'
        with self.assertRaisesRegex(ValueError, 'Smoke config reference changed'):
            config.validate_reference_trees(changed, self.witness)

    def test_source_witness_change_rejected(self):
        changed = dict(self.witness)
        changed['c2me.toml'] += b'\n# injected\n'
        with self.assertRaisesRegex(ValueError, 'Failed-source config witness changed'):
            config.validate_reference_trees(self.smoke, changed)

    def test_duplicate_json_key_rejected(self):
        with self.assertRaisesRegex(ValueError, 'Duplicate JSON key'):
            config.strict_json(b'{"enabled":true,"enabled":false}')

    def test_pvj_probability_change_rejected(self):
        name = 'projectvibrantjourneys/minecraft/oak_trees.json'
        rows = json.loads(self.witness[name])
        rows[0]['chance'] += 1
        with self.assertRaisesRegex(ValueError, 'PVJ biome probabilities changed'):
            config.equivalent(name, self.original.get(name), self.smoke[name], json.dumps(rows).encode())

    def test_unrecognized_semantic_exception_rejected(self):
        with self.assertRaisesRegex(ValueError, 'Unknown changed config path'):
            config.equivalent('new-mod/settings.toml', None, b'a = 1\n', b'a = 2\n')


if __name__ == '__main__':
    unittest.main()

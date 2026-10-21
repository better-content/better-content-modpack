#!/usr/bin/env python3
"""Synthetic baseline-reader equivalence/integrity controls; retained fixtures, no MC."""
import hashlib
import json
from pathlib import Path
import re
import sys
import unittest
from unittest import mock
import zipfile

ROOT = Path(sys.argv[1]).resolve()
EVIDENCE = Path(sys.argv[2]).resolve()
sys.path.insert(0, str(ROOT / 'scripts'))
import structure_loot_inputs as inputs


def table(value):
    return {'type': 'minecraft:chest', 'pools': [], 'fixture_value': value}


class BaselineControls(unittest.TestCase):
    def setUp(self):
        self.base = EVIDENCE / self._testMethodName
        self.base.mkdir(parents=True, exist_ok=False)
        self.root = self.base / 'pack'
        self.home = self.base / 'home'
        (self.root / 'mods').mkdir(parents=True)
        self.cache = self.home / '.cache/bc/packwiz-downloads'
        self.jar(self.cache / 'vanilla/fixture.jar', {'data/test/loot_tables/priority.json': table(0)})
        vanilla = {'filename': 'fixture.jar', 'download': {'hash-format': 'sha256',
                   'hash': hashlib.sha256((self.cache / 'vanilla/fixture.jar').read_bytes()).hexdigest()}}
        for patch in (mock.patch.object(inputs, 'VANILLA', vanilla),
                      mock.patch.object(inputs.Path, 'home', return_value=self.home)):
            patch.start()
            self.addCleanup(patch.stop)
        self.pin('a', {'data/test/loot_tables/priority.json': table(1),
                       'data/test/loot_tables/upstream.json': table(2),
                       'data/test/structures/unused.nbt': b'\x0a\x00\x00\x00',
                       'data/test/worldgen/biome/example.json': {},
                       'data/test/tags/items/unused.json': {'values': []},
                       'test/Witness.class': b'LootTable'})
        self.pin('b', {'data/test/loot_tables/priority.json': table(3)})
        self.jar(self.root / 'mods/local.jar', {'data/test/loot_tables/priority.json': table(4)})
        self.jar(self.root / 'datapacks/archive.zip', {'data/test/loot_tables/priority.json': table(5)})
        self.data('kubejs/data/test/loot_tables/priority.json', table(99))
        self.data('kubejs/data/test/loot_tables/upstream.json', table(99))
        self.data('kubejs/data/test/loot_tables/only_kubejs.json', table(99))
        self.data('kubejs/data/forge/loot_modifiers/global_loot_modifiers.json',
                  {'replace': True, 'entries': []})
        self.data('datapacks/a/data/test/loot_tables/priority.json', table(6))
        self.data('datapacks/z/data/test/loot_tables/priority.json', table(7))
        self.discovery()

    def discovery(self):
        details = {}
        _, _, references, _, _, injections, _ = inputs.load(self.root, fetch=False, details=details)
        self.context = dict(template_references=references, injections=injections,
                            worldgen_consumers=details['worldgen_consumers'])

    def baseline(self):
        return inputs.load_table_baseline(self.root, **self.context)

    def jar(self, path, resources):
        path.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(path, 'w', compression=zipfile.ZIP_DEFLATED) as archive:
            for name, value in resources.items():
                archive.writestr(name, value if isinstance(value, bytes) else json.dumps(value))

    def pin(self, name, resources):
        path = self.cache / 'mods' / (name + '.jar')
        self.jar(path, resources)
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        (self.root / 'mods' / (name + '.pw.toml')).write_text(
            f'filename = "{name}.jar"\n[download]\nhash-format = "sha256"\nhash = "{digest}"\n')

    def data(self, name, value):
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(value))

    def original(self):
        return inputs.load(self.root, fetch=False, pack_tables=False)[0]

    def test_every_layer_priority_matches_full_reader(self):
        baseline = self.baseline()
        self.assertEqual(self.original(), baseline)
        self.assertEqual(table(7), baseline['test:priority'])
        self.assertEqual(table(2), baseline['test:upstream'])
        self.assertNotIn('test:only_kubejs', baseline)

    def test_not_borrowed_from_effective_overrides(self):
        effective = inputs.load(self.root, fetch=False)[0]
        self.assertEqual(table(99), effective['test:upstream'])
        self.assertEqual(table(2), self.baseline()['test:upstream'])

    def test_pin_mutation_after_full_read_is_not_cached(self):
        self.original()
        (self.cache / 'mods/a.jar').write_bytes(b'changed after full inventory')
        with self.assertRaisesRegex(ValueError, 'missing or changed pinned input'):
            self.baseline()

    def test_missing_archive_is_not_skipped(self):
        (self.cache / 'mods/a.jar').rename(self.base / 'retained-missing-a.jar')
        for reader in (self.original, self.baseline):
            with self.assertRaisesRegex(ValueError, 'missing or changed pinned input'):
                reader()

    def test_even_archives_without_tables_are_reverified(self):
        self.pin('b', {'test/Unused.class': b'not a table'})
        self.assertEqual(self.original(), self.baseline())
        (self.cache / 'mods/b.jar').write_bytes(b'mutated unrelated archive')
        with self.assertRaisesRegex(ValueError, 'missing or changed pinned input'):
            self.baseline()

    def test_invalid_json_is_not_filtered(self):
        self.pin('a', {'data/test/loot_tables/upstream.json': b'{malformed'})
        for reader in (self.original, self.baseline):
            with self.assertRaises(json.JSONDecodeError):
                reader()

    def test_malformed_upstream_shape_hidden_by_override_still_fails(self):
        for value in ([], None, 42, {'type': 42}, {'type': 'minecraft:chest', 'pools': [{'entries': [{'type': 42}]}]}):
            with self.subTest(value=value):
                self.pin('a', {'data/test/loot_tables/upstream.json': value})
                # The effective reader's authored override must not certify raw data.
                self.assertEqual(table(99), inputs.load(self.root, fetch=False)[0]['test:upstream'])
                for reader in (self.original, self.baseline):
                    with self.assertRaises(AttributeError):
                        reader()

    def test_baseline_reads_only_table_payloads_not_duplicate_inventory(self):
        reads = []
        original = zipfile.ZipFile.read
        def record(archive, name, *args, **kwargs):
            reads.append(name)
            return original(archive, name, *args, **kwargs)
        with mock.patch.object(zipfile.ZipFile, 'read', autospec=True, side_effect=record):
            baseline = self.baseline()
        self.assertTrue(reads)
        self.assertTrue(all(re.fullmatch(r'data/[^/]+/loot_tables/.+\.json', name) for name in reads))
        self.assertEqual(self.original(), baseline)


if __name__ == '__main__':
    suite = unittest.defaultTestLoader.loadTestsFromTestCase(BaselineControls)
    result = unittest.TextTestRunner(verbosity=2).run(suite)
    if not result.wasSuccessful():
        raise SystemExit(1)
    print('Structure loot input-reader controls: 8 passed; retained synthetic fixtures, not native acceptance.')

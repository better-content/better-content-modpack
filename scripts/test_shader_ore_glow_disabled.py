#!/usr/bin/env python3
"""Check that Complementary does not classify or light up ore blocks."""
import hashlib
from pathlib import Path
import tomllib
import unittest
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
SHADER = 'shaderpacks/ComplementaryReimagined_r5.8.1.zip'
SETTINGS = SHADER + '.txt'


class OreGlowDisabledTests(unittest.TestCase):
    def test_modded_ore_glow_class_is_empty(self):
        with ZipFile(ROOT / SHADER) as archive:
            self.assertIsNone(archive.testzip())
            rows = archive.read('shaders/block.properties').decode().splitlines()
        assignments = [line for line in rows if line.startswith('block.10024=')]
        self.assertEqual(assignments, ['block.10024='])
        # Retain vanilla material mappings; global glow is disabled independently.
        self.assertIn('block.10272=iron_ore', rows)
        self.assertIn('block.10284=copper_ore', rows)

    def test_global_ore_glow_is_disabled(self):
        settings = (ROOT / SETTINGS).read_text().splitlines()
        self.assertEqual([line for line in settings if line.startswith('GLOWING_ORE_MASTER=')],
                         ['GLOWING_ORE_MASTER=0'])

    def test_shader_files_are_hash_pinned(self):
        pack = tomllib.loads((ROOT / 'pack.toml').read_text())
        index_data = (ROOT / pack['index']['file']).read_bytes()
        self.assertEqual(pack['index']['hash'], hashlib.sha256(index_data).hexdigest())
        records = tomllib.loads(index_data.decode())['files']
        for name in (SHADER, SETTINGS):
            matches = [record for record in records if record['file'] == name]
            self.assertEqual(len(matches), 1, name)
            self.assertEqual(matches[0]['hash'], hashlib.sha256((ROOT / name).read_bytes()).hexdigest())


if __name__ == '__main__':
    unittest.main()

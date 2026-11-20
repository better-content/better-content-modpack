#!/usr/bin/env python3
"""Pack-level guard for disabling non-geology Overworld ore generators.

This is a static packaging contract, not a substitute for fresh-world runtime sampling.
"""
import hashlib
import json
from pathlib import Path
import tomllib
import unittest

ROOT = Path(__file__).resolve().parents[1]
SUBWILD = "config/subwild-common.toml"
VEIN_TOGGLE = (
    "datapacks/worldgen_compat_fixes/data/minecraft/worldgen/"
    "density_function/overworld/noise_router/vein_toggle.json"
)
REMOVAL = (
    "datapacks/worldgen_compat_fixes/data/kubejs/forge/biome_modifier/"
    "remove_vanilla_overworld_ores.json"
)


class OreWorldgenContractTests(unittest.TestCase):
    def test_biome_ore_removal_remains_active(self):
        modifier = json.loads((ROOT / REMOVAL).read_text())
        self.assertEqual(modifier["type"], "forge:remove_features")
        self.assertEqual(modifier["biomes"], "#minecraft:is_overworld")
        self.assertEqual(modifier["step"], "underground_ores")
        for feature in ("minecraft:ore_coal_upper", "minecraft:ore_iron_middle",
                        "minecraft:ore_copper", "minecraft:ore_diamond"):
            self.assertIn(feature, modifier["features"])

    def test_only_subwild_ores_are_disabled_not_cave_decoration(self):
        config = tomllib.loads((ROOT / SUBWILD).read_text())
        self.assertEqual(config["Dimension Whitelist"], ["minecraft:overworld"])
        features = config["Features"]
        self.assertIs(features["Generate SubWild Ores"], False)
        for decoration in ("Generate Pebbles", "Generate Speleothems",
                           "Generate Stairs", "Generate Foxfires"):
            self.assertIs(features[decoration], True)

    def test_only_noise_vein_toggle_is_zeroed(self):
        value = json.loads((ROOT / VEIN_TOGGLE).read_text())
        self.assertIs(type(value), float)
        self.assertEqual(value, 0.0)

    def test_both_files_and_index_are_hash_pinned(self):
        pack = tomllib.loads((ROOT / "pack.toml").read_text())
        index_bytes = (ROOT / pack["index"]["file"]).read_bytes()
        self.assertEqual(pack["index"]["hash-format"], "sha256")
        self.assertEqual(pack["index"]["hash"], hashlib.sha256(index_bytes).hexdigest())
        records = tomllib.loads(index_bytes.decode())["files"]
        for path in (SUBWILD, VEIN_TOGGLE):
            matches = [record for record in records if record["file"] == path]
            self.assertEqual(len(matches), 1, path)
            self.assertEqual(matches[0]["hash"],
                             hashlib.sha256((ROOT / path).read_bytes()).hexdigest())


if __name__ == "__main__":
    unittest.main()

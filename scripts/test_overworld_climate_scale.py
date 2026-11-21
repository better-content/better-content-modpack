"""Guard the approved 1.20.1 Overworld climate-only worldgen override.

The complete noise_settings JSON is required by Minecraft's registry format. It was
copied from the Minecraft 1.20.1 server data, with exactly two horizontal scales
changed; the reversed byte hash ensures terrain, caves and ore settings stay vanilla.
"""

import hashlib
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
OVERRIDE = ROOT / "datapacks/worldgen_compat_fixes/data/minecraft/worldgen/noise_settings/overworld.json"
VANILLA_1201_SHA256 = "6a6e1ddfbbbd85c3d8cb0a06a1b6f348185bb3717d3404d0089f7b42440688b4"


class OverworldClimateScaleTest(unittest.TestCase):
    def test_only_temperature_and_humidity_horizontal_scales_differ_from_vanilla(self):
        text = OVERRIDE.read_text()
        settings = json.loads(text)
        for channel, noise in (("temperature", "minecraft:temperature"), ("vegetation", "minecraft:vegetation")):
            router = settings["noise_router"][channel]
            self.assertEqual(router["type"], "minecraft:shifted_noise")
            self.assertEqual(router["noise"], noise)
            self.assertEqual(router["xz_scale"], 0.025)
            self.assertEqual(router["y_scale"], 0.0)
            original = (
                f'"noise": "{noise}",\n'
                '      "shift_x": "minecraft:shift_x",\n'
                '      "shift_y": 0.0,\n'
                '      "shift_z": "minecraft:shift_z",\n'
                '      "xz_scale": 0.025'
            )
            self.assertEqual(text.count(original), 1)
            text = text.replace(original, original.replace('"xz_scale": 0.025', '"xz_scale": 0.25'))
        self.assertEqual(hashlib.sha256(text.encode()).hexdigest(), VANILLA_1201_SHA256)

    def test_pack_is_required_for_new_worlds(self):
        import tomllib

        config = tomllib.loads((ROOT / "config/global_packs.toml").read_text())
        self.assertIn("datapacks/", config["datapacks"]["required"])
        metadata = json.loads((OVERRIDE.parents[4] / "pack.mcmeta").read_text())
        self.assertEqual(metadata["pack"]["pack_format"], 15)


if __name__ == "__main__":
    unittest.main()

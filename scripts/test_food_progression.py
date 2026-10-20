#!/usr/bin/env python3
"""Minecraft-free regression contract for Carrot's native-food policy."""
from pathlib import Path
import tomllib
import unittest


ROOT = Path(__file__).resolve().parents[1]


class CarrotFoodProgressionTest(unittest.TestCase):
    def test_native_coverage_and_unchanged_progression(self):
        with (ROOT / "defaultconfigs/solcarrot-server.toml").open("rb") as source:
            config = tomllib.load(source)
        self.assertEqual(config["filtering"], {
            "blacklist": [],
            "whitelist": [],
            "minimumFoodValue": 1,
        })
        self.assertEqual(config["milestones"], {
            "baseHearts": 5,
            "heartsPerMilestone": 1,
            "milestones": [30, 60, 89, 119, 149, 178, 208, 238, 267, 297,
                           327, 356, 386, 416, 445],
        })
        self.assertEqual(config["miscellaneous"], {
            "resetOnDeath": False,
            "limitProgressionToSurvival": False,
        })


if __name__ == "__main__":
    unittest.main()

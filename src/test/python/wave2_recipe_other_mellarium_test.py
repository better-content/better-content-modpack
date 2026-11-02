"""Isolated source-unit parity checks against pinned ComplicatedBees recipe bytes; no loader/world."""
import json
import unittest
import zipfile
from pathlib import Path

PACK = Path(__file__).resolve().parents[3]
JAR = Path.home() / ".cache/bc/packwiz-downloads/mods/complicated_bees-1.20.1-3.4.6.jar"
ROOT = PACK / "kubejs/data/complicated_bees/recipes"
MOD_LOADED = {"type": "forge:mod_loaded", "modid": "better_manufacturing"}
MOD_ABSENT = {"type": "forge:not", "value": MOD_LOADED}


def native(name):
    with zipfile.ZipFile(JAR) as archive:
        return json.loads(archive.read(f"data/complicated_bees/recipes/{name}.json"))


def override(name):
    return json.loads((ROOT / f"{name}.json").read_text())


class MellariumConstructionSourceTest(unittest.TestCase):
    def test_hydroregulator_preserves_native_cost_and_fallback(self):
        name = "mellarium_hydroregulator"
        original = native(name)
        root = override(name)
        self.assertEqual("forge:conditional", root["type"])
        self.assertEqual(2, len(root["recipes"]))
        loaded, fallback = root["recipes"]
        self.assertEqual([MOD_LOADED], loaded["conditions"])
        self.assertEqual([MOD_ABSENT], fallback["conditions"])
        self.assertEqual(original, fallback["recipe"])
        rated = loaded["recipe"]
        self.assertEqual(original["type"], rated["type"])
        self.assertEqual(original["result"], rated["result"])
        self.assertEqual(original["category"], rated["category"])
        self.assertEqual(original["show_notification"], rated["show_notification"])
        self.assertEqual(set(original["key"]) | {"H"}, set(rated["key"]))
        for symbol, ingredient in original["key"].items():
            self.assertEqual(ingredient, rated["key"][symbol])
        self.assertEqual({"item": "tconstruct:tool_handle"}, rated["key"]["H"])
        self.assertEqual(len(original["pattern"]), len(rated["pattern"]))
        self.assertEqual([len(row) for row in original["pattern"]], [len(row) for row in rated["pattern"]])
        added = []
        for y, (old_row, new_row) in enumerate(zip(original["pattern"], rated["pattern"])):
            for x, (old, new) in enumerate(zip(old_row, new_row)):
                if old != new:
                    self.assertEqual(" ", old)
                    self.assertEqual("H", new)
                    added.append((x, y))
        self.assertEqual([(0, 0)], added)

    def test_mellarium_base_restores_native_when_manufacturing_absent(self):
        original = native("mellarium_base")
        root = override("mellarium_base")
        self.assertEqual("forge:conditional", root["type"])
        self.assertEqual(2, len(root["recipes"]))
        loaded, fallback = root["recipes"]
        self.assertEqual([MOD_LOADED], loaded["conditions"])
        self.assertEqual([MOD_ABSENT], fallback["conditions"])
        self.assertEqual(original, fallback["recipe"])
        authored = loaded["recipe"]
        self.assertEqual(["HP#", "#A#", "###"], authored["pattern"])
        self.assertEqual(original["result"], authored["result"])
        self.assertEqual(original["category"], authored["category"])
        self.assertEqual(original["show_notification"], authored["show_notification"])
        self.assertEqual(original["type"], authored["type"])
        self.assertEqual(original["key"]["#"], authored["key"]["#"])
        self.assertEqual(original["key"]["A"], authored["key"]["A"])
        self.assertEqual({"item": "tconstruct:tool_handle"}, authored["key"]["H"])
        self.assertEqual({"item": "tconstruct:large_plate"}, authored["key"]["P"])
        # This wave-one construction override costs six panels, not the native eight.
        # The present fix restores the no-Manufacturing native route, not cost parity.
        self.assertEqual(6, "".join(authored["pattern"]).count("#"))
        self.assertEqual(8, "".join(original["pattern"]).count("#"))

    def test_full_grid_temp_keeps_all_nine_paid_items_before_handle(self):
        name = "mellarium_temp_unit"
        original = native(name)
        root = override(name)
        self.assertEqual("forge:conditional", root["type"])
        self.assertEqual(2, len(root["recipes"]))
        loaded, fallback = root["recipes"]
        self.assertEqual([MOD_LOADED], loaded["conditions"])
        self.assertEqual([MOD_ABSENT], fallback["conditions"])
        self.assertEqual(original, fallback["recipe"])
        first = loaded["recipe"]
        expected_first = dict(original, result={"item": "better_manufacturing:mellarium_temp_blank"})
        self.assertEqual(expected_first, first)
        self.assertEqual(["I#I", "#A#", "I#I"], first["pattern"])
        self.assertEqual(4, "".join(first["pattern"]).count("I"))
        self.assertEqual(4, "".join(first["pattern"]).count("#"))
        self.assertEqual(1, "".join(first["pattern"]).count("A"))
        second_root = override("mellarium_temp_unit_from_blank")
        self.assertEqual("forge:conditional", second_root["type"])
        self.assertEqual(1, len(second_root["recipes"]))
        second = second_root["recipes"][0]
        self.assertEqual([MOD_LOADED], second["conditions"])
        finishing = second["recipe"]
        self.assertEqual("minecraft:crafting_shapeless", finishing["type"])
        self.assertEqual("misc", finishing["category"])
        self.assertEqual(original["result"], finishing["result"])
        self.assertCountEqual([
            {"item": "better_manufacturing:mellarium_temp_blank"},
            {"item": "tconstruct:tool_handle"},
        ], finishing["ingredients"])


if __name__ == "__main__":
    unittest.main()

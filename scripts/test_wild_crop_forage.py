#!/usr/bin/env python3
"""Minecraft-free contracts for pack-owned finite wild crop forage (not runtime acceptance)."""
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "kubejs/data"
PRODUCE = {
    "carrots": "minecraft:carrot", "potatoes": "minecraft:potato",
    "beetroots": "minecraft:beetroot", "cabbages": "farmersdelight:cabbage",
    "onions": "farmersdelight:onion", "tomatoes": "farmersdelight:tomato",
    "rice": "farmersdelight:rice",
}


def load(path):
    return json.loads((DATA / path).read_text())


def named_entries(value):
    if isinstance(value, dict):
        if value.get("type") == "minecraft:item":
            yield value["name"]
        for child in value.values():
            yield from named_entries(child)
    elif isinstance(value, list):
        for child in value:
            yield from named_entries(child)


class WildCropForageTest(unittest.TestCase):
    def test_native_paths_remain_disabled_and_one_pack_path_exists(self):
        for crop in PRODUCE:
            with self.subTest(crop=crop):
                self.assertEqual(load(f"farmersdelight/forge/biome_modifier/wild_{crop}.json"), {"type": "forge:none"})
                modifier = load(f"kubejs/forge/biome_modifier/forage/{crop}.json")
                habitat = "rice" if crop == "rice" else "land"
                self.assertEqual(modifier, {
                    "type": "forge:add_features", "biomes": f"#kubejs:forage_{habitat}",
                    "features": f"kubejs:forage/{crop}", "step": "vegetal_decoration",
                })
                placed = load(f"kubejs/worldgen/placed_feature/forage/{crop}.json")
                self.assertEqual(placed["feature"], f"kubejs:forage/{crop}")
                self.assertEqual(placed["placement"][0], {"type": "better_bumblezone_crops:overworld_forage"})
                rarity = [p for p in placed["placement"] if p["type"] == "minecraft:rarity_filter"]
                self.assertEqual(rarity, [] if crop == "rice" else [{"type": "minecraft:rarity_filter", "chance": 6}])

    def test_geometry_native_survival_and_habitats(self):
        land = load("kubejs/tags/worldgen/biome/forage_land.json")
        self.assertEqual(land["values"], ["#minecraft:is_overworld"])
        self.assertEqual(set(land["remove"]), {"#minecraft:is_ocean", "#minecraft:is_river", "#minecraft:is_beach", "minecraft:mushroom_fields"})
        rice = load("kubejs/tags/worldgen/biome/forage_rice.json")
        self.assertIn("#minecraft:is_river", rice["values"])
        self.assertIn("minecraft:swamp", rice["values"])
        for crop in PRODUCE:
            configured = load(f"kubejs/worldgen/configured_feature/forage/{crop}.json")
            config = configured["config"]
            self.assertEqual([config[k] for k in ("tries", "xz_spread", "y_spread")], [32, 7, 3])
            self.assertNotIn("floor_feature", config)
            if crop == "rice":
                self.assertEqual(configured["type"], "farmersdelight:wild_rice")
                state = config["feature"]["feature"]["config"]["to_place"]["state"]
                self.assertEqual(state["Properties"], {"half": "lower", "waterlogged": "true"})
            else:
                self.assertEqual(configured["type"], "minecraft:random_patch")
                predicates = config["feature"]["placement"][0]["predicate"]["predicates"]
                self.assertIn({"type": "minecraft:matching_blocks", "blocks": "minecraft:air"}, predicates)
                self.assertIn({"type": "minecraft:would_survive", "state": {"Name": f"farmersdelight:wild_{crop}"}}, predicates)

    def test_loot_is_food_only_without_tool_escape(self):
        for crop, produce in PRODUCE.items():
            table = load(f"farmersdelight/loot_tables/blocks/wild_{crop}.json")
            self.assertEqual(set(named_entries(table)), {produce})
            self.assertNotIn("shears_harvest", json.dumps(table))
            if crop in {"beetroots", "cabbages", "tomatoes"}:
                self.assertIn({"chance": 0.2, "condition": "minecraft:random_chance"}, table["pools"][0]["conditions"])
            if crop in {"carrots", "potatoes", "onions"}:
                functions = table["pools"][0]["entries"][0]["functions"]
                self.assertTrue(any(f["function"] == "minecraft:apply_bonus" and f["enchantment"] == "minecraft:fortune" for f in functions))

    def test_rice_retains_native_pair_checks(self):
        pools = load("farmersdelight/loot_tables/blocks/wild_rice.json")["pools"]
        self.assertEqual(len(pools), 2)
        for pool, half, offset, other in zip(pools, ["lower", "upper"], [1, -1], ["upper", "lower"]):
            conditions = pool["conditions"]
            self.assertIn({"block": "farmersdelight:wild_rice", "condition": "minecraft:block_state_property", "properties": {"half": half}}, conditions)
            pair = next(c for c in conditions if c["condition"] == "minecraft:location_check")
            self.assertEqual(pair["offsetY"], offset)
            self.assertEqual(pair["predicate"]["block"]["state"], {"half": other})

    def test_seed_recipe_gate_is_preserved(self):
        recipes = (ROOT / "kubejs/server_scripts/progression/21_better_bumblezone_crops.js").read_text()
        self.assertIn("event.remove({ output: '#better_bumblezone_crops:seeds' })", recipes)


if __name__ == "__main__":
    unittest.main()

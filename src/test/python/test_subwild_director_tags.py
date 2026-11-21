"""Contract for SubWild cave faces used by Depth Director's block sampler.

This checks pack data, not native generation or spawn rates. SubWild paints many cave
faces with vanilla blocks, so checking only subwild:* block IDs would miss the fix.
"""
import json
from pathlib import Path
import unittest

PACK = Path(__file__).resolve().parents[3]
TAGS = PACK / 'kubejs/data/better_cave_encounters/tags/blocks/geology'


class SubWildDirectorTagsTest(unittest.TestCase):
    def test_cave_face_palettes_are_mapped_once(self):
        owners = {}
        for path in sorted(TAGS.glob('*.json')):
            for block in json.loads(path.read_text())['values']:
                self.assertNotIn(block, owners, f'{block}: {owners.get(block)} and {path.stem}')
                self.assertNotIn('_ore', block, f'ore is not a cave-face rock: {block}')
                owners[block] = path.stem

        expected = {
            'ochre_tombs': ('minecraft:sand', 'minecraft:sandstone', 'minecraft:smooth_sandstone',
                            'subwild:sand_patch', 'subwild:mossy_sandstone', 'subwild:hot_sandstone'),
            'red_hunting_grounds': ('minecraft:red_sand', 'minecraft:red_sandstone',
                                   'minecraft:smooth_red_sandstone', '#forge:terracotta',
                                   'subwild:red_sand_patch', 'subwild:mossy_red_sandstone'),
            'black_stalking_caves': ('minecraft:basalt', 'minecraft:blackstone', 'minecraft:obsidian',
                                     'subwild:hot_basalt', 'subwild:molten_stone'),
            'rubble_burrows': ('minecraft:gravel', 'subwild:gravel_patch'),
            'silt_carrion': ('minecraft:dirt', 'minecraft:podzol', 'minecraft:mud',
                             'subwild:mossy_dirt', 'subwild:wet_stone'),
            'karst_nests': ('minecraft:moss_block', 'subwild:mossy_stone'),
            'ivory_ossuary': ('minecraft:diorite', 'minecraft:packed_ice', 'subwild:mossy_diorite'),
            'mineral_rifts': ('minecraft:granite', 'minecraft:andesite', 'subwild:mossy_granite'),
        }
        for habitat, blocks in expected.items():
            for block in blocks:
                self.assertEqual(owners.get(block), habitat, block)
        # Generic stone is shared by all cave types: assigning it would drown out
        # votes for Unearthed's actual rock in mixed cave faces.
        self.assertNotIn('minecraft:stone', owners)


if __name__ == '__main__':
    unittest.main()

#!/usr/bin/env python3
"""Pinned-native + inert KubeJS contracts; never launches Minecraft."""
from collections import defaultdict
from pathlib import Path
import copy
import hashlib
import json
import re
import subprocess
import tomllib
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
CACHE = Path.home() / '.cache/bc/packwiz-downloads/mods'
GROUPS = {'proteins', 'fats', 'grains', 'vegetables', 'fruits', 'dairy', 'sugars', 'alcohol'}
EXPECTED = {
    'rich': 'bigeye_tuna redscaled_tuna blue_herring deepjaw_herring ironjaw_herring garnet_mackerel black_eel geode_eel sludge_catfish crystalback_sturgeon icetooth_sturgeon sunny_sturgeon elderscale stonefish',
    'root': 'shroomfish sporefish vivid_moss lush_pike the_quarrish sage_catfish lily_snapper',
    'grain': 'rockgill peakdweller deepslatefish crystalback_boreal crystalback_trout sun_seeking_carp',
    'draught': 'twilight_koi ghostly_pike',
}
PINS = ['starcatcher', 'diet', 'fruits-delight', 'farmers-delight', 'ubes-delight', 'brewin-and-chewin', 'kubejs']


def native(pin):
    data = tomllib.loads((ROOT / f'mods/{pin}.pw.toml').read_text())
    path = CACHE / data['filename']
    if not path.is_file():
        raise AssertionError(f'Missing declared native input {path}; run ./test.main.kts dev to prepare pins')
    download = data['download']
    assert hashlib.new(download['hash-format'], path.read_bytes()).hexdigest() == download['hash'], path
    return path


def load_resources(paths):
    tags = defaultdict(list)
    recipes = {}
    def put(name, data):
        match = re.fullmatch(r'data/([^/]+)/tags/items/(.+)\.json', name)
        if match:
            key = match[1] + ':' + match[2]
            if data.get('replace'): tags[key] = []
            tags[key].extend(v['id'] if isinstance(v, dict) else v for v in data.get('values', []))
            for v in data.get('remove', []):
                value = v['id'] if isinstance(v, dict) else v
                tags[key] = [entry for entry in tags[key] if entry != value]
        match = re.fullmatch(r'data/([^/]+)/recipes/(.+)\.json', name)
        if match: recipes[match[1] + ':' + match[2]] = data
    for path in paths:
        with zipfile.ZipFile(path) as z:
            for name in z.namelist():
                if re.fullmatch(r'data/[^/]+/(tags/items|recipes)/.+\.json', name):
                    put(name, json.loads(z.read(name)))
    for path in sorted((ROOT / 'kubejs/data').rglob('*.json')):
        put('data/' + str(path.relative_to(ROOT / 'kubejs/data')), json.loads(path.read_text()))
    return dict(tags), recipes


def resolve(tags, tag, stack=()):
    assert tag not in stack, f'Tag cycle: {stack} -> {tag}'
    result = set()
    for value in tags.get(tag, []):
        result.update(resolve(tags, value[1:], stack + (tag,)) if value.startswith('#') else {value})
    return result


def inputs(recipe, tags):
    """Ingredient positions only, not result or unlock criteria."""
    result = set()
    def walk(value):
        if isinstance(value, list):
            for v in value: walk(v)
        elif isinstance(value, dict):
            if 'tag' in value: result.update(resolve(tags, value['tag']))
            if isinstance(value.get('item'), str): result.add(value['item'])
            for key, v in value.items():
                if key not in {'tag', 'item'}: walk(v)
    for field in ['ingredient', 'ingredients', 'key', 'input', 'inputs', 'pedestalItems', 'reagent', 'inputItems']:
        walk(recipe.get(field))
    return result


class FishNutritionContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.contract = json.loads((ROOT / 'kubejs/config/fish_nutrition.json').read_text())
        cls.archives = {name: native(name) for name in PINS}
        cls.jar = cls.archives['starcatcher']
        cls.javap = subprocess.check_output(['javap', '-c', '-p', '-classpath', str(cls.jar),
            'com.wdiscute.starcatcher.registry.ModItems'], text=True, timeout=30)
        verbose = subprocess.check_output(['javap', '-v', '-p', '-classpath', str(cls.jar),
            'com.wdiscute.starcatcher.registry.ModItems'], text=True, timeout=30)
        # Bind discriminators to real constructor references, not fish names.
        for discriminator, constructor in [(50, 'FishItem'), (51, 'FireResistantBasicItem')]:
            section = re.search(rf'\n  {discriminator}:.*?(?=\n  \d+:|\Z)', verbose, re.S)[0]
            assert f'/{constructor}."<init>":()V' in section, section
        bindings = {m[1]: int(m[2]) for m in re.finditer(
            r'// String ([a-z_]+)\n\s*\d+: invokedynamic[^\n]*// InvokeDynamic #(\d+):get:', cls.javap)}
        cls.edible = {'starcatcher:' + name for name, binding in bindings.items() if binding == 50}
        cls.inedible = {'starcatcher:' + name for name, binding in bindings.items() if binding == 51}
        cls.tags, cls.recipes = load_resources(list(cls.archives.values()) + sorted((ROOT / 'mods').glob('*.jar')))
        seed = {tag: sorted(resolve(cls.tags, tag)) for tag in cls.tags}
        seed.setdefault('minecraft:fishes', []).append('minecraft:cod')  # vanilla positive control
        run = subprocess.run(['node', str(ROOT / 'src/test/resources/fish-nutrition-contract.cjs'), str(ROOT)],
            input=json.dumps(seed), text=True, capture_output=True, timeout=15)
        assert run.returncode == 0, run.stdout + run.stderr
        cls.executed = json.loads(run.stdout)
        cls.merged = cls.executed['tags']
        cls.families = cls.contract['families']
        cls.special = {x for name, f in cls.families.items() if name != 'plain' for x in f['raw']}

    def test_native_roster_is_edible_and_approved_profiles_are_exact(self):
        self.assertEqual('bc.fish_nutrition.v1', self.contract['schema'])
        all_raw = [x for f in self.families.values() for x in f['raw']]
        self.assertEqual(83, len(self.edible))
        self.assertEqual(self.edible, set(all_raw))
        self.assertEqual(len(all_raw), len(set(all_raw)))
        for family, names in EXPECTED.items():
            self.assertEqual({'starcatcher:' + name for name in names.split()}, set(self.families[family]['raw']))
        self.assertEqual(29, len(self.special))
        self.assertIn('starcatcher:obsidian_eel', self.inedible)
        self.assertNotIn('starcatcher:obsidian_eel', all_raw)
        self.assertIn('starcatcher:obsidian_eel', self.contract['corrections'])
        for name, expected in [('com.wdiscute.starcatcher.items.FishItem', 'ModFoodProperties.BASIC_RAW_FISH'),
                              ('com.wdiscute.starcatcher.items.helper.FireResistantBasicItem', None)]:
            code = subprocess.check_output(['javap', '-c', '-p', '-classpath', str(self.jar), name], text=True, timeout=30)
            if expected: self.assertIn(expected, code)
            else: self.assertNotIn('FoodProperties', code)

    def test_native_food_and_diet_precedence_contracts(self):
        def bytecode(archive, name):
            return subprocess.check_output(['javap', '-c', '-p', '-classpath', str(archive), name], text=True, timeout=30)
        food = bytecode(self.jar, 'com.wdiscute.starcatcher.items.ModFoodProperties')
        cooked = food.split('Field BASIC_RAW_FISH:')[1]
        self.assertRegex(cooked, r'bipush\s+6\n[^\n]+m_38760_')
        self.assertRegex(cooked, r'fconst_2\n[^\n]+m_38758_')
        builder = bytecode(self.archives['kubejs'], 'dev.latvian.mods.kubejs.item.FoodBuilder')
        self.assertRegex(builder, r'Field saturation:F\n[^\n]+m_38758_')
        generator = bytecode(self.archives['diet'], 'com.illusivesoulworks.diet.common.util.DietValueGenerator')
        groups = generator.split('  private static java.util.Set<com.illusivesoulworks.diet.api.type.IDietGroup> findGroups(')[1].split('\n  private static ')[0]
        self.assertLess(groups.index('IDietGroup.contains:'), groups.index('Field RECIPES:'))
        branch = re.search(r'Set.isEmpty:[^\n]+\n\s*\d+: ifeq\s+(\d+)\n\s*\d+: getstatic[^\n]+Field RECIPES:', groups)
        self.assertIsNotNone(branch, 'Direct nonempty group set must skip native recipe generation')
        self.assertRegex(groups, rf'\n\s*{branch[1]}: getstatic[^\n]+Field GENERATED:')

    def test_effective_merged_groups_have_no_leaks(self):
        for name, family in self.families.items():
            expected = {'proteins'} | {'rich': {'fats'}, 'root': {'vegetables'}, 'grain': {'grains'}}.get(name, set())
            self.assertEqual(expected, set(family['groups']))
            for item in family['raw'] + [family['cooked']]:
                actual = {g for g in GROUPS if item in resolve(self.merged, 'diet:' + g)}
                self.assertEqual(expected, actual, item)
        for item in self.inedible:
            before = {g for g in GROUPS if item in resolve(self.tags, 'diet:' + g)}
            after = {g for g in GROUPS if item in resolve(self.merged, 'diet:' + g)}
            self.assertEqual(before, after, item)

    def test_window_weights_only_change_two_native_fields(self):
        boosted = set()
        with zipfile.ZipFile(self.jar) as z:
            for entry in z.namelist():
                if not entry.startswith('data/starcatcher/starcatcher/fish/') or not entry.endswith('.json'): continue
                original = json.loads(z.read(entry))
                item = original['catch_info']['item']
                temporal = (set(original['world_restrictions']['seasons']) != {'all'} or
                            original['daytime'] != 'all' or original['weather'] != 'all')
                if item not in self.edible or not temporal:
                    override = ROOT / 'kubejs' / entry
                    if override.is_file():
                        current = json.loads(override.read_text())
                        self.assertEqual(original['base_chance'], current['base_chance'], item)
                        self.assertEqual(original['bait_restrictions'], current['bait_restrictions'], item)
                    continue
                boosted.add(item)
                current = json.loads((ROOT / 'kubejs' / entry).read_text())
                expected = copy.deepcopy(original)
                expected['base_chance'] *= 4
                expected['bait_restrictions']['correct_bait_chance_added'] *= 4
                self.assertEqual(expected, current, item)
        self.assertEqual(boosted, set(self.contract['boosted']))
        self.assertEqual(46, len(boosted))
        self.assertEqual(4, self.contract['window_weight_multiplier'])

    def test_cooking_preserves_families_without_changing_native_pacing(self):
        methods = {'smelting': 200, 'smoking': 100, 'campfire_cooking': 600}
        native_names = {'smelting': 'cooked_fish', 'smoking': 'cooked_fish_from_smoking',
                        'campfire_cooking': 'cooked_fish_from_campfire_cooking'}
        with zipfile.ZipFile(self.jar) as archive:
            native_inputs = set(json.loads(archive.read('data/starcatcher/tags/items/starcaught_fishes.json'))['values'])
        self.assertEqual(self.edible - native_inputs, set(self.contract['native_cooking_exclusions']))
        self.assertEqual(native_inputs - self.edible, set(self.contract['legacy_plain_cookable']))
        all_routes = defaultdict(list)
        for name, family in self.families.items():
            for method, ticks in methods.items():
                rid = ('starcatcher:' + native_names[method] if name == 'plain' else f'kubejs:fish/{name}_{method}')
                recipe = self.recipes[rid]
                self.assertEqual('minecraft:' + method, recipe['type'])
                self.assertEqual(ticks, recipe['cookingtime'])
                self.assertEqual(.35, recipe['experience'])
                self.assertEqual({'item': family['cooked']}, recipe['result'])
                actual = inputs(recipe, self.merged)
                expected = (set(family['raw']) & native_inputs) | (set(self.contract['legacy_plain_cookable']) if name == 'plain' else set())
                self.assertEqual(expected, actual)
                all_routes[method].extend(actual)
        for method, routes in all_routes.items():
            self.assertEqual(native_inputs, set(routes), method)
            self.assertEqual(len(routes), len(set(routes)), method + ' routes must be disjoint')
        self.assertEqual(self.edible, set().union(*(set(f['raw']) for f in self.families.values())))
        self.assertEqual([{'item': 'minecraft:barrier'}], self.recipes['starcatcher:aquaculture_fillet']['ingredients'])
        for family in ['rich', 'root', 'grain', 'draught']:
            item = self.executed['items'][self.families[family]['cooked']]
            path = ROOT / 'kubejs/assets' / (item['texture'].replace(':', '/textures/', 1) + '.png')
            self.assertTrue(path.is_file(), path)
            self.assertTrue(path.read_bytes().startswith(b'\x89PNG\r\n\x1a\n'))
        self.assertNotIn('starcatcher:cooked_starcaught_fish', self.executed['items'])

    def test_shared_outputs_cannot_inherit_specialty_fish(self):
        protected = self.contract['generic_output_groups']
        def output(recipe):
            result = recipe.get('result', {})
            return result.get('item') if isinstance(result, dict) else result if isinstance(result, str) else None
        by_output = defaultdict(list)
        for recipe in self.recipes.values():
            if output(recipe): by_output[output(recipe)].append(recipe)
        def baseline_groups(item, stack=()):
            direct = {g for g in GROUPS if item in resolve(self.tags, 'diet:' + g)}
            if direct or item in stack: return direct
            return set().union(*(baseline_groups(ingredient, stack + (item,))
                for recipe in by_output[item] for ingredient in inputs(recipe, self.tags)))
        # A positive-group reference closure only, not a replacement nutrition engine.
        # Actual Diet bytecode precedence is asserted separately below.
        for item, expected in protected.items():
            self.assertEqual(baseline_groups(item), set(expected), item)
            self.assertEqual(set(expected), {g for g in GROUPS if item in resolve(self.merged, 'diet:' + g)}, item)
        with zipfile.ZipFile(self.archives['ubes-delight']) as z:
            expected_consumers = set()
            for name in z.namelist():
                if not re.fullmatch(r'data/ubesdelight/recipes/.+\.json', name): continue
                original = json.loads(z.read(name))
                if inputs(original, self.tags) & self.special:
                    rid = 'ubesdelight:' + name.removeprefix('data/ubesdelight/recipes/').removesuffix('.json')
                    expected_consumers.add(rid)
                    self.assertEqual(original, self.recipes[rid], rid)
                    self.assertIn(output(original), protected)
            self.assertEqual(expected_consumers, set(self.contract['generic_consumers']))
        cooked = {f['cooked'] for f in self.families.values()}
        for rid, recipe in self.recipes.items():
            if output(recipe) in cooked or output(recipe) in protected: continue
            if recipe.get('type') in {'minecraft:smelting', 'minecraft:smoking', 'minecraft:campfire_cooking',
                                     'minecraft:crafting_shapeless', 'farmersdelight:cooking', 'brewinandchewin:fermenting'}:
                self.assertFalse(inputs(recipe, self.merged) & self.special, rid)

    def test_booze_is_explicit_and_never_recipe_inherited(self):
        config = tomllib.loads((ROOT / 'defaultconfigs/better_survival_physiology-server.toml').read_text())
        loads = dict(entry.rsplit('=', 1) for entry in config['metabolism']['alcoholPotencies'])
        self.assertEqual(20.0, config['metabolism']['alcoholClearMinutes'])
        self.assertEqual(4.0, config['metabolism']['sugarHalfLifeMinutes'])
        beverages = {
            'kombucha': .08, 'beer': .14, 'pale_jane': .16, 'strongroot_ale': .18,
            'steel_toe_stout': .20, 'mead': .22, 'rice_wine': .22, 'egg_grog': .24,
            'bloody_mary': .24, 'salty_folly': .25, 'glittering_grenadine': .26,
            'saccharine_rum': .28, 'red_rum': .30, 'dread_nog': .32, 'vodka': .38,
            'withering_dross': .42,
        }
        self.assertEqual({'brewinandchewin:' + key: value for key, value in beverages.items()},
                         {key: float(value) for key, value in loads.items() if key.startswith('brewinandchewin:')})
        self.assertEqual(19, len(loads))
        expected = set(self.families['draught']['raw'] + [self.families['draught']['cooked']])
        fish_loads = {item for item in loads if item.startswith('starcatcher:') or item in {f['cooked'] for f in self.families.values()}}
        self.assertEqual(expected, fish_loads)
        for item in expected: self.assertEqual(.06, float(loads[item]))
        for name, family in self.families.items():
            self.assertEqual(.06 if name == 'draught' else 0, family['alcohol_load'])
        jars = list((ROOT / 'mods').glob('better-survival-physiology-*.jar'))
        self.assertEqual(1, len(jars))
        with zipfile.ZipFile(jars[0]) as archive:
            text = archive.read('better_survival_physiology.mixins.json').decode()
        self.assertNotIn('DietValueGeneratorMixin', text)
        self.assertNotIn('DietApiImplMixin', text)


if __name__ == '__main__':
    unittest.main(verbosity=2)

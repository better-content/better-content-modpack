#!/usr/bin/env python3
"""Pure pack-owned check for the two approved fixed KC rice ports.

This is not the old exhaustive Cooking food/recipe-policy compiler, a loaded
recipe check, or a replacement for native serializer/serving acceptance.
"""
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PORTS = {
    'fruitsdelight:cooking/pineapple_fried_rice': (
        [{'item': 'fruitsdelight:pineapple'}, {'tag': 'forge:grain/rice'},
         {'tag': 'forge:grain/rice'}, {'tag': 'forge:grain/rice'},
         {'tag': 'forge:salad_ingredients/cabbage'}, {'tag': 'forge:eggs'}], 0.1),
    'kubejs:food_eras/market_fried_rice': (
        [{'item': 'fruitsdelight:pineapple'}, {'tag': 'forge:grain/rice'},
         {'item': 'farmersdelight:cabbage'}, {'item': 'farmersdelight:tomato'},
         {'item': 'minecraft:egg'}, {'item': 'farmersdelight:beef_patty'}], 0.2),
}


def fingerprint(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, separators=(',', ':'), allow_nan=False).encode()).hexdigest()


def validate(policy, root=ROOT):
    if policy.get('schema') != 'better_cooking:recipe_policy.v1' or policy.get('enabled') is not True:
        raise ValueError('missing fixed-port cooking policy')
    routes, sources = policy['routes'], policy['expected_sources']
    ids = [row['id'] for row in policy['definitions']]
    if len(ids) != len(set(ids)) or set(policy['remove_ids']) & set(PORTS):
        raise ValueError('duplicate definition or removed fried-rice source')
    if set(PORTS) & set(policy.get('pending_source_ids', [])):
        raise ValueError('new ports must not retain the old queued market FD recipe')
    for recipe_id, (ingredients, experience) in PORTS.items():
        old = sources[recipe_id]
        if old != {'type': 'farmersdelight:cooking', 'container': {'item': 'minecraft:bowl'},
                   'cookingtime': 200, 'experience': experience, 'ingredients': ingredients,
                   'result': {'item': 'fruitsdelight:pineapple_fried_rice'}}:
            raise ValueError('historical rice formulation drift: ' + recipe_id)
        if policy['source_recipe_hashes'][recipe_id] != fingerprint(old):
            raise ValueError('historical rice source hash drift: ' + recipe_id)
        route = routes[recipe_id]
        definitions = route.get('definitions') or []
        if route.get('action') != 'redirect' or len(definitions) != 1 or definitions[0]['id'] != recipe_id:
            raise ValueError('not one stable-ID fixed rice redirect: ' + recipe_id)
        definition = next((row for row in policy['definitions'] if row['id'] == recipe_id), None)
        if definition is None or definition['json'] != definitions[0]['json'] or not definitions[0].get('preservation_review'):
            raise ValueError('missing or divergent rice definition: ' + recipe_id)
        expected = {'type': 'kaleidoscope_cookery:pot', 'carrier': {'item': 'minecraft:bowl'},
                    'time': 200, 'stir_fry_count': 3, 'ingredients': ingredients,
                    'result': {'item': 'fruitsdelight:pineapple_fried_rice'}}
        if definition['json'] != expected:
            raise ValueError('rice carrier/input/yield/serializer drift: ' + recipe_id)
    if ").id('kubejs:food_eras/market_fried_rice')" in (root / 'kubejs/server_scripts/progression/food_eras.js').read_text():
        raise ValueError('market FD recipe still installed by food_eras')
    if 'ServerEvents.recipes' in (root / 'kubejs/server_scripts/progression/83_culinary_policy.js').read_text():
        raise ValueError('retired legacy policy is still a live recipe installer')
    # Existing namespace is deliberately preserved. Every retained chopping
    # model ID must resolve to its four pre-existing display stages in the pack.
    for row in policy['definitions']:
        recipe = row['json']
        if recipe.get('type') != 'kaleidoscope_cookery:chopping_board':
            continue
        model = recipe.get('model_id', '')
        if not model.startswith('better_cooking:'):
            raise ValueError('chopping model ID changed: ' + row['id'])
        stages = root / 'kubejs/assets/better_cooking/models/chopping_board' / model.split(':', 1)[1]
        if any(not (stages / f'{step}.json').is_file() for step in range(4)):
            raise ValueError('missing preserved chopping display stages: ' + model)
    return len(PORTS)


if __name__ == '__main__':
    print('verified', validate(json.loads((ROOT / 'kubejs/config/cooking_recipes.json').read_text())),
          'fixed rice ports and existing chopping models (source only)')

#!/usr/bin/env python3
"""Terrain-only native source/expected-value contract. No Minecraft or runtime verdict.

Inputs are the versioned terrain catalogue, pack recipes/policies and hash-verified
Packwiz archives. An empty cache rehydrates declared archives with the existing
preparation helper. Never reads a runtime dump or a retained audit as an oracle.
"""
import collections
import copy
from fractions import Fraction
import hashlib
import json
from pathlib import Path
import re
import sys
import tomllib
import zipfile

from prepare_dev_inputs import ensure_pin

ROOT = Path(__file__).resolve().parents[1]
ID = re.compile(r'^[a-z0-9_.-]+:[a-z0-9_./-]+$')
# Discovery guard only, NEVER a production ingredient classifier. All matches must
# have an authored exact decision, and native base-stone/dirt tags are also checked.
TERMS = re.compile(r'(dirt|soil|silt|loam|clay|sand|chalk|kaolin|travertine|limestone|shale|stone|slate|gabbro|granodiorite|rhyolite|granite|quartzite|dolomite|dacite|kimberlite|regolith|conglomerate|mud|chert|tuff|scoria|scorchia|jasper|permafrost|muck|coprolith|guanostone|radrock|galena|obsidian|phyllite|basalt)')
SHAPED = re.compile(r'(slab|stairs|wall|brick|pillar|polished|chisel|_ore|button|plate|tile|fence|gate|window|layered|smooth|lamp|sconce|sign|chest|machine|furnace|trap|spawner|pressure|detector|charger|casing|rail|pedestal|altar|soul|end_stone|netherrack|blackstone)')


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def resources(catalogue):
    """Load declared terrain owners plus all already-pinned native scope archives.

    All installed archives are declared by survey_pins. This is intentional: a mod
    addition/pin change must force re-review, not silently inherit a guessed assay.
    """
    models, blocks, recipes, tags, compounds = {}, {}, {}, collections.defaultdict(set), {}
    actual = {}
    for pin_path in sorted((ROOT / 'mods').glob('*.pw.toml')):
        pin = tomllib.loads(pin_path.read_text())
        actual[str(pin_path.relative_to(ROOT))] = pin['download']['hash']
    require(actual == catalogue['survey_pins'], 'installed pin roster changed: review terrain coverage explicitly')
    for rel in actual:
        pin = tomllib.loads((ROOT / rel).read_text())
        path = ensure_pin(pin, Path.home() / '.cache/bc/packwiz-downloads/mods')
        with zipfile.ZipFile(path) as archive:
            for name in archive.namelist():
                if not name.endswith('.json'):
                    continue
                if not any(part in name for part in ['/models/item/', '/blockstates/', '/recipes/', '/tags/blocks/', '/tags/items/']) and name != 'data/chemlib/compounds.json':
                    continue
                try:
                    value = json.loads(archive.read(name))
                except (ValueError, UnicodeDecodeError):
                    continue
                source = pin['filename'] + '!' + name
                parts = name.split('/')
                if name.startswith('assets/') and '/models/item/' in name:
                    models[parts[1] + ':' + name.split('/models/item/')[1][:-5]] = source
                if name.startswith('assets/') and '/blockstates/' in name:
                    blocks[parts[1] + ':' + name.split('/blockstates/')[1][:-5]] = source
                if name.startswith('data/') and '/recipes/' in name and '/advancements/' not in name:
                    recipes[parts[1] + ':' + name.split('/recipes/')[1][:-5]] = (value, source)
                if name.startswith('data/') and any(t in name for t in ['/tags/blocks/', '/tags/items/']):
                    domain = 'blocks' if '/tags/blocks/' in name else 'items'
                    tag = parts[1] + ':' + name.split('/tags/' + domain + '/')[1][:-5]
                    tags[tag].update(x if isinstance(x, str) else x['id'] for x in value.get('values', []))
                if name == 'data/chemlib/compounds.json':
                    compounds = { 'chemlib:' + c['name']: c for c in value['compounds'] }
    # Authored pack JSON wins over native recipe resources.
    for path in sorted((ROOT / 'kubejs/data').glob('*/recipes/**/*.json')):
        rel = path.relative_to(ROOT / 'kubejs/data')
        recipes[rel.parts[0] + ':' + '/'.join(rel.parts[2:])[:-5]] = (json.loads(path.read_text()), str(path.relative_to(ROOT)))
    return models, blocks, recipes, tags, compounds


def expand_tag(tag, tags, visiting=()):
    if tag in visiting:
        return set()
    result = set()
    for value in tags.get(tag, set()):
        result.update(expand_tag(value[1:], tags, visiting + (tag,)) if value.startswith('#') else [value])
    return result


def expected(profile):
    return {o['item']: Fraction(str(o['chance'])) * o['count'] for o in profile['physical'] + profile['chemical']}


def certificate(transitions, fixed):
    """Strict positive potential certificate for the bounded stoichiometric graph.

    An ingredient slot is an OR of exact alternatives: its cheapest potential is
    used. Therefore one inequality covers ALL combinations, not one sampled path.
    Every retained transition must be non-increasing. Physical recovery loss is
    checked separately from this potential, which is not mass. Nonnegative sums
    of these inequalities rule out positive expected
    amplification cycles, including repeated recovery of intermediate outputs.
    """
    nodes = set(fixed)
    for transition in transitions:
        for choices in transition['inputs']:
            nodes.update(choices)
        nodes.update(transition['outputs'])
    # Construct a conservative integer-valued witness by raising ingredient
    # potentials. Rounding upward prevents asymptotic rational relaxation on
    # equality cycles; the final inequalities are still checked EXACTLY below.
    # Initial values are lower bounds, not frozen commodity prices or masses.
    weights = {node: Fraction(1) for node in nodes}
    weights.update(fixed)
    for _ in range(256):
        changed = False
        for t in transitions:
            cost = sum(min(weights[n] for n in slot) for slot in t['inputs'])
            value = sum(weights[n] * count for n, count in t['outputs'].items())
            if value <= cost:
                continue
            target = value / len(t['inputs'])
            ceiling = (target.numerator + target.denominator - 1) // target.denominator
            require(ceiling < 10**9, 'no bounded positive potential: ' + t['id'])
            for choices in t['inputs']:
                for node in choices:
                    weights[node] = max(weights[node], Fraction(ceiling))
            changed = True
        if not changed:
            break
    else:
        raise AssertionError('conversion certificate did not converge')
    for t in transitions:
        cost = sum(min(weights[n] for n in slot) for slot in t['inputs'])
        value = sum(weights[n] * count for n, count in t['outputs'].items())
        require(value <= cost, 'amplification: ' + t['id'])
    return weights


def conversion_model(c, recipes, tags):
    entries = {e['input']: e for e in c['entries']}
    removed = {e['id'] for e in c['remove_recipes']}
    active_mods = set()  # resolved from pinned mods.toml below
    for rel in c['survey_pins']:
        pin = tomllib.loads((ROOT / rel).read_text())
        with zipfile.ZipFile(Path.home() / '.cache/bc/packwiz-downloads/mods' / pin['filename']) as archive:
            if 'META-INF/mods.toml' in archive.namelist():
                metadata = tomllib.loads(archive.read('META-INF/mods.toml').decode())
                active_mods.update(m['modId'] for m in metadata.get('mods', []))
    active_mods.update(['minecraft', 'forge'])

    def enabled(condition):
        if isinstance(condition, list):
            return all(enabled(v) for v in condition)
        kind = condition.get('type')
        if kind == 'forge:mod_loaded':
            return condition['modid'] in active_mods
        if kind == 'forge:not':
            return not enabled(condition['value'])
        if kind in ['forge:and', 'forge:or']:
            return (all if kind == 'forge:and' else any)(enabled(v) for v in condition['values'])
        if kind == 'forge:tag_empty':
            return not expand_tag(condition['tag'], tags)
        # Unknown native configuration flags are overapproximated as enabled;
        # no source recipe is waived by guessing its configured absence.
        return True
    # Native vanilla input tags and actual pack-authored compatibility additions.
    tags = copy.deepcopy(tags)
    tags['minecraft:sand'].update(['minecraft:sand', 'minecraft:red_sand'])
    tags['forge:sand'].update(['minecraft:sand', 'minecraft:red_sand'])
    tags['forge:sandstone'].update(['minecraft:sandstone', 'minecraft:red_sandstone'])
    tags['forge:stone'].update(['minecraft:stone', '#minecraft:base_stone_overworld'])
    tags['forge:cobblestone'].update(['minecraft:cobblestone'])
    for script, variables in [('check__30_stone_cobble_compat.js', ['stoneLikeEntries', 'cobbleLikeEntries']), ('check__40_dirt_grass_compat.js', ['dirtLikeEntries', 'grassLikeEntries'])]:
        text = (ROOT / 'kubejs/server_scripts/compat/retained' / script).read_text()
        for variable in variables:
            match = re.search(r'var ' + variable + r'\s*=\s*\[([\s\S]*?)\]', text)
            require(match is not None, 'compatibility array not found: ' + variable)
            values = re.findall(r"'([^']+)'", match[1])
            target = {'stoneLikeEntries': 'forge:stone', 'cobbleLikeEntries': 'forge:cobblestone', 'dirtLikeEntries': 'kubejs:dirt_like', 'grassLikeEntries': 'kubejs:grass_like'}[variable]
            tags[target].update(values)
    rock_policy = (ROOT / 'kubejs/server_scripts/compat/retained/check__75_stone_cobble_tag_compat.js').read_text()
    dirt_policy = (ROOT / 'kubejs/server_scripts/compat/retained/refactor__compatability__85_dirt_grass_tag_compat.js').read_text()
    protected_rock = set(re.findall(r"'([a-z0-9_]+:[a-z0-9_./-]+)'", rock_policy.split('ServerEvents.recipes')[0]))
    protected_dirt = set(re.findall(r"'([a-z0-9_]+:[a-z0-9_./-]+)'", dirt_policy.split('ServerEvents.recipes')[0]))

    def slot(v, rid):
        if isinstance(v, list):
            return set().union(*(slot(x, rid) for x in v))
        if not isinstance(v, dict):
            raise AssertionError('unsupported relevant ingredient ' + rid)
        if 'item' in v:
            item = v['item']
            tag = None
            if item == 'minecraft:dirt' and rid not in protected_dirt:
                tag = 'kubejs:dirt_like'
            if item == 'minecraft:grass_block':
                tag = 'kubejs:grass_like'
            if item in ['minecraft:stone', 'minecraft:cobblestone'] and rid not in protected_rock:
                tag = 'forge:' + item.split(':')[1]
            return expand_tag(tag, tags) if tag else {item}
        if 'tag' in v:
            # Unknown external tag is a positive external reagent, not a free input.
            return expand_tag(v['tag'], tags) or {'#' + v['tag']}
        raise AssertionError('unsupported relevant ingredient ' + rid)

    transitions = []
    # Bound scope to recipes creating a recoverable terrain input or one of the
    # physical products; add relevant native aggregate alternatives explicitly.
    relevant = set(entries) | set(c['physical_units']) | {'minecraft:coarse_dirt', 'minecraft:clay'}
    # Backward closure includes compression/decompression and manufactured
    # intermediates that can return to terrain; they gain no recovery recipe.
    for _ in range(32):
        before = len(relevant)
        for rid, (d, source) in recipes.items():
            if rid in removed or not enabled(d.get('conditions', [])) or d.get('type') not in ['minecraft:crafting_shaped', 'minecraft:crafting_shapeless', 'minecraft:stonecutting', 'minecraft:smelting', 'minecraft:blasting']:
                continue
            result = d.get('result')
            out = result if isinstance(result, str) else result.get('item') if isinstance(result, dict) else None
            if out not in relevant:
                continue
            raw = list(d.get('key', {}).values()) if 'key' in d else d.get('ingredients', [d.get('ingredient')])
            for v in raw:
                relevant.update(slot(v, rid))
        if len(relevant) == before:
            break
    else:
        raise AssertionError('conversion dependency closure exceeded its bound')
    for rid, (d, source) in recipes.items():
        if rid in removed or not enabled(d.get('conditions', [])):
            continue
        kind = d.get('type')
        result = d.get('result')
        if kind not in ['minecraft:crafting_shaped', 'minecraft:crafting_shapeless', 'minecraft:stonecutting', 'minecraft:smelting', 'minecraft:blasting', 'create:milling', 'create:crushing']:
            continue
        if kind in ['create:milling', 'create:crushing']:
            raw = d.get('ingredients', [])
            if len(raw) != 1 or not isinstance(raw[0], dict) or raw[0].get('item') not in relevant:
                continue
            # Keep only ordinary aggregate alternatives, not ore/metal resources.
            if any(o.get('item') not in relevant and o.get('item') != 'minecraft:flint' for o in d.get('results', [])):
                continue
            outs = collections.defaultdict(Fraction)
            for o in d.get('results', []):
                outs[o['item']] += Fraction(str(o.get('chance', 1))) * o.get('count', 1)
            inputs = [slot(raw[0], rid)]
        else:
            item = result if isinstance(result, str) else result.get('item') if isinstance(result, dict) else None
            if item not in relevant:
                continue
            outs = {item: Fraction(result.get('count', 1) if isinstance(result, dict) else 1)}
            if kind == 'minecraft:crafting_shaped':
                inputs = [slot(d['key'][symbol], rid) for row in d['pattern'] for symbol in row if symbol != ' ']
            elif kind == 'minecraft:crafting_shapeless':
                inputs = [slot(v, rid) for v in d['ingredients']]
            else:
                inputs = [slot(d['ingredient'], rid)]
        transitions.append({'id': rid, 'inputs': inputs, 'outputs': outs, 'source': source})
    # Vanilla definitions absent from mod JARs: authored exact 1.20.1 recipes.
    transitions.extend([
        {'id': 'minecraft:coarse_dirt', 'inputs': [{'minecraft:dirt'}]*2 + [{'minecraft:gravel'}]*2, 'outputs': {'minecraft:coarse_dirt': Fraction(4)}},
        {'id': 'minecraft:diorite', 'inputs': [{'minecraft:cobblestone'}]*2 + [{'minecraft:quartz'}]*2, 'outputs': {'minecraft:diorite': Fraction(2)}},
        {'id': 'minecraft:granite', 'inputs': [{'minecraft:diorite'}, {'minecraft:quartz'}], 'outputs': {'minecraft:granite': Fraction(1)}},
        {'id': 'minecraft:andesite', 'inputs': [{'minecraft:diorite'}, {'minecraft:cobblestone'}], 'outputs': {'minecraft:andesite': Fraction(2)}},
        {'id': 'minecraft:clay', 'inputs': [{'minecraft:clay_ball'}]*4, 'outputs': {'minecraft:clay': Fraction(1)}}
    ])
    # Physical counterpart/hoe/smelting paths are conservative 1:1 transfers.
    for e in entries.values():
        target = e['counterpart_of']
        if target:
            transitions.append({'id': 'substrate/' + e['input'], 'inputs': [{e['input']}], 'outputs': {target: Fraction(1)}})
        transitions.append({'id': 'terrain/' + e['input'], 'inputs': [{e['input']}], 'outputs': expected(c['profiles'][e['profile']]), 'recovery': True})
    for i in ['minecraft:coarse_dirt', 'minecraft:rooted_dirt', 'minecraft:dirt_path', 'minecraft:grass_block', 'minecraft:podzol', 'minecraft:mycelium']:
        transitions.append({'id': 'soil/' + i, 'inputs': [{i}], 'outputs': {'minecraft:dirt': Fraction(1)}})
    # A conversion potential is not physical mass or a commodity price. Start
    # clay balls at a quarter block, then construct a consistent positive witness
    # for every product (including compressed blocks and chemical packets).
    fixed = {'minecraft:clay_ball': Fraction(1, 4)}
    certificate(transitions, fixed)
    # Negative test: a two-step expected amplification through an intermediate.
    for bad in [
        [{'id':'bad recovery', 'inputs':[{'minecraft:dirt'}], 'outputs':{'minecraft:dirt':Fraction(2)}, 'recovery':True}],
        [{'id':'make intermediate','inputs':[{'minecraft:dirt'}], 'outputs':{'test:intermediate':Fraction(2)}}, {'id':'recover intermediate','inputs':[{'test:intermediate'}], 'outputs':{'minecraft:dirt':Fraction(1)}}]
    ]:
        try:
            certificate(bad, fixed)
        except AssertionError:
            pass
        else:
            raise AssertionError('amplification negative control passed')
    return len(transitions)


def main():
    c = json.loads((ROOT / 'kubejs/config/terrain_recovery.json').read_text())
    models, blocks, recipes, tags, compounds = resources(c)
    entries = {e['input']: e for e in c['entries']}
    excluded = {e['input']: e['reason'] for e in c['exclusions']}
    decisions = set(entries) | set(excluded)
    require(len(entries) == len(c['entries']) and not set(entries) & set(excluded), 'duplicate catalogue decisions')
    for item, e in entries.items():
        require(ID.fullmatch(item), 'invalid input ' + item)
        if not item.startswith('minecraft:'):
            require(item in models and e['source'] in [models[item], blocks.get(item)], 'missing native item/source ' + item)
    for item in blocks:
        name = item.split(':')[1]
        if TERMS.search(name) and not SHAPED.search(name):
            require(item in decisions, 'unreviewed terrain candidate ' + item)
    for tag in ['minecraft:base_stone_overworld', 'minecraft:dirt', 'unearthed:igneous', 'unearthed:metamorphic', 'unearthed:sedimentary']:
        for item in expand_tag(tag, tags):
            require(item.startswith('minecraft:') or item in decisions, 'unreviewed native soil/stone tag member ' + item)
    require(set(compounds) <= set(c['chemistry_audit']), 'unreviewed installed compound')
    consumer_text = (ROOT / 'kubejs/server_scripts/progression/45_acid_chemistry.js').read_text()
    for p in c['profiles'].values():
        for o in p['physical'] + p['chemical']:
            require(o['item'].startswith('minecraft:') or o['item'] in models, 'missing output ' + o['item'])
        for o in p['chemical']:
            require(o['item'] in consumer_text or o['item'] == 'bloodmagic:saltpeter' and 'forge:dusts/saltpeter' in consumer_text and o['item'] in expand_tag('forge:dusts/saltpeter', tags), 'unreachable chemistry consumer ' + o['item'])
            require(o['consumer'].split('#')[1] in consumer_text, 'missing consumer recipe anchor')
    # Exact replacement inputs and pinned tag recycling definitions cannot drift silently.
    for r in c['remove_recipes']:
        if r['id'].startswith('kubejs:'):
            original = r['source'].split('#')[1]
        else:
            original = r['id']
        require(original in recipes, 'missing replaced native recipe ' + original)
        d = recipes[original][0]
        require(d['ingredients'] == [{'item': r['input']}], 'changed replaced recipe ingredient ' + original)
    for spec in c['recycling_exclusions']:
        require(recipes[spec['id']][0]['ingredients'] == [{'tag': spec['tag']}], 'changed recycling tag')
    count = conversion_model(c, recipes, tags)
    print(json.dumps({'inputs':len(entries), 'profiles':len(c['profiles']), 'reviewed_exclusions':len(excluded), 'conversion_transitions':count, 'compound_decisions':len(compounds), 'native_scope':'hash-pinned source resources; no Minecraft acceptance'}))


if __name__ == '__main__':
    main()

#!/usr/bin/env python3
"""Executable reviewed inventory and bounded authored-pool statistics.

This is a source contract, not a second implementation of Minecraft loot or a
native acceptance claim. The existing Node contract exercises production filters.
"""
import collections
import copy
import hashlib
import gzip
import json
import struct
import tomllib
import zipfile
from pathlib import Path
import random
import sys

ROOT = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / 'scripts'))
from structure_loot_inputs import NbtReader, effective_injections, fingerprint, load, load_table_baseline, strings
from structure_loot_unused import CONTEXTS as UNUSED_CONTEXTS, compute as unused_source_evidence


# Exact path-only repairs, not aliases or reward reconstruction. Immutable pinned
# originals are independently read from their owning archives on every test run.
REFERENCE_REPAIRS = [
    ('fallout-wastelands', 'fallout_wastelands_', 'factory_ruin_01_01way_v0', 'fallout_wastelands_:blocks/industrialtier_1', 'fallout_wastelands_:chests/industrialtier_1'),
    ('fallout-wastelands', 'fallout_wastelands_', 'factory_ruin_02_01way', 'fallout_wastelands_:blocks/industrialtier_1', 'fallout_wastelands_:chests/industrialtier_1'),
    ('fallout-wastelands', 'fallout_wastelands_', 'military_train_01', 'fallout_wastelands_:chests/scientifictier_1', 'fallout_wastelands_:chests/sciencetier_1'),
    ('fallout-wastelands', 'fallout_wastelands_', 'ruined_building_05', 'fallout_wastelands_:chests/scientifictier_1', 'fallout_wastelands_:chests/sciencetier_1'),
    ('cataclysm', 'cataclysm', 'desert_occupied_village', 'minecraft:chests/village_desert_house', 'minecraft:chests/village/village_desert_house'),
    ('fallout-wastelands', 'fallout_wastelands_', 'factory_ruin_01_01way_v0', 'fallout_wastelands_:blocks/medicaltier_1', 'fallout_wastelands_:chests/hospitaltier_1'),
    ('towns-and-towers', 'kaisyn', 'village/exclusives/iberian/professions_misc/iberian_leatherworker_1', 'minecraft:chests/village/village_leatherworker', 'minecraft:chests/village/village_tannery'),
]


def loot_tag(value):
    encoded = value.encode('utf-8')
    return b'\x08\x00\x09LootTable' + struct.pack('>H', len(encoded)) + encoded


def validate_reference_repairs(contract):
    observations = []
    for mod, namespace, template, before, after in REFERENCE_REPAIRS:
        pin = tomllib.loads((ROOT / f'mods/{mod}.pw.toml').read_text())
        archive = Path.home() / '.cache/bc/packwiz-downloads/mods' / pin['filename']
        resource = f'data/{namespace}/structures/{template}.nbt'
        with zipfile.ZipFile(archive) as jar:
            original = jar.read(resource)
        raw = gzip.decompress(original)
        nbt = NbtReader(raw).root()
        occurrences = sum(block.get('nbt', {}).get('LootTable') == before for block in nbt['blocks'])
        assert occurrences > 0 and raw.count(loot_tag(before)) == occurrences
        expected = raw
        for owner, ns, name, old, new in REFERENCE_REPAIRS:
            if (owner, ns, name) == (mod, namespace, template):
                expected = expected.replace(loot_tag(old), loot_tag(new))
        authored = ROOT / 'kubejs' / resource
        actual = gzip.decompress(authored.read_bytes())
        def require_exact(candidate):
            assert candidate == expected, ('template changed beyond exact LootTable reference repair', resource)
        require_exact(actual)
        # Independently reject both a reverted reference and unrelated NBT bytes;
        # the oracle must not accept a matching review digest as a waiver.
        for mutation in (raw, actual + b'\x00'):
            try:
                require_exact(mutation)
            except AssertionError:
                pass
            else:
                raise AssertionError('negative template mutation admitted: ' + resource)
        repaired = NbtReader(actual).root()
        assert sum(block.get('nbt', {}).get('LootTable') == after for block in repaired['blocks']) >= occurrences
        observations.append(dict(resource=resource, before=before, after=after, occurrences=occurrences,
                                 pinned_original_sha256=hashlib.sha256(original).hexdigest(),
                                 authored_sha256=hashlib.sha256(authored.read_bytes()).hexdigest()))
    assert observations == contract['reference_repairs'], 'template repair review drift'
    return observations


MISSING_IDS = {
    'fallout_wastelands_:chests/raider', 'fallout_wastelands_:chests/raidertier_1',
    'fallout_wastelands_:chests/scraptier_1', 'kaisyn:village/village_birch_forest_house',
    'kaisyn:village/village_forest_ruins/forest_ruins_butcher',
    'kaisyn:village/village_forest_ruins/forest_ruins_library',
    'kaisyn:village/village_forest_ruins/forest_ruins_temple',
    'wythers:chests/village/forest_ruins_big_house',
}
DESIGN_PATH = ROOT / 'src/test/resources/structure-loot-authored-missing.json'
DESIGN_SPEC = json.loads(DESIGN_PATH.read_text())
assert DESIGN_SPEC['schema'] == 'bc.structure_loot_authored_missing.v1'
DESIGNS = {row['id']: row for row in DESIGN_SPEC['tables']}
assert set(DESIGNS) == MISSING_IDS and len(DESIGN_SPEC['tables']) == len(MISSING_IDS)


def authored_table(design):
    entries = []
    for item in design['entries']:
        entry = {'type': 'minecraft:item', 'name': item['item'], 'weight': item['weight']}
        count = item['count']
        if count != 1:
            value = {'type': 'minecraft:uniform', 'min': count[0], 'max': count[1]} if isinstance(count, list) else count
            entry['functions'] = [{'function': 'minecraft:set_count', 'count': value}]
        entries.append(entry)
    lo, hi = design['rolls']
    return {'type': 'minecraft:chest', 'pools': [{'rolls': {'type': 'minecraft:uniform', 'min': lo, 'max': hi}, 'entries': entries}]}


def validate_authored_items():
    import re
    explicit = set(re.findall(r"'([a-z0-9_.-]+:[a-z0-9_./-]+)'", (ROOT / 'kubejs/server_scripts/policy/global_loot_progression.js').read_text()))
    quarantine = set(json.loads((ROOT / 'kubejs/config/quarantined_items.json').read_text())['items'])
    policy = json.loads((ROOT / 'kubejs/config/crafting_policy.json').read_text())
    pin = tomllib.loads((ROOT / 'mods/fallout-wastelands.pw.toml').read_text())
    with zipfile.ZipFile(Path.home() / '.cache/bc/packwiz-downloads/mods' / pin['filename']) as jar:
        registry = jar.read('net/mcreator/falloutwastelands/init/FalloutWastelandsModItems.class')
    assert b'DeferredRegister' in registry and b'RegistryObject' in registry
    with zipfile.ZipFile(Path.home() / '.cache/bc/packwiz-downloads/vanilla/minecraft-client-1.20.1.jar') as vanilla:
        models = set(vanilla.namelist())
    for design in DESIGNS.values():
        assert design['role'] in {'supplies', 'encounter'} and 1 <= design['rolls'][0] <= design['rolls'][1] <= 4
        assert design['room_evidence'] and design['special_reward_status'] and design['template_consumers']
        for entry in design['entries']:
            item = entry['item']; namespace, path = item.split(':')
            assert item not in explicit | quarantine and not path.startswith('raw_') and not path.endswith('_seeds'), ('new design violates acquisition policy', item)
            assert path not in {'potion', 'splash_potion', 'lingering_potion', 'tipped_arrow'}
            assert isinstance(entry['weight'], int) and entry['weight'] > 0
            count = entry['count']
            assert count == 1 or (len(count) == 2 and 1 <= count[0] <= count[1] <= 10)
            for family in policy['families']:
                if family['disposition'] not in {'cut', 'authority', 'canonical_duplicate'}: continue
                for selector in family.get('selectors', []):
                    if selector.get('tag'): continue  # same deliberate omission as production loot policy
                    if selector.get('exact_id'):
                        matches = item == selector['exact_id']
                    else:
                        matches = bool(selector.get('namespace') or selector.get('id_prefix')) and (not selector.get('namespace') or namespace == selector['namespace']) and (not selector.get('id_prefix') or path.startswith(selector['id_prefix']))
                    assert not matches, ('new design matches cut family', item, family)
            if namespace == 'fallout_wastelands_':
                assert path.encode() in registry, ('missing pinned item registration literal', item)
            else:
                assert namespace == 'minecraft' and f'assets/minecraft/models/item/{path}.json' in models


def at(obj, path):
    for part in path:
        obj = obj[part]
    return obj


def edited(base, edits):
    result = copy.deepcopy(base)
    for edit in edits:
        path = edit['path']
        target = at(result, path[:-1])
        before = target.get(path[-1], []) if isinstance(target, dict) else target[path[-1]]
        assert before == edit['before'], ('upstream changed at reviewed edit', path)
        target[path[-1]] = edit['after']
    return result


def validate(tables, candidates, contract, native):
    rows = {row['id']: row for row in contract['tables']}
    assert len(rows) == len(contract['tables']), 'duplicate review'
    assert rows.keys() == candidates | {'quark:entities/stoneling_carry', 'minecraft:empty'}, 'unreviewed candidate or stale review'
    for key, row in rows.items():
        assert row['rationale'] and row['decision'] in {'tune', 'unchanged', 'author-missing'}
        assert row['kind'] in {'physical', 'mob', 'package', 'non-container', 'helper-or-injection', 'intentional-empty-or-cut', 'unknown-consumer', 'unresolved'}
        assert row['role'] in ({'supplies', 'encounter', 'treasure'} if row['kind'] == 'physical' else {None})
        assert fingerprint(tables.get(key)) == row['table_sha256'], ('review drift', key)
        if row['decision'] == 'author-missing':
            assert key in DESIGNS and key not in native, ('new definition was not missing upstream', key)
            assert row['kind'] == 'physical' and row['role'] == DESIGNS[key]['role']
            assert row['new_design_disclosure'] == DESIGNS[key]['special_reward_status']
            assert row['authored_items'] == sorted(entry['item'] for entry in DESIGNS[key]['entries'])
            assert tables[key] == authored_table(DESIGNS[key]), ('new authored reward/state changed beyond accepted design', key)
            assert row['template_consumers'] == DESIGNS[key]['template_consumers'] and row['template_consumers']
            assert 'edits' not in row
        elif row['decision'] == 'tune':
            assert row['kind'] == 'physical', ('non-container tuning', key)
            assert edited(native[key], row['edits']) == tables[key], ('unreviewed modification', key)
            # Only bounded count/roll edits: no item substitution, added supplies,
            # changed weights/conditions/NBT/enchantments or reward reconstruction.
            for edit in row['edits']:
                path = edit['path']
                assert path[0] == 'pools' and isinstance(path[1], int)
                assert path[-1] in {'rolls', 'count', 'functions'}
                if path[-1] == 'functions':
                    assert edit['before'] == []
                    assert len(edit['after']) == 1
                    assert edit['after'][0]['function'] == 'minecraft:set_count'
        else:
            assert 'edits' not in row
    assert {key for key, row in rows.items() if row['decision'] == 'author-missing'} == MISSING_IDS
    assert {key for key, row in rows.items() if row['decision'] in {'tune', 'author-missing'}} == set(contract['native_probe']['tables']), 'tuning escaped reviewed physical allowlist'
    assert rows['quark:entities/stoneling_carry']['kind'] == 'mob'
    assert rows['wares:gameplay/empty_package']['kind'] == 'package'
    assert rows['the_bumblezone:structures/sempiternal_sanctum/sudoku_failed']['kind'] == 'intentional-empty-or-cut'
    return rows


def number_bounds(value):
    if isinstance(value, (int, float)):
        return int(value), int(value)
    assert value.get('type', 'minecraft:uniform') == 'minecraft:uniform', value
    return int(value['min']), int(value['max'])


def draw(rng, value):
    lo, hi = number_bounds(value)
    return rng.randint(lo, hi)


def quantity(entry):
    counts = [f for f in entry.get('functions', []) if f.get('function', '').removeprefix('minecraft:') == 'set_count']
    assert len(counts) <= 1
    return counts[0]['count'] if counts else 1


def sample_changed_pools(rows, tables, contract):
    spec = contract['samples']
    size = spec['size']
    assert size >= 20000
    observations = []
    for key, row in rows.items():
        if row['decision'] not in {'tune', 'author-missing'}:
            continue
        pool_ids = list(range(len(tables[key]['pools']))) if row['decision'] == 'author-missing' else sorted({edit['path'][1] for edit in row['edits']})
        for pool_id in pool_ids:
            pool = tables[key]['pools'][pool_id]
            assert not pool.get('conditions') and not pool.get('functions')
            entries = pool['entries']
            assert all(not e.get('conditions') and not e.get('children') for e in entries)
            weights = [int(e.get('weight', 1)) for e in entries]
            assert min(weights) > 0
            rng = random.Random(spec['seed'] + int(hashlib.sha256(f'{key}/{pool_id}'.encode()).hexdigest()[:8], 16))
            total_rolls = 0
            selections = collections.Counter()
            quantities = collections.Counter()
            for _ in range(size):
                rolls = draw(rng, pool['rolls'])
                total_rolls += rolls
                for _ in range(rolls):
                    index = rng.choices(range(len(entries)), weights=weights)[0]
                    selections[index] += 1
                    quantities[index] += draw(rng, quantity(entries[index]))
            expected_rolls = sum(number_bounds(pool['rolls'])) / 2
            assert abs(total_rolls / size - expected_rolls) <= expected_rolls * spec['relative_mean_tolerance']
            for index, entry in enumerate(entries):
                probability = weights[index] / sum(weights)
                assert abs(selections[index] / total_rolls - probability) <= spec['absolute_probability_tolerance']
                # A nested entry is a selected helper token, NOT simulated final loot.
                expected_count = sum(number_bounds(quantity(entry))) / 2
                if selections[index] >= 100:
                    observed_count = quantities[index] / selections[index]
                    assert abs(observed_count - expected_count) <= max(0.15, expected_count * spec['relative_mean_tolerance'])
            observations.append({'table': key, 'pool': pool_id, 'samples': size, 'mean_rolls': round(total_rolls / size, 4), 'expected_mean_rolls': expected_rolls})
    return observations


POSSIBLE_IDENTITIES = {
    'creatingspace:abandoned_moon_base_loot': 'blueprint and nickel reward; physical consumer unknown',
    'fallout_wastelands_:chests/enclavelootable': 'Enclave equipment reward; physical consumer unknown',
    'fallout_wastelands_:chests/raidertier_2': 'distinct higher-tier equipment reward; physical consumer unknown',
    'goety:chests/shack_book': 'valuable book/paper/enchantment reward; physical consumer unknown',
    'supplementaries:inject/test': 'helper/debug exploration-map definition; physical consumer unknown',
    'the_bumblezone:structures/gazebuzz_cluster/potions_reward': 'puzzle diamond reward, not potion loot; physical consumer unknown',
    'the_bumblezone:structures/gazebuzz_cluster/targets_reward': 'puzzle diamond/emerald reward; physical consumer unknown',
}


def possible_pool_budgets(table):
    result = []
    for index, pool in enumerate(table['pools']):
        lo, hi = number_bounds(pool['rolls'])
        assert not pool.get('conditions') and not pool.get('functions') and not pool.get('bonus_rolls')
        weights = [entry.get('weight', 1) for entry in pool['entries']]
        entries = []
        for entry, weight in zip(pool['entries'], weights):
            assert entry['type'] == 'minecraft:item' and not entry.get('conditions')
            entries.append(dict(item=entry['name'], weight=weight,
                selection_probability_fraction=f'{weight}/{sum(weights)}',
                count_bounds=list(number_bounds(quantity(entry))),
                native_functions=entry.get('functions', [])))
        result.append(dict(pool=index, rolls_bounds=[lo, hi], luck_bonus_rolls=0,
            raw_item_unit_bounds=[lo * min(e['count_bounds'][0] for e in entries), hi * max(e['count_bounds'][1] for e in entries)],
            entries=entries, scope='Raw source selections/counts only; not final native stacks, effective modifiers, or placement frequency.'))
    return result


def validate_possible_reviews(tables, native, rows, effective, contract):
    review = contract['possible_physical_source_review']
    assert review['schema'] == 'bc.structure_loot_possible.v1'
    records = {r['id']: r for r in review['tables']}
    assert len(records) == len(review['tables']) and set(records) == set(POSSIBLE_IDENTITIES)
    assert not set(records) & set(contract['native_probe']['tables']), 'conditional reviews silently changed native selected-table obligations'
    broad = sorted(k for k,v in effective.items() if not v.get('conditions'))
    for key, record in records.items():
        assert rows[key]['kind'] == 'unknown-consumer' and rows[key]['role'] is None
        assert rows[key]['decision'] == record['decision'] == 'unchanged'
        assert tables[key] == native[key], ('conditional review modified shared/unknown native semantics', key)
        assert record['activity'] == record['effective_native_composition'] == 'UNKNOWN'
        assert record['inactive'] is False and record['native_blocking'] is True
        assert record['conditional_possible_physical_role'] == 'treasure'
        assert record['retained_semantic_identity'] == POSSIBLE_IDENTITIES[key]
        assert record['source_table_sha256'] == fingerprint(tables[key])
        assert record['current_source_pool_budgets'] == possible_pool_budgets(tables[key])
        assert record['direct_active_modifier_refs'] == sorted(k for k,v in effective.items() if key in set(strings(v)))
        assert record['unresolved_unconditional_modifier_registrations'] == broad
        assert record['preserve_special_state'] is True and record['unknown_shared_consumer'] is True
        assert record['unchanged_is_not_balance_or_placement_certification'] is True
        assert record['unchanged_preservation_rationale'] and record['frequency'] == 'UNKNOWN'
    goety = records['goety:chests/shack_book']
    assert goety['upstream_valuable_reward_budget_acknowledged'] == {
        'rolls': [2,10], 'paper_count_per_selection': [2,7], 'raw_max_paper_units': 70,
        'book_count_per_selection': [1,3], 'enchantment_levels': 30, 'treasure_enchantment': True,
        'frequency': 'UNKNOWN', 'shared_consumer_context': 'UNKNOWN',
    }


def reject_mutation(tables, candidates, contract, native, mutate):
    t, c, policy = copy.deepcopy(tables), set(candidates), copy.deepcopy(contract)
    mutate(t, c, policy)
    try:
        validate(t, c, policy, native)
    except (AssertionError, KeyError):
        return
    raise AssertionError('negative mutation was admitted')


def main():
    contract = json.loads((ROOT / 'src/test/resources/structure-loot-review.json').read_text())
    assert contract['schema'] == 'bc.structure_loot_review.v1'
    pins = {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted((ROOT / 'mods').glob('*.pw.toml'))}
    assert pins == contract['pin_sha256'], 'active pins changed; review table/consumer coverage again'
    for name, expected in contract['policy_sha256'].items():
        assert hashlib.sha256((ROOT / name).read_bytes()).hexdigest() == expected, ('independent acquisition policy changed; review effective rewards', name)
    details = {}
    tables, sources, references, candidates, code, injections, prefilled = load(ROOT, details=details)
    # Independently verified upstream table baseline; the full inventory above
    # already covers the identical non-table templates/code/modifier witnesses.
    native = load_table_baseline(ROOT, template_references=references, injections=injections,
                                 worldgen_consumers=details['worldgen_consumers'])
    assert fingerprint(injections) == contract['injection_sha256'], 'injection composition drift'
    effective = effective_injections(injections)
    assert fingerprint(effective) == contract['effective_injection_sha256'], 'effective modifier composition drift'
    assert hashlib.sha256(DESIGN_PATH.read_bytes()).hexdigest() == contract['authored_missing_sha256'], 'authored missing designs changed without review'
    validate_authored_items()
    rows = validate(tables, candidates, contract, native)
    validate_possible_reviews(tables, native, rows, effective, contract)
    for change in [
        lambda p: p['possible_physical_source_review']['tables'][0].update(activity='inactive'),
        lambda p: p['possible_physical_source_review']['tables'][0].update(native_blocking=False),
        lambda p: p['possible_physical_source_review']['tables'][0]['current_source_pool_budgets'][0]['entries'][0].update(native_functions=[]),
        lambda p: p['possible_physical_source_review']['tables'][0].update(conditional_possible_physical_role='encounter'),
    ]:
        mutated = copy.deepcopy(contract); change(mutated)
        try:
            validate_possible_reviews(tables, native, rows, effective, mutated)
        except AssertionError:
            pass
        else:
            raise AssertionError('possible-physical conditional review mutation admitted')
    unused_expected = unused_source_evidence(ROOT)
    def require_unused(evidence):
        assert evidence == unused_expected, 'reviewed unused-definition evidence drift or positive consumer'
        assert {row['id'] for row in evidence['definitions']} == set(UNUSED_CONTEXTS)
        assert all(not references.get(key) and not details['worldgen_consumers'].get(key) for key in UNUSED_CONTEXTS)
        assert all(rows[key]['kind'] == 'unknown-consumer' for key in UNUSED_CONTEXTS), 'source review cannot predeclare native inactivity'
    require_unused(contract['unused_definition_review'])
    for change in [
        lambda e: e['definitions'][0]['live_known_code_callers'].append('future:runtime/caller'),
        lambda e: e['definitions'][1]['external_data_consumers'].append('future:structures/chest.nbt'),
        lambda e: e['nonphysical_context'].update(block='mod:suspicious_chest'),
        lambda e: e['nonphysical_context'].update(id='mod:suspicious_chest'),
    ]:
        mutated = copy.deepcopy(contract['unused_definition_review']); change(mutated)
        try:
            require_unused(mutated)
        except AssertionError:
            pass
        else:
            raise AssertionError('unused/nonphysical context negative mutation admitted')
    repairs = validate_reference_repairs(contract)
    for key, row in rows.items():
        assert row['source'] == sources.get(key), ('source priority changed', key)
        assert row['template_consumers'] == sorted(references.get(key, [])), ('template consumers changed', key)
        assert row['active_injections'] == sorted(name for name, value in effective.items() if key in set(strings(value)))
        assert row['worldgen_consumers'] == details['worldgen_consumers'].get(key, [])
        assert row['override_layers'] == details['table_layers'].get(key, [])
        if row['override_layers']:
            # No arbitrary alphabetic-mod winner is claimed as effective priority.
            assert row['override_layers'][-1]['source'].startswith('kubejs!'), ('ambiguous native override priority', key)
        for witness in row.get('code_literal_witnesses', []):
            assert witness in code and key.split(':')[1].encode() in code[witness], ('code witness changed', key)
    # Guard against missing/new tables, broad scope, and state/reward regressions.
    reject_mutation(tables, candidates, contract, native, lambda t,c,p: c.add('future:chests/unreviewed'))
    reject_mutation(tables, candidates, contract, native, lambda t,c,p: t['quark:entities/stoneling_carry']['pools'][0].update(rolls=10))
    reject_mutation(tables, candidates, contract, native, lambda t,c,p: t['wares:gameplay/empty_package']['pools'][0].update(rolls=3))
    reject_mutation(tables, candidates, contract, native, lambda t,c,p: t['minecraft:empty'].update(pools=[{'rolls':1}]))
    reject_mutation(tables, candidates, contract, native, lambda t,c,p: t['minecraft:chests/simple_dungeon']['pools'][0].update(rolls=8))
    reject_mutation(tables, candidates, contract, native, lambda t,c,p: t['wares:chests/village/plains_warehouse']['pools'][2]['entries'][0]['functions'][0].update(tag='{}'))
    reject_mutation(tables, candidates, contract, native, lambda t,c,p: t['alexscaves:chests/underground_cabin_magnetic_caves']['pools'][1]['entries'][1]['functions'][0].update(tag='{}'))
    reject_mutation(tables, candidates, contract, native, lambda t,c,p: t['minecraft:chests/shipwreck_supply']['pools'][1].update(rolls=0))
    # Updating a table digest alone must not waive the exact protected-field diff.
    def protected_pool_with_updated_digest(t, c, p):
        key = 'minecraft:chests/simple_dungeon'
        t[key]['pools'][0]['rolls'] = 8
        next(row for row in p['tables'] if row['id'] == key)['table_sha256'] = fingerprint(t[key])
    reject_mutation(tables, candidates, contract, native, protected_pool_with_updated_digest)
    # New definitions cannot waive reward, NBT or quantity changes by merely
    # updating their review digest. They also may not claim a valid upstream table.
    def changed_new_design(t, c, p, change):
        key = 'fallout_wastelands_:chests/scraptier_1'
        change(t[key])
        next(row for row in p['tables'] if row['id'] == key)['table_sha256'] = fingerprint(t[key])
    reject_mutation(tables, candidates, contract, native, lambda t,c,p: changed_new_design(t,c,p,lambda table: table['pools'][0].update(rolls=20)))
    reject_mutation(tables, candidates, contract, native, lambda t,c,p: changed_new_design(t,c,p,lambda table: table['pools'][0]['entries'][0].update(name='minecraft:netherite_ingot')))
    reject_mutation(tables, candidates, contract, native, lambda t,c,p: changed_new_design(t,c,p,lambda table: table['pools'][0]['entries'][0].update(functions=[{'function':'minecraft:set_nbt','tag':'{forged:1b}'}])))
    observations = sample_changed_pools(rows, tables, contract)
    counts = collections.Counter(row['kind'] for row in rows.values())
    print('Structure loot ownership: reviewed pinned candidate inventory; 15 exact table overrides; eight explicit new missing-table designs; twelve negative table mutations rejected.')
    print(json.dumps({'reviewed':len(rows), 'classifications':dict(counts), 'unresolved':[k for k,v in rows.items() if v['kind']=='unresolved'], 'unknown_consumers':[k for k,v in rows.items() if v['kind']=='unknown-consumer'], 'authored_pool_samples':observations, 'exact_reference_repairs':repairs, 'reviewed_unused_source_contexts':list(UNUSED_CONTEXTS), 'unused_scope_negative_mutations':4, 'conditional_possible_physical_reviews':7, 'conditional_review_negative_mutations':4, 'native_acceptance':'PENDING separate exact-candidate runtime probe; source templates do not prove active placements', 'prefilled_template_occurrences_untouched':len(prefilled)}, indent=2))


if __name__ == '__main__':
    main()

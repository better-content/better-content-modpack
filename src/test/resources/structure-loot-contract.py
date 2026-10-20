#!/usr/bin/env python3
"""Executable reviewed inventory and bounded authored-pool statistics.

This is a source contract, not a second implementation of Minecraft loot or a
native acceptance claim. The existing Node contract exercises production filters.
"""
import collections
import copy
import hashlib
import json
from pathlib import Path
import random
import sys

ROOT = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / 'scripts'))
from structure_loot_inputs import effective_injections, fingerprint, load, strings


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
        assert row['rationale'] and row['decision'] in {'tune', 'unchanged'}
        assert row['kind'] in {'physical', 'mob', 'package', 'non-container', 'helper-or-injection', 'intentional-empty-or-cut', 'unknown-consumer', 'unresolved'}
        assert row['role'] in ({'supplies', 'encounter', 'treasure'} if row['kind'] == 'physical' else {None})
        assert fingerprint(tables.get(key)) == row['table_sha256'], ('review drift', key)
        if row['decision'] == 'tune':
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
    assert {key for key, row in rows.items() if row['decision'] == 'tune'} == set(contract['native_probe']['tables']), 'tuning escaped reviewed physical allowlist'
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
        if row['decision'] != 'tune':
            continue
        pool_ids = sorted({edit['path'][1] for edit in row['edits']})
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
    native, *_ = load(ROOT, fetch=False, pack_tables=False)
    assert fingerprint(injections) == contract['injection_sha256'], 'injection composition drift'
    effective = effective_injections(injections)
    assert fingerprint(effective) == contract['effective_injection_sha256'], 'effective modifier composition drift'
    rows = validate(tables, candidates, contract, native)
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
    observations = sample_changed_pools(rows, tables, contract)
    counts = collections.Counter(row['kind'] for row in rows.values())
    print('Structure loot ownership: reviewed pinned candidate inventory; 15 exact table overrides; nine negative mutations rejected.')
    print(json.dumps({'reviewed':len(rows), 'classifications':dict(counts), 'unresolved':[k for k,v in rows.items() if v['kind']=='unresolved'], 'unknown_consumers':[k for k,v in rows.items() if v['kind']=='unknown-consumer'], 'authored_pool_samples':observations, 'native_acceptance':'PENDING separate exact-candidate runtime probe; source templates do not prove active placements', 'prefilled_template_occurrences_untouched':len(prefilled)}, indent=2))


if __name__ == '__main__':
    main()

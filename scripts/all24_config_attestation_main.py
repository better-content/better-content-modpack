#!/usr/bin/env python3
"""Read-only finite Build22 Forge config-output comparison, not native acceptance.

The immutable inputs are the exact Build22 ZIP pair and Smoke receipt. Forge may
rewrite config files. This gate compares a completed source's config tree with a
hash-pinned same-pair Smoke tree, plus a hash-pinned failed-source witness used
only to bound the set of observed exceptions. Unknown changes always block.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import sys
import tomllib
import uuid
import zipfile

ROOT = Path(__file__).absolute().parents[1]
READY = ROOT / 'dist/build-26/ready.json'
SERVER = ROOT / 'dist/build-26/server/better-content.zip'
CLIENT = ROOT / 'dist/build-26/client/better-content.zip'
RUN = ROOT / 'generated/test-evidence/20261101T041701Z-2762886/smoke/run.json'
SMOKE = ROOT / 'generated/test-evidence/20261101T041701Z-2762886/smoke/fixture/server/better-content-server/config'
# R3 is a FAILED native attempt. It bounds config-output differences only;
# neither its source case nor this config comparison is a successor acceptance.
WITNESS = ROOT / 'generated/world-axiom-runtimes/main21-all24-20261101T0108-r3/config'
READY_SHA = '856d17fb522c343d34f28821ce00a2033f53bf23525ad6c35375208eee5ac263'
RUN_SHA = '4a6c54605a817140ed4c253811b8c04329d594e442f9cd72af008f9cca6f9543'
SERVER_SHA = 'c18aebc94e5b5d14860c5ef72725f65003d6f313c37295f7833216ec40d0c6fa'
CLIENT_SHA = 'e6bad4c0e1598dfd917e8494d3640a18b11d8268e7dcf26687c7743596145af9'
SMOKE_TREE_SHA = '31a4c1dfaf1504765c3210e208fee4df58ca4c573c3d4309286c732d195f00db'
WITNESS_TREE_SHA = '5c26a3c6e6e02a94215b1d0af99727b27879aec3459185c16abaa78e0b9c96d5'
ZIP_CONFIG_TREE_SHA = 'fa074dc2081eb10cae8f718cc5026e7b27b5d3b96ad7e27e8e207acfa4257f37'
RUN_ID = '20261101T041701Z-2762886'
MAX_FILES = 2500
MAX_FILE = 4 * 1024 * 1024
MAX_TOTAL = 32 * 1024 * 1024
PVJ = 'projectvibrantjourneys/'
TOML_SAME = {'c2me.toml', 'coldsweat/entity.toml', 'coldsweat/item.toml',
             'coldsweat/world.toml', 'explosionoverhaul/explosionoverhaul-common.toml',
             'terrablender.toml'}
PROPERTIES_SAME = {'dynamicvillagertrades.properties', 'nomobfarm.properties'}
ZIP_VALUE_EXCEPTIONS = {'ftbbackups2.json', 'globalgamerules-common.toml'}
OPTIONAL_ABSENT = {'forgeendertech/Biomes/minecraft$wooded_badlands.cfg'}
OPTIONAL_NEW = {'forgeendertech/Biomes/creatingspace$venus_hellground.cfg',
                'forgeendertech/Biomes/the_deep_void$gathering_grounds.cfg'}
# Finite reviewed provider-generated paths from the pinned Smoke and retained
# comparison runs. A syntactically valid but unobserved biome is still UNKNOWN.
KNOWN_OPTIONAL_BIOMES = OPTIONAL_NEW | OPTIONAL_ABSENT | {
    'forgeendertech/Biomes/ae2$spatial_storage.cfg',
    'forgeendertech/Biomes/minecraft$jungle.cfg',
    # Observed as inert, generated-only additions in retained Build24 failed source R1.
    'forgeendertech/Biomes/twilightforest$thornlands.cfg',
    'forgeendertech/Biomes/ars_nouveau$archwood_forest.cfg',
    # Build26 failed-source R1: inert exact-template output, not a success proof.
    'forgeendertech/Biomes/natures_spirit$blooming_dunes.cfg',
    'forgeendertech/Biomes/the_bumblezone$sugar_water_floor.cfg',
}
WORLD_AXIOM_INSERTIONS = {'better_world_management_feasibility:axiom_recipe_arena',
                          'better_world_management:empty'}


def need(ok, message):
    if not ok:
        raise ValueError(message)


def safe(path):
    path = Path(path).absolute()
    need('..' not in path.parts and not any(p.is_symlink() for p in (path, *path.parents)),
         'Symlink/traversal forbidden: ' + str(path))
    return path


def digest(data):
    return hashlib.sha256(data).hexdigest()


def sha(path):
    with safe(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def pairs(items):
    result = {}
    for key, value in items:
        need(key not in result, 'Duplicate JSON key: ' + key)
        result[key] = value
    return result


def strict_json(raw):
    return json.loads(raw, object_pairs_hook=pairs,
                      parse_constant=lambda value: need(False, 'Nonfinite JSON: ' + value))


def jsonc(raw):
    """Only JSONC line/block comments outside strings; reject unterminated syntax."""
    text = raw.decode('utf-8')
    output = []
    i, quoted, escaped = 0, False, False
    while i < len(text):
        c = text[i]
        if quoted:
            output.append(c)
            if escaped: escaped = False
            elif c == '\\': escaped = True
            elif c == '"': quoted = False
            i += 1; continue
        if c == '"': quoted = True; output.append(c); i += 1; continue
        if text.startswith('//', i):
            end = text.find('\n', i)
            output.append('\n' if end >= 0 else '')
            i = len(text) if end < 0 else end + 1; continue
        if text.startswith('/*', i):
            end = text.find('*/', i + 2)
            need(end >= 0, 'Unterminated JSONC comment')
            output.append(' ' * (end + 2 - i))
            i = end + 2; continue
        output.append(c); i += 1
    need(not quoted, 'Unterminated JSONC string')
    return strict_json(''.join(output))


def tree(root):
    root = safe(root)
    need(root.is_dir(), 'Missing config tree: ' + str(root))
    result, total = {}, 0
    for path in root.rglob('*'):
        path = safe(path)
        info = path.stat()
        need(info.st_uid == os.getuid(), 'Foreign-owned config entry: ' + str(path))
        need(path.is_dir() or (stat.S_ISREG(info.st_mode) and info.st_nlink == 1),
             'Nonregular/hardlinked config entry: ' + str(path))
        if path.is_dir(): continue
        name = path.relative_to(root).as_posix()
        need(name not in result and 0 < info.st_size <= MAX_FILE, 'Duplicate/empty/oversized config: ' + name)
        data = path.read_bytes()
        need(len(data) == info.st_size, 'Config changed while reading: ' + name)
        result[name] = data
        total += len(data)
        need(len(result) <= MAX_FILES and total <= MAX_TOTAL, 'Config inventory exceeds bounded profile')
    return result


def inventory_sha(rows):
    h = hashlib.sha256()
    for name, data in sorted(rows.items()):
        h.update(name.encode() + b'\0' + digest(data).encode() + b'\0' + str(len(data)).encode() + b'\n')
    return h.hexdigest()


def zip_config(path):
    need(sha(path) == SERVER_SHA, 'Exact Build22 server ZIP changed')
    result, total = {}, 0
    prefix = 'better-content-server/config/'
    with zipfile.ZipFile(safe(path)) as archive:
        names = archive.namelist()
        need(len(names) == len(set(names)), 'Duplicate ZIP entries')
        for entry in archive.infolist():
            name = entry.filename
            if not name.startswith(prefix) or entry.is_dir(): continue
            rel = name[len(prefix):]
            need(rel and not rel.startswith('/') and '\\' not in rel and ':' not in rel
                 and all(part not in ('', '.', '..') for part in rel.split('/'))
                 and not entry.flag_bits & 1 and entry.file_size <= MAX_FILE
                 and stat.S_IFMT(entry.external_attr >> 16) in (0, stat.S_IFREG),
                 'Unsafe ZIP config: ' + name)
            data = archive.read(entry)
            need(len(data) == entry.file_size and rel not in result, 'ZIP config size/duplicate drift')
            result[rel] = data; total += len(data)
            need(len(result) <= MAX_FILES and total <= MAX_TOTAL, 'ZIP config inventory exceeds bound')
    need(len(result) == 880 and inventory_sha(result) == ZIP_CONFIG_TREE_SHA,
         'Original Build22 ZIP config inventory changed')
    return result


def validate_receipts(ready, run):
    pair = {'server': str(SERVER), 'client': str(CLIENT),
            'server_sha256': SERVER_SHA, 'client_sha256': CLIENT_SHA}
    need(ready.get('schema') == 'bc.release_ready.v4' and ready.get('status') == 'passed'
         and ready.get('run_id') == RUN_ID and ready.get('candidates') == pair
         and ready.get('tiers', {}).get('smoke') == {'status': 'passed',
             'receipt': {'path': str(RUN), 'sha256': RUN_SHA}}, 'Build22 ready/Smoke pair mismatch')
    need(run.get('schema') == 'bc.session_run.v2' and run.get('tier') == 'smoke'
         and run.get('status') == 'passed' and run.get('acceptance_eligible') is True
         and run.get('run_id') == RUN_ID and run.get('candidates') == pair
         and run.get('fixture') == str(SMOKE.parents[2]), 'Smoke receipt does not bind fixture/pair')


def validate_reference_trees(smoke, witness):
    need(len(smoke) == 1961 and inventory_sha(smoke) == SMOKE_TREE_SHA,
         'Smoke config reference changed')
    need(len(witness) == 1962 and inventory_sha(witness) == WITNESS_TREE_SHA,
         'Failed-source config witness changed')


def anchors():
    need(sha(READY) == READY_SHA and sha(RUN) == RUN_SHA, 'Build22 ready/Smoke receipt changed')
    ready, run = strict_json(READY.read_bytes()), strict_json(RUN.read_bytes())
    validate_receipts(ready, run)
    need(sha(CLIENT) == CLIENT_SHA, 'Exact Build22 client ZIP changed')
    original = zip_config(SERVER)
    smoke = tree(SMOKE); witness = tree(WITNESS)
    validate_reference_trees(smoke, witness)
    need(set(original) - set(smoke) == {'endermanoverhaul.json'}
         and set(original) - set(witness) == {'endermanoverhaul.json'}
         and smoke.get('endermanoverhaul.jsonc') == witness.get('endermanoverhaul.jsonc'),
         'Enderman JSON-to-JSONC output closure differs')
    return original, smoke, witness


def typed_toml(data):
    return tomllib.loads(data.decode('utf-8'))


def properties(data):
    result = {}
    for line in data.decode('iso-8859-1').splitlines():
        line = line.strip()
        if not line or line[0] in '#!': continue
        need('=' in line, 'Unsupported Java properties syntax')
        key, value = line.split('=', 1)
        need(key not in result, 'Duplicate Java property')
        result[key] = value
    return result


def biome_rows(data):
    rows = strict_json(data)
    need(isinstance(rows, list), 'PVJ biome config must be a list')
    result = {}
    for row in rows:
        need(isinstance(row, dict) and set(row) == {'biomeName', 'chance'}
             and isinstance(row['biomeName'], str) and type(row['chance']) is int
             and row['biomeName'] not in result, 'PVJ duplicate/malformed biome row')
        result[row['biomeName']] = row['chance']
    return result


def equivalent(name, original, smoke, actual):
    """Only exact observed path families; no generic parser-equality escape hatch."""
    if name in TOML_SAME:
        need(typed_toml(smoke) == typed_toml(actual), name + ': typed TOML changed')
        return 'same_typed_toml'
    if name == 'defaultresources.json':
        need(jsonc(smoke) == jsonc(actual), 'Extracted resource policy changed')
        return 'same_typed_resource_extract_policy'
    if name in PROPERTIES_SAME:
        need(properties(smoke) == properties(actual), name + ': Java properties changed')
        return 'same_typed_properties'
    if name.startswith(PVJ):
        need(biome_rows(smoke) == biome_rows(actual), name + ': PVJ biome probabilities changed')
        return 'same_biome_rows_reordered'
    if name == 'emi_loot_config.toml':
        a, b = typed_toml(smoke), typed_toml(actual)
        need(isinstance(a.get('skippedKeys'), list) and isinstance(b.get('skippedKeys'), list)
             and len(a['skippedKeys']) == len(set(a['skippedKeys']))
             and len(b['skippedKeys']) == len(set(b['skippedKeys']))
             and set(a['skippedKeys']) == set(b['skippedKeys']), 'EMI skipped key membership changed')
        a['skippedKeys'].sort(); b['skippedKeys'].sort()
        need(a == b, 'EMI config changed outside skipped key order')
        return 'same_emi_skipped_key_set'
    if name == 'structure_generation_improver.json':
        a, b = strict_json(smoke), strict_json(actual)
        left, right = a.get('Template file list'), b.get('Template file list')
        need(isinstance(left, list) and isinstance(right, list)
             and len(right) == len(left) + 2 and len(right) == len(set(right))
             and set(right) - set(left) == WORLD_AXIOM_INSERTIONS
             and [x for x in right if x not in WORLD_AXIOM_INSERTIONS] == left,
             'Unexpected structure template discovery')
        b['Template file list'] = left
        need(a == b, 'Structure config changed outside two World Axiom fixture templates')
        return 'two_exact_native_fixture_templates'
    if name in ZIP_VALUE_EXCEPTIONS:
        need(original is not None, 'Packaged semantic baseline missing')
        if name.endswith('.toml'):
            packaged, base, changed = map(typed_toml, (original, smoke, actual))
            # Two exact vanilla gamerule paths are bounded by packaged defaults
            # and the same-pair connected Smoke output. No third value is admitted.
            paths = (('gamerules', 'player', 'spawn_radius'),
                     ('gamerules', 'spawning', 'do_patrol_spawning'))
            for path in paths:
                def at(d):
                    for part in path: d = d[part]
                    return d
                allowed = (at(packaged), at(base), 0) if path == ('gamerules', 'player', 'spawn_radius') else (at(packaged), at(base))
                need(at(changed) in allowed, 'Source gamerule differs from packaged, same-pair Smoke and observed successor spawn')
                d = base
                for part in path[:-1]: d = d[part]
                d[path[-1]] = at(changed)
            # Both the retained failed source and Build24 R1 emitted native
            # GameTest daylight=false; packaged and same-pair Smoke are true.
            daylight = base['gamerules']['updates']['do_daylight_cycle']
            if daylight != changed['gamerules']['updates']['do_daylight_cycle']:
                need(daylight is True and packaged['gamerules']['updates']['do_daylight_cycle'] is True
                     and changed['gamerules']['updates']['do_daylight_cycle'] is False,
                     'Unobserved GameTest daylight rule')
                base['gamerules']['updates']['do_daylight_cycle'] = False
            need(base == changed, 'Source gamerules changed outside bounded GameTest output')
        else:
            packaged, base, changed = map(jsonc, (original, smoke, actual))
            need(type(packaged.get('enabled')) is bool and changed.get('enabled') == packaged['enabled'],
                 'Source backup setting differs from packaged value')
            base['enabled'] = packaged['enabled']
            need(base == changed, 'Source backup config changed outside packaged enabled value')
        return 'packaged_value_over_smoke_fixture_override'
    if name == 'forgeendertech/Wind.cfg':
        pattern = rb'(?m)(?P<head>^\s*# Random seed \[range: -2147483648 ~ 2147483647, default: )-?[0-9]+(?P<tail>\]\n\s*I:randomSeed=)-?[0-9]+'
        def normalized(data):
            matches = list(re.finditer(pattern, data))
            need(len(matches) == 1, 'ForgeEndertech seed syntax changed')
            match = matches[0]
            values = re.findall(rb'-?[0-9]+', data[match.start():match.end()])
            need(int(values[-1]) == int(values[-2]) and -2**31 <= int(values[-1]) < 2**31,
                 'ForgeEndertech seed/default mismatch')
            return data[:match.start()] + b'SEED_SENTINEL' + data[match.end():]
        need(normalized(smoke) == normalized(actual), 'ForgeEndertech config changed outside random seed')
        return 'bounded_random_seed_only'
    if name == 'resourceful-config-web.json':
        a, b = strict_json(smoke), strict_json(actual)
        # The exact password leaf is found by a closed recursive search;
        # every other key and value must remain equal.
        def replace_password(node):
            hits = []
            def walk(value):
                if isinstance(value, dict):
                    for key, child in value.items():
                        if key == 'password' and isinstance(child, str):
                            need(re.fullmatch(r'[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}', child),
                                 'Resourceful password is not a UUID')
                            hits.append(child); value[key] = 'UUID_SENTINEL'
                        else: walk(child)
                elif isinstance(value, list):
                    for child in value: walk(child)
            walk(node)
            need(len(hits) == 1, 'Resourceful password leaf changed')
            return node
        need(replace_password(a) == replace_password(b), 'Resourceful config changed outside generated password')
        return 'generated_uuid_password_only'
    raise ValueError('Unknown changed config path: ' + name)


# A closed, byte-exact, disabled ForgeEndertech generated-biome template.
# The only variable is the biome registry name, constrained to its filename.
DISABLED_BIOME_TEMPLATE = b'''# Configuration file

Biome {
    # Biome registry name [default: BIOME_NAMESPACE:BIOME_PATH]
    S:name=BIOME_NAMESPACE:BIOME_PATH
}


Config {
    # If set to False, parameters from this config will not be used. [default: false]
    B:enabled=false
}


Wind {
    # Defines probability of wind strength and direction change in percentage. [range: 0.0 ~ 100.0, default: 1.0]
    S:changeProbability=1.0

    # Defines how fast the wind changes. [range: 0.0 ~ 1.0, default: 0.15]
    S:changeStep=0.15

    # Defines maximum wind strength. [range: 0 ~ 30, default: 10]
    I:maxStrength=10

    # Defines the wind direction.
    # Possible values: RANDOM, NORTH, SOUTH, WEST, EAST, PLAYER, SPAWN
    #  [default: RANDOM]
    S:targetDirection=RANDOM
}


'''


def disabled_generated_biome(name, data):
    found = re.fullmatch(r'forgeendertech/Biomes/([a-z0-9_.-]+)\$([a-z0-9_./-]+)\.cfg', name)
    return bool(found and data == DISABLED_BIOME_TEMPLATE.replace(
        b'BIOME_NAMESPACE:BIOME_PATH', (found.group(1) + ':' + found.group(2)).encode()))


def verify(candidate, original, smoke, witness, *, registered_biomes=None):
    if registered_biomes is not None:
        need(isinstance(registered_biomes, list) and 50 <= len(registered_biomes) <= 10000
             and all(isinstance(item, str) and re.fullmatch(r'[a-z0-9_.-]+:[a-z0-9_./-]+', item)
                     for item in registered_biomes)
             and registered_biomes == sorted(set(registered_biomes)),
             'Invalid native registered biome roster')
        registered_biomes = set(registered_biomes)
    need(set(smoke) - set(witness) == OPTIONAL_ABSENT
         and set(witness) - set(smoke) == OPTIONAL_NEW,
         'Witness optional config path closure changed')
    observed_differences = {name for name in set(smoke) & set(witness) if smoke[name] != witness[name]}
    need(len(observed_differences) == 21, 'Witness changed-path count differs')
    extra = set(candidate) - set(smoke)
    missing = set(smoke) - set(candidate)
    def admitted_new(name):
        if not disabled_generated_biome(name, candidate[name]): return False
        if registered_biomes is None: return name in KNOWN_OPTIONAL_BIOMES
        match = re.fullmatch(r'forgeendertech/Biomes/([a-z0-9_.-]+)\$([a-z0-9_./-]+)\.cfg', name)
        return bool(match and match.group(1) + ':' + match.group(2) in registered_biomes)
    need(all(admitted_new(name) for name in extra),
         'Unknown new config path, unregistered biome ID or non-inert generated biome')
    need(missing <= KNOWN_OPTIONAL_BIOMES and
         all(disabled_generated_biome(name, smoke[name]) for name in missing),
         'Missing required Smoke config')
    need(set(original) - set(candidate) == {'endermanoverhaul.json'},
         'Unexpected original ZIP config absent; JSON-to-JSONC closure required')
    need(candidate.get('endermanoverhaul.jsonc') == smoke['endermanoverhaul.jsonc'],
         'Enderman replacement JSONC differs from exact Smoke output')
    classes = {}
    for name, data in sorted(candidate.items()):
        if name not in smoke:
            classes[name] = 'exact_inert_generated_biome_template'; continue
        if data == smoke[name]:
            classes[name] = 'byte_equal_smoke'; continue
        need(name in observed_differences or name in ZIP_VALUE_EXCEPTIONS or name == 'emi_loot_config.toml' or name in TOML_SAME or name in PROPERTIES_SAME
             or name.startswith(PVJ) or name == 'defaultresources.json',
             'Unknown changed config path: ' + name)
        classes[name] = equivalent(name, original.get(name), smoke[name], data)
    # No original ZIP path may change to a third value except a named,
    # bounded observed exception above. The original ZIP is an input baseline,
    # not a post-Forge immutable config assertion.
    return {'schema': 'world_axioms_main_build26_config_attestation_v1',
            'status': 'CONFIG_ONLY_MATCH', 'native_acceptance': False,
            'original_zip_config_files': len(original), 'smoke_config_files': len(smoke),
            'candidate_config_files': len(candidate), 'candidate_tree_sha256': inventory_sha(candidate),
            'native_biome_registry_sha256': (hashlib.sha256('\n'.join(sorted(registered_biomes)).encode()).hexdigest()
                                             if registered_biomes is not None else None),
            'classifications': classes}


def attest(runtime, material_sha, source_result_sha):
    runtime = safe(runtime)
    need(runtime.parent == ROOT / 'generated/world-axiom-runtimes'
         and re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._-]{0,127}', runtime.name),
         'Require direct main-pack disposable runtime nonce')
    need(re.fullmatch('[0-9a-f]{64}', material_sha or '') and re.fullmatch('[0-9a-f]{64}', source_result_sha or ''),
         'Explicit material and source-result SHA required')
    material_path = runtime / 'runtime-inputs-v1.json'
    result_path = runtime / 'all24-launch-v1/source/result.json'
    need(sha(material_path) == material_sha and sha(result_path) == source_result_sha,
         'Candidate material/source-result SHA mismatch')
    material, result = strict_json(material_path.read_bytes()), strict_json(result_path.read_bytes())
    need(material.get('schema') == 'world_axioms_main_build26_materialization_v1'
         and material.get('status') == 'materialized_unaccepted'
         and material.get('destination') == str(runtime)
         and material.get('accepted_zip') == str(SERVER)
         and material.get('client_zip') == str(CLIENT)
         and material.get('accepted_zip_expected_sha256') == SERVER_SHA
         and material.get('client_zip_expected_sha256') == CLIENT_SHA,
         'Candidate not materialized from exact Build22 pair')
    need(result.get('schema') == 'world_axioms_real_succession_launch_v1'
         and result.get('phase') == 'source' and result.get('runtime') == str(runtime)
         and result.get('materialization_sha256') == material_sha
         and result.get('status') in {'FAILED', 'PASS'} and result.get('finished_at')
         and isinstance(result.get('owner_uuid'), str)
         and str(uuid.UUID(result['owner_uuid'])) == result['owner_uuid'],
         'Candidate config not bound to terminal source attempt')
    original, smoke, witness = anchors()
    need(material.get('pinned_release', {}).get('ready_sha256') == READY_SHA,
         'Candidate materialization ready marker differs')
    candidate = tree(runtime / 'config')
    native = result.get('promotion', {}).get('fixture', {}).get('registered_biomes')
    if result['status'] == 'PASS':
        receipt = strict_json((runtime / 'world-axiom-succession-fixture-v1.json').read_bytes())
        need(receipt.get('registered_biomes') == native and receipt.get('source_process') ==
             result['promotion']['fixture']['source_process'], 'Native biome registry binding changed')
    report = verify(candidate, original, smoke, witness, registered_biomes=native)
    need(inventory_sha(tree(runtime / 'config')) == report['candidate_tree_sha256'],
         'Candidate config changed during attestation')
    need(inventory_sha(tree(SMOKE)) == SMOKE_TREE_SHA and
         inventory_sha(tree(WITNESS)) == WITNESS_TREE_SHA,
         'Pinned reference changed during attestation')
    report.update(runtime=str(runtime), materialization_sha256=material_sha,
                  source_result_status=result['status'],
                  source_result_sha256=source_result_sha, ready_sha256=READY_SHA,
                  smoke_run_sha256=RUN_SHA, smoke_tree_sha256=SMOKE_TREE_SHA,
                  failed_source_witness_tree_sha256=WITNESS_TREE_SHA,
                  exact_server_zip_sha256=SERVER_SHA, exact_client_zip_sha256=CLIENT_SHA)
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--runtime', type=Path, required=True)
    parser.add_argument('--materialization-sha256', required=True)
    parser.add_argument('--source-result-sha256', required=True)
    args = parser.parse_args()
    try:
        report = attest(args.runtime, args.materialization_sha256, args.source_result_sha256)
    except Exception as error:
        print('Build22 config attestation BLOCKED: ' + type(error).__name__ + ': ' + str(error), file=sys.stderr)
        return 1
    print(json.dumps({k: v for k, v in report.items() if k != 'classifications'}, sort_keys=True))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())

#!/usr/bin/env python3
"""Strict pre-world projection of immutable World Axiom contracts. Never launches a game."""
import argparse
import copy
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import sys
import tempfile
import uuid
import zipfile

IDS = frozenset('iron_law eternal_engine alloy_testament captive_hearth ark seed_covenant patient_hive familiars_name cartographers_memory font_atlas prospectors_eye celestial_almanac communal_archive fortified_beginnings settled_arrival founders_exchange sacrificial_tradition spirit_awakening chalk_covenant patient_alchemist shaped_horizon turning_year deep_compact chosen_omen'.split())
INHERITANCE = frozenset('ark seed_covenant patient_hive familiars_name'.split())
FIELDS = ('schema', 'phase', 'lineage', 'base_generation', 'revision', 'transaction', 'source_world', 'selected', 'configuration', 'payloads', 'nominators')
SNAPSHOT = Path('data/better_world_management/world-axioms-v1.json')
STATE = Path('.better_world_management')
RUNTIME = Path('kubejs/config/world_axioms_runtime.json')
PACK = Path('datapacks/world_axioms_v1')
FAMILIES = ('ironstone', 'copper_bloom', 'tin_quartz', 'brassroot')
CHOICES = {
    'shaped_horizon': {'preset': ('ordinary', 'large_biomes')},
    'turning_year': {'season': ('early_spring', 'early_summer', 'early_autumn', 'early_winter')},
    'deep_compact': {'deposit': FAMILIES, 'band': ('shallow', 'deep')},
    'founders_exchange': {'currency': ('impact', 'tempo', 'work', 'mobility', 'endurance', 'robustness', 'renewal', 'control')},
    'font_atlas': {'currency': ('impact', 'tempo', 'work', 'mobility', 'endurance', 'robustness', 'renewal', 'control')},
    'chosen_omen': {'event': ('thin_veil', 'spirit_fair', 'wandering_lights', 'open_ways')},
    'cartographers_memory': {'kind': ('monument', 'mansion', 'mineshaft', 'fortress')},
}
DEFAULTS = {key: {field: options[0] for field, options in fields.items()} for key, fields in CHOICES.items()}
DEFAULTS['founders_exchange'] = {'currency': 'renewal'}
DEFAULTS['font_atlas'] = {'currency': 'renewal'}
DEFAULTS['communal_archive'] = {'plaque': 'Those who built before us are not forgotten.'}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def safe(path):
    path = Path(path)
    require('..' not in path.parts, 'Parent traversal is forbidden')
    path = path.absolute()
    for part in (path, *path.parents):
        require(not part.is_symlink(), 'Symlink path forbidden: ' + str(part))
    return path


def contained(root, path):
    path = safe(path)
    require(path != root and path.is_relative_to(root), 'Path must be inside ROOT')
    return path


def unique(pairs):
    value = {}
    for key, entry in pairs:
        require(key not in value, 'Duplicate JSON key: ' + key)
        value[key] = entry
    return value


def integer(raw):
    require(raw != '-0', 'Noncanonical negative-zero integer')
    return int(raw)


def parse(raw):
    return json.loads(raw, object_pairs_hook=unique, parse_int=integer, parse_constant=lambda x: (_ for _ in ()).throw(ValueError('Nonfinite JSON')))


def read(path, limit=65536):
    path = safe(path)
    require(path.is_file() and path.stat().st_size <= limit, 'Missing/oversized file: ' + str(path))
    return path.read_bytes()


def compact(value):
    # Matches Gson JsonObject.toString(), not GsonBuilder's pretty-print encoding.
    return json.dumps(value, ensure_ascii=False, separators=(',', ':'), allow_nan=False).replace('\u2028', '\\u2028').replace('\u2029', '\\u2029').encode('utf-8')


def text(value):
    require(isinstance(value, str), 'Expected string')
    value.encode('utf-8', errors='strict')
    return value


def length(value):
    return len(text(value).encode('utf-16-le')) // 2


def identifier(value):
    require(re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._-]{0,127}', text(value)) is not None, 'Invalid identity')
    return value


def counter(value, maximum=9223372036854775807):
    require(type(value) is int and 0 <= value <= maximum, 'Invalid counter')
    return value


def strings(value):
    require(type(value) is dict and len(value) <= 24, 'Invalid string map')
    return {text(key): text(entry) for key, entry in sorted(value.items())}


def config(node, supplied):
    supplied = strings(supplied)
    require(len(supplied) <= 5, 'Too many configuration fields')
    values = dict(DEFAULTS.get(node, {}))
    values.update(supplied)
    keys = set(CHOICES.get(node, {}))
    if node == 'communal_archive':
        keys = {'plaque', 'book1', 'book2', 'book3', 'book4'}
    require(set(values) <= keys, 'Unknown configuration field')
    require(all('\0' not in v and length(v) <= 1027 for v in values.values()), 'Invalid configuration string')
    for key, options in CHOICES.get(node, {}).items():
        require(values[key] in options, 'Invalid configuration choice')
    if node == 'communal_archive':
        require(length(values['plaque']) <= 256, 'Plaque exceeds limit')
        for i in range(1, 5):
            pages = values.get('book' + str(i), '').split('\n')
            require(len(pages) <= 4 and all(length(p) <= 256 for p in pages), 'Archive pages exceed limit')
    return dict(sorted(values.items()))


def decode(raw):
    require(len(raw) <= 65536, 'Oversized contract')
    value = parse(raw)
    require(type(value) is dict and set(value) == set(FIELDS) | {'sha256'}, 'Unknown/incomplete contract fields')
    require(value['schema'] == 'world_axioms_v1' and value['phase'] == 'committed', 'Contract must be committed world_axioms_v1')
    for key in ('lineage', 'transaction'):
        identifier(value[key])
    world_name = text(value['source_world'])
    require(re.fullmatch(r'[A-Za-z0-9._-]{1,128}', world_name) is not None and world_name not in ('.', '..'), 'Invalid source world name')
    base = counter(value['base_generation'], 9223372036854775806)
    counter(value['revision'])
    selected = value['selected']
    require(type(selected) is list and all(type(v) is str for v in selected), 'Invalid selection array')
    require(len(selected) == len(set(selected)) and set(selected) <= IDS, 'Duplicate/unknown selected ID')
    require(len(selected) <= min(24, base + 1), 'Point budget exceeded')
    require(type(value['configuration']) is dict and len(value['configuration']) <= 24, 'Invalid configuration')
    require(set(value['configuration']) <= set(selected), 'Configuration for unselected axiom')
    normalized = copy.deepcopy(value)
    normalized['selected'] = sorted(selected)
    normalized['configuration'] = {key: config(key, value['configuration'].get(key, {})) for key in sorted(selected)}
    for key in ('payloads', 'nominators'):
        normalized[key] = strings(value[key])
    require(set(normalized['payloads']) <= set(selected) & (INHERITANCE | {'cartographers_memory'}), 'Unexpected nomination payload')
    require(all(length(v) <= 16384 for v in normalized['payloads'].values()), 'Oversized nomination payload')
    require(set(normalized['nominators']) <= set(normalized['payloads']) & INHERITANCE, 'Unexpected nominator')
    for actor in normalized['nominators'].values():
        require(str(uuid.UUID(actor)).lower() == actor.lower(), 'Invalid nominator UUID')
    body = {key: normalized[key] for key in FIELDS}
    require(value['sha256'] == hashlib.sha256(compact(body)).hexdigest(), 'Contract checksum mismatch')
    return normalized


def mkdir(path):
    path = safe(path)
    missing = []
    for part in (path, *path.parents):
        if part.exists():
            require(part.is_dir(), 'Directory ancestor is not a directory')
            break
        missing.append(part)
    for part in reversed(missing):
        part.mkdir()
        fsync(part.parent)
        fsync(part)


def atomic(path, raw):
    path = safe(path)
    mkdir(path.parent)
    safe(path)
    fd, temporary = tempfile.mkstemp(prefix=path.name + '.', suffix='.partial', dir=path.parent)
    try:
        with os.fdopen(fd, 'wb') as stream:
            stream.write(raw)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
        fsync(path.parent)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def fsync(path):
    fd = os.open(safe(path), os.O_RDONLY | os.O_DIRECTORY)
    try:
        os.fsync(fd)
    finally:
        os.close(fd)


def encoded(value):
    return compact(value) + b'\n'


def tsv(path, magic, keys):
    lines = read(path).decode('utf-8').splitlines()
    require(len(lines) == len(keys) + 1 and lines[0] == magic, 'Invalid versioned TSV')
    result = {}
    for line, key in zip(lines[1:], keys):
        parts = line.split('\t')
        require(len(parts) == 2 and parts[0] == key and parts[1] and '\r' not in parts[1], 'Invalid TSV field ' + key)
        result[key] = parts[1]
    return result


def lineage(root):
    state = tsv(root / STATE / 'lineage-v5.tsv', 'BC_PRESTIGE_LINEAGE_V5', ('lineage', 'total_prestiges', 'generation'))
    identifier(state['lineage'])
    for key in ('generation', 'total_prestiges'):
        require(re.fullmatch('0|[1-9][0-9]*', state[key]) is not None, 'Invalid lineage counter')
        counter(int(state[key]))
    require(state['generation'] == state['total_prestiges'], 'Lineage counter mismatch')
    return state['lineage'], int(state['generation'])


def reset_identity(root, world, build, allow_promoted=False):
    reset = tsv(root / STATE / 'control/reset-request-v5.tsv', 'BC_PRESTIGE_RESET_V5',
                ('state', 'lineage', 'base_generation', 'transaction', 'world', 'old_seed', 'biome_1', 'biome_2', 'biome_3', 'seed_mode'))
    require(reset['state'] == 'committed' and reset['seed_mode'] == 'random', 'Invalid reset state')
    require(reset['lineage'] == build['lineage'] and reset['base_generation'] == str(build['base_generation'])
            and reset['transaction'] == build['transaction'] and reset['world'] == build['source_world'] == world.name,
            'Stale reset identity')
    identity, generation = lineage(root)
    allowed = {build['base_generation'], build['base_generation'] + 1} if allow_promoted else {build['base_generation']}
    require(identity == build['lineage'] and generation in allowed, 'Stale durable lineage')


def ore_projection(root, build):
    if build is None or 'deep_compact' not in build['selected']:
        return {}
    options = config('deep_compact', build['configuration'].get('deep_compact', {}))
    low, high = (0, 80) if options['band'] == 'shallow' else (-48, 16)
    safe(root / 'mods')
    jars = list((root / 'mods').glob('better-ore-geology-*.jar'))
    require(len(jars) == 1, 'Deep Compact requires exactly one native better-ore-geology jar')
    jar = safe(jars[0])
    require(jar.is_file(), 'Invalid geology owner jar')
    files = {}
    with zipfile.ZipFile(jar) as archive:
        require(len(archive.namelist()) == len(set(archive.namelist())), 'Duplicate jar entries')
        for variant in ('home', 'echo'):
            name = 'data/better_ore_geology/worldgen/placed_feature/' + options['deposit'] + '_' + variant + '.json'
            info = archive.getinfo(name)
            require(info.file_size <= 65536 and (info.external_attr >> 16) & 0o170000 != 0o120000, 'Unsafe native feature entry')
            raw = archive.read(info)
            feature = parse(raw)
            require(type(feature) is dict and type(feature.get('placement')) is list, 'Invalid native placed feature')
            heights = [p for p in feature['placement'] if p.get('type') == 'minecraft:height_range']
            require(len(heights) == 1, 'Expected one native height modifier')
            height = heights[0]['height']
            require(height.get('type') in ('minecraft:uniform', 'minecraft:trapezoid'), 'Unsupported native height distribution')
            for bound in ('min_inclusive', 'max_inclusive'):
                require(type(height.get(bound)) is dict and set(height[bound]) == {'absolute'}
                        and type(height[bound]['absolute']) is int, 'Expected absolute native height bounds')
            expected = copy.deepcopy(feature)
            next(h for h in expected['placement'] if h.get('type') == 'minecraft:height_range')['height']['min_inclusive']['absolute'] = low
            next(h for h in expected['placement'] if h.get('type') == 'minecraft:height_range')['height']['max_inclusive']['absolute'] = high
            # Edit only the two numeric byte spans. All count/morphology/formatting bytes survive unchanged.
            for bound, number in (('min_inclusive', low), ('max_inclusive', high)):
                pattern = rb'("' + bound.encode() + rb'"\s*:\s*\{\s*"absolute"\s*:\s*)-?[0-9]+'
                raw, count = re.subn(pattern, lambda match: match[1] + str(number).encode(), raw)
                require(count == 1, 'Ambiguous native height byte spans')
            require(parse(raw) == expected, 'Projection changed native data outside selected height bounds')
            files[name] = raw
    files['pack.mcmeta'] = encoded({'pack': {'pack_format': 15, 'description': 'Transaction-scoped World Axiom deposit height override'}})
    return files


def owned_pack(world, target=None):
    target = safe(target if target is not None else world / PACK)
    marker = '.world-axioms-owned.json'
    if target.exists():
        require(target.is_dir(), 'Projection datapack is not a directory')
        require((target / marker).is_file(), 'Refusing to replace unowned datapack')
        owned = parse(read(target / marker))
        require(owned.get('schema') == 'world_axioms_datapack_v1', 'Unknown owned datapack marker')
        actual = []
        for path in target.rglob('*'):
            safe(path)
            require(path.is_file() or path.is_dir(), 'Unsafe datapack entry')
            if path.is_file():
                actual.append(path.relative_to(target).as_posix())
        require(set(actual) == set(owned['files']) | {marker}, 'Unowned files in generated datapack')
        allowed = {'pack.mcmeta'} | {'data/better_ore_geology/worldgen/placed_feature/' + f + '_' + v + '.json' for f in FAMILIES for v in ('home', 'echo')}
        require(set(owned['files']) <= allowed, 'Invalid owned datapack manifest')
    else:
        owned = {'files': []}
    return owned


def write_pack(world, files, build):
    target = safe(world / PACK)
    work = safe(world / '.world-axioms-projection')
    previous = safe(work / 'previous')
    marker = '.world-axioms-owned.json'
    # Recover an interrupted directory swap. Staging lives OUTSIDE datapacks so partial packs cannot auto-enable.
    if previous.exists():
        owned_pack(world, previous)
        if not target.exists():
            mkdir(target.parent)
            os.replace(previous, target)
            fsync(target.parent)
            fsync(work)
        else:
            owned_pack(world)
            shutil.rmtree(previous)
            fsync(work)
    owned_pack(world)
    if not files and not target.exists():
        return
    mkdir(work)
    stage = None
    if files:
        stage = Path(tempfile.mkdtemp(prefix='stage-', dir=work))
        fsync(work)
        for name, raw in files.items():
            atomic(stage / name, raw)
        atomic(stage / marker, encoded({'schema': 'world_axioms_datapack_v1', 'build_sha256': build['sha256'], 'files': sorted(files)}))
        owned_pack(world, stage)
    mkdir(target.parent)
    if target.exists():
        os.replace(target, previous)
        fsync(target.parent)
        fsync(work)
    try:
        if stage is not None:
            os.replace(stage, target)
            fsync(target.parent)
            fsync(work)
    except OSError:
        if previous.exists() and not target.exists():
            os.replace(previous, target)
            fsync(target.parent)
            fsync(work)
        raise
    if previous.exists():
        shutil.rmtree(previous)
        fsync(work)


def preset(root, build):
    large = build is not None and 'shaped_horizon' in build['selected'] and config('shaped_horizon', build['configuration'].get('shaped_horizon', {}))['preset'] == 'large_biomes'
    path = root / 'server.properties'
    raw = read(path, 1048576)
    lines = raw.splitlines(keepends=True)
    matching = [i for i, line in enumerate(lines) if re.match(rb'\s*level-type\s*[=:]', line)]
    require(len(matching) <= 1, 'Duplicate level-type property')
    replacement = b'level-type=' + (b'minecraft:large_biomes' if large else b'minecraft:normal') + b'\n'
    if matching:
        lines[matching[0]] = replacement
    else:
        if lines and not lines[-1].endswith((b'\n', b'\r')):
            lines[-1] += b'\n'
        lines.append(replacement)
    return b''.join(lines)


def project(root, world, build, snapshot_raw=None):
    # Validate all sources and existing destinations before mutations; startup aborts on any failure.
    files = ore_projection(root, build)
    properties = preset(root, build)
    owned_pack(world)
    for destination in (root / RUNTIME, root / 'server.properties', world / SNAPSHOT, world / PACK):
        safe(destination)
    if snapshot_raw is not None:
        atomic(world / SNAPSHOT, snapshot_raw)
    write_pack(world, files, build)
    runtime = {'schema': 'world_axioms_runtime_v1', 'lineage': build['lineage'] if build else lineage(root)[0],
               'generation': build['base_generation'] + 1 if build else 0, 'transaction': build['transaction'] if build else '',
               'selected': build['selected'] if build else [], 'configuration': build['configuration'] if build else {},
               'build_sha256': build['sha256'] if build else ''}
    atomic(root / RUNTIME, encoded(runtime))
    atomic(root / 'server.properties', properties)


def validate_projection(root, world, build):
    expected = {'schema': 'world_axioms_runtime_v1', 'lineage': build['lineage'],
                'generation': build['base_generation'] + 1, 'transaction': build['transaction'],
                'selected': build['selected'], 'configuration': build['configuration'], 'build_sha256': build['sha256']}
    runtime = parse(read(root / RUNTIME))
    require(type(runtime) is dict and type(runtime.get('generation')) is int and runtime == expected,
            'Startup recipe projection changed or belongs to another build')
    properties = read(root / 'server.properties', 1048576)
    values = re.findall(rb'^\s*level-type\s*[=:]\s*([^\r\n]*)', properties, re.MULTILINE)
    large = 'shaped_horizon' in build['selected'] and build['configuration']['shaped_horizon']['preset'] == 'large_biomes'
    wanted = b'minecraft:large_biomes' if large else b'minecraft:normal'
    require(len(values) == 1 and values[0].strip().replace(b'\\:', b':') == wanted, 'Native generator preset projection changed')
    files = ore_projection(root, build)
    owned = owned_pack(world)
    target = safe(world / PACK)
    if not files:
        require(not target.exists(), 'Unselected ore projection still present')
    else:
        require(target.is_dir() and owned == {'schema': 'world_axioms_datapack_v1', 'build_sha256': build['sha256'], 'files': sorted(files)},
                'Ore projection manifest identity changed')
        for name, raw in files.items():
            require(read(target / name) == raw, 'Pinned ore projection bytes changed: ' + name)


def health(root, world):
    build = decode(read(world / SNAPSHOT))
    reset_identity(root, world, build, allow_promoted=True)
    frozen = decode(read(root / STATE / 'control/world-axiom-reset-v1.json'))
    require(frozen == build, 'World snapshot differs from committed frozen selection')
    validate_projection(root, world, build)
    value = parse(read(root / STATE / 'control/world-axiom-health-v1.json'))
    required = {'schema', 'status', 'lineage', 'transaction', 'generation', 'build_sha256'}
    require(type(value) is dict and required <= set(value) <= required | {'detail'}, 'Invalid world axiom health fields')
    if 'detail' in value:
        require(length(value['detail']) <= 512, 'Oversized health detail')
        value = {key: entry for key, entry in value.items() if key != 'detail'}
    counter(value['generation'])
    require(value == {'schema': 'world_axiom_health_v1', 'status': 'healthy', 'lineage': build['lineage'], 'transaction': build['transaction'],
                      'generation': build['base_generation'] + 1, 'build_sha256': build['sha256']}, 'World axiom health identity/digest mismatch')
    return build


def run(command, root, world, snapshot=None):
    root = safe(root)
    require(root.is_dir(), 'ROOT must exist')
    world = contained(root, world)
    if command in ('prepare', 'validate'):
        require(snapshot is not None, command + ' requires SNAPSHOT')
        snapshot = contained(root, snapshot)
        raw = read(snapshot)
        build = decode(raw)
        reset_identity(root, world, build, allow_promoted=command == 'validate')
        if command == 'validate':
            return
        require(not (world / 'level.dat').exists(), 'prepare requires a not-yet-created successor world')
        current = safe(world / SNAPSHOT)
        if current.exists():
            require(decode(read(current))['sha256'] == build['sha256'], 'Conflicting successor snapshot')
        project(root, world, build, raw)
    elif command == 'normal':
        current = safe(world / SNAPSHOT)
        build = decode(read(current)) if current.exists() else None
        identity, generation = lineage(root)
        if build is not None:
            require((identity, generation) == (build['lineage'], build['base_generation'] + 1) and build['source_world'] == world.name,
                    'Stale active-world snapshot identity or world binding')
        project(root, world, build)
    elif command == 'health':
        health(root, world)
    elif command == 'promote':
        health(root, world)
        atomic(root / STATE / 'world-axioms-v1.json', read(world / SNAPSHOT))
    else:
        raise ValueError('Unknown command')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=('prepare', 'validate', 'normal', 'health', 'promote'))
    parser.add_argument('root', type=Path)
    parser.add_argument('world', type=Path)
    parser.add_argument('snapshot', type=Path, nargs='?')
    args = parser.parse_args()
    require((args.command in ('prepare', 'validate')) == (args.snapshot is not None), 'prepare and validate require SNAPSHOT; other commands reject it')
    run(args.command, args.root, args.world, args.snapshot)
    print('world-axioms: ' + args.command + ' verified')


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError, KeyError, TypeError, AttributeError, zipfile.BadZipFile) as error:
        print('world-axioms: rejected: ' + str(error), file=sys.stderr)
        sys.exit(1)

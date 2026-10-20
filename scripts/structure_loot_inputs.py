#!/usr/bin/env python3
"""Pinned source inputs for table-owned structure loot contracts (no game launch).

All active Packwiz archives are inputs, not a retained diagnostic inventory. Missing
archives are reproducibly fetched and verified with the normal native-input helper.
The vanilla artifact is pinned independently; no local Forge cache is required.
"""
import gzip
import hashlib
import io
import json
from pathlib import Path
import re
import struct
import tomllib
import zipfile

from prepare_dev_inputs import ensure_pin

VANILLA = {
    'filename': 'minecraft-client-1.20.1.jar',
    'download': {'hash-format': 'sha1', 'hash': '0c3ec587af28e5a785c0b4a7b8a30f9a8f78f838',
                 'url': 'https://piston-data.mojang.com/v1/objects/0c3ec587af28e5a785c0b4a7b8a30f9a8f78f838/client.jar'},
}


def fingerprint(data):
    return hashlib.sha256(json.dumps(data, sort_keys=True, separators=(',', ':')).encode()).hexdigest()


class NbtReader:
    def __init__(self, data):
        self.stream = io.BytesIO(data)

    def number(self, fmt):
        return struct.unpack('>' + fmt, self.stream.read(struct.calcsize('>' + fmt)))[0]

    def string(self):
        return self.stream.read(self.number('H')).decode('utf-8', errors='replace')

    def payload(self, kind, depth=0):
        if depth > 128:
            raise ValueError('NBT nesting limit')
        if kind in range(1, 7):
            return self.number({1: 'b', 2: 'h', 3: 'i', 4: 'q', 5: 'f', 6: 'd'}[kind])
        if kind in (7, 11, 12):
            count = self.number('i')
            if count < 0:
                raise ValueError('negative NBT array')
            self.stream.seek(count * {7: 1, 11: 4, 12: 8}[kind], 1)
            if self.stream.tell() > len(self.stream.getbuffer()):
                raise ValueError('truncated NBT array')
            return None
        if kind == 8:
            return self.string()
        if kind == 9:
            element, count = self.number('B'), self.number('i')
            if not 0 <= count <= 10_000_000:
                raise ValueError('NBT list limit')
            return [self.payload(element, depth + 1) for _ in range(count)]
        if kind == 10:
            result = {}
            while True:
                element = self.number('B')
                if element == 0:
                    return result
                name = self.string()
                result[name] = self.payload(element, depth + 1)
        raise ValueError('invalid NBT tag')

    def root(self):
        if self.number('B') != 10:
            raise ValueError('NBT root must be compound')
        self.string()
        return self.payload(10)


def load(root, fetch=True, pack_tables=True, details=None):
    cache = Path.home() / '.cache/bc/packwiz-downloads'
    tables, sources, templates, code, injections = {}, {}, {}, {}, {}
    layers, worldgen_documents = {}, {}
    pins = [('vanilla', VANILLA)]
    pins += [(p.name, tomllib.loads(p.read_text())) for p in sorted((root / 'mods').glob('*.pw.toml'))]
    archives = []
    for owner, pin in pins:
        path = cache / ('vanilla' if owner == 'vanilla' else 'mods') / pin['filename']
        if fetch:
            path = ensure_pin(pin, path.parent)
        elif not path.is_file() or hashlib.new(pin['download']['hash-format'], path.read_bytes()).hexdigest() != pin['download']['hash']:
            raise ValueError('missing or changed pinned input: ' + str(path))
        archives.append((owner, path))
    archives += [(p.name, p) for p in sorted((root / 'mods').glob('*.jar'))]
    archives += [(p.name, p) for p in sorted((root / 'datapacks').glob('*.zip'))]

    def accept(name, raw, source):
        match = re.fullmatch(r'data/([^/]+)/loot_tables/(.+)\.json', name)
        if match:
            key = match[1] + ':' + match[2]
            tables[key] = json.loads(raw)
            sources[key] = source + '!' + name
            layers.setdefault(key, []).append({'source': sources[key], 'sha256': fingerprint(tables[key])})
        elif re.fullmatch(r'data/[^/]+/structures/.+\.nbt', name):
            templates[name] = (source, raw)
        elif '/loot_modifiers/' in name and name.endswith('.json'):
            injections[source + '!' + name] = json.loads(raw)
        elif '/worldgen/' in name and name.endswith('.json'):
            # Native Gson accepts comments in some upstream worldgen JSON.
            # Quoted resource literals are discovery witnesses, not a claim that
            # a processor/configured feature is enabled in the runtime registry.
            worldgen_documents[source + '!' + name] = re.findall(r'"(?:loot_table|loot_table_id|LootTable)"\s*:\s*"([a-z0-9_.-]+:[a-z0-9_./-]+)"', raw.decode('utf-8'))
        elif name.endswith('.class') and (b'LootTable' in raw or b'loot_table' in raw or b'chests/' in raw):
            code[source + '!' + name] = raw

    for owner, path in archives:
        with zipfile.ZipFile(path) as archive:
            for name in archive.namelist():
                if name.startswith('data/') or name.endswith('.class'):
                    accept(name, archive.read(name), owner)
    for data in [root / 'kubejs/data', *sorted((root / 'datapacks').glob('*/data'))]:
        for path in sorted(data.rglob('*')):
            if path.is_file():
                if not pack_tables and data == root / 'kubejs/data' and '/loot_tables/' in str(path):
                    continue
                accept('data/' + path.relative_to(data).as_posix(), path.read_bytes(), str(data.parent.relative_to(root)))

    references, prefilled = {}, []
    for name, (source, raw) in templates.items():
        if raw[:2] == b'\x1f\x8b':
            raw = gzip.decompress(raw)
        if b'LootTable' not in raw and b'Items' not in raw:
            continue
        nbt = NbtReader(raw).root()
        palettes = nbt.get('palettes') or [nbt.get('palette', [])]
        for block in nbt.get('blocks', []):
            tag = block.get('nbt', {})
            names = sorted({p[block['state']]['Name'] for p in palettes})
            witness = source + '!' + name + ':' + ','.join(names)
            if tag.get('LootTable'):
                key = tag['LootTable']
                if ':' not in key:
                    key = 'minecraft:' + key
                references.setdefault(key, set()).add(witness)
            elif tag.get('Items'):
                prefilled.append(witness)
    candidates = {k for k, v in tables.items() if v.get('type', '').removeprefix('minecraft:') == 'chest'
                  or re.match(r'[^:]+:chests?/', k)} | references.keys()
    # CHEST/path names discover candidates only; roles are finite reviewed input.
    # Follow explicit nested-table and active injection references so generic
    # helper tables cannot disappear merely because they omit a CHEST type.
    resource_references = {}
    for source, document in worldgen_documents.items():
        for key in set(strings(document)) & tables.keys():
            resource_references.setdefault(key, []).append(source)
            candidates.add(key)
    effective = effective_injections(injections)
    for modifier in effective.values():
        values = set(strings(modifier))
        if values & candidates:
            candidates.update(values & tables.keys())
    while True:
        nested = {child for key in candidates for child in table_references(tables.get(key, {}))}
        missing = nested - candidates
        if not missing:
            break
        candidates.update(missing)
    if details is not None:
        details['table_layers'] = {key: value for key, value in layers.items() if len(value) > 1}
        details['worldgen_consumers'] = {key: sorted(value) for key, value in resource_references.items()}
    return tables, sources, references, candidates, code, injections, prefilled


def strings(value):
    if isinstance(value, str):
        yield value
    elif isinstance(value, dict):
        for child in value.values():
            yield from strings(child)
    elif isinstance(value, list):
        for child in value:
            yield from strings(child)


def table_references(value):
    if isinstance(value, dict):
        if value.get('type', '').removeprefix('minecraft:') == 'loot_table' and isinstance(value.get('name'), str):
            yield value['name']
        for child in value.values():
            yield from table_references(child)
    elif isinstance(value, list):
        for child in value:
            yield from table_references(child)


def effective_injections(injections):
    # The authored Forge registry explicitly replaces upstream registration lists.
    # Definitions still use ordinary resource override priority; inactive upstream
    # definitions are not mistaken for active injections.
    registry = injections.get('kubejs!data/forge/loot_modifiers/global_loot_modifiers.json')
    if not registry or registry.get('replace') is not True:
        raise ValueError('review requires explicit authored global modifier registry')
    definitions = {}
    for source, value in injections.items():
        match = re.search(r'!data/([^/]+)/loot_modifiers/(.+)\.json$', source)
        if match and match[2] != 'global_loot_modifiers':
            definitions[match[1] + ':' + match[2]] = value
    if len(registry['entries']) != len(set(registry['entries'])):
        raise ValueError('duplicate active loot modifier')
    return {key: definitions[key] for key in registry['entries']}

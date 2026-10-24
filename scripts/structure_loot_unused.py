#!/usr/bin/env python3
"""Closed pinned-source evidence, not a registry-absence or runtime verdict.

Only two reviewed definitions may use this schema. Literal scanning is a finite
source witness, not an abstract interpreter for arbitrary dynamically assembled
IDs. Native selected resource/configuration checks remain separately mandatory.
"""
import gzip
import hashlib
import json
from pathlib import Path
import tomllib
import zipfile
from structure_loot_inputs import NbtReader, VANILLA, fingerprint

CONTEXTS = {
    'bloodmagic:chests/simple_dungeon/test_gems': {
        'owner': 'blood-magic.pw.toml',
        'class': 'wayoftime/bloodmagic/common/data/GeneratorLootTable$BMLootTables.class',
        'datagen_base': 'net/minecraft/data/loot/packs/VanillaChestLoot',
    },
    'rats:chest/plague_doctor_hut': {
        'owner': 'rats.pw.toml',
        'class': 'com/github/alexthe666/rats/data/loot/RatsChestLootTables.class',
        'datagen_base': 'net/minecraft/data/loot/LootTableSubProvider',
    },
}


def sha(data):
    return hashlib.sha256(data).hexdigest()


def scanned_entries(jar):
    """Exactly the class/data bytes searched for consumers, independent of ZIP metadata."""
    names = [name for name in jar.namelist() if name.endswith('.class') or name.startswith('data/')]
    assert len(names) == len(set(names)), 'duplicate scanned archive entry'
    for name in sorted(names):
        yield name, jar.read(name)


def compute(root):
    root = Path(root)
    cache = Path.home() / '.cache/bc/packwiz-downloads'
    pins = [('vanilla', VANILLA)] + [(p.name, tomllib.loads(p.read_text())) for p in sorted((root/'mods').glob('*.pw.toml'))]
    archives = []
    for owner, pin in pins:
        path = cache / ('vanilla' if owner == 'vanilla' else 'mods') / pin['filename']
        # The caller's load() prepares pins. This evidence independently rejects
        # stale/mismatched bytes rather than trusting a retained discovery output.
        digest = hashlib.new(pin['download']['hash-format'], path.read_bytes()).hexdigest()
        assert digest == pin['download']['hash'], ('changed pinned scan input', owner)
        archives.append((owner, path))
    archives += [(str(p.relative_to(root)), p) for p in sorted((root/'mods').glob('*.jar'))]
    archives += [(str(p.relative_to(root)), p) for p in sorted((root/'datapacks').glob('*.zip'))]
    hits = {key: {'classes': [], 'external_data': [], 'pack_inputs': []} for key in CONTEXTS}
    owners = {}
    scan_inputs = []
    for owner, path in archives:
        archive_sha = sha(path.read_bytes())
        scanned = []
        with zipfile.ZipFile(path) as jar:
            for name, raw in scanned_entries(jar):
                # Bind every searched byte/name, not ZIP timestamps or unsearched source metadata.
                scanned.append([name, sha(raw)])
                is_class = name.endswith('.class')
                if name.endswith('.nbt') and raw[:2] == b'\x1f\x8b':
                    raw = gzip.decompress(raw)
                for key, context in CONTEXTS.items():
                    namespace, table = key.split(':', 1)
                    own_resource = f'data/{namespace}/loot_tables/{table}.json'
                    if owner == context['owner'] and name == context['class']:
                        assert context['datagen_base'].encode() in raw
                        owners.setdefault(key, {}).update(owner=owner, archive_filename=path.name,
                            archive_sha256=archive_sha, class_path=name, class_sha256=sha(raw),
                            datagen_base=context['datagen_base'])
                    if owner == context['owner'] and name == own_resource:
                        owners.setdefault(key, {}).update(definition_resource=own_resource, definition_sha256=sha(raw))
                        continue  # Its own random_sequence is not a consumer.
                    needles = [key.encode(), table.encode()]
                    if is_class:
                        needles.append(table.rsplit('/', 1)[-1].encode())
                    if any(needle in raw for needle in needles):
                        field = 'classes' if is_class else 'external_data'
                        hits[key][field].append({'source': owner+'!'+name, 'sha256': sha(raw)})
        scan_inputs.append([owner, fingerprint(scanned)])
    for directory in ['kubejs', 'config', 'defaultconfigs', 'datapacks', 'global_packs']:
        for path in sorted((root/directory).rglob('*')):
            if not path.is_file():
                continue
            raw = path.read_bytes()
            if path.suffix == '.nbt' and raw[:2] == b'\x1f\x8b':
                raw = gzip.decompress(raw)
            for key in CONTEXTS:
                if any(needle in raw for needle in [key.encode(), key.split(':',1)[1].encode()]):
                    hits[key]['pack_inputs'].append({'source': str(path.relative_to(root)), 'sha256': sha(raw)})
    records = []
    for key, context in CONTEXTS.items():
        expected = context['owner']+'!'+context['class']
        assert [hit['source'] for hit in hits[key]['classes']] == [expected], ('new live/unknown code caller', key, hits[key]['classes'])
        assert not hits[key]['external_data'] and not hits[key]['pack_inputs'], ('new source consumer', key, hits[key])
        pin = dict(pins)[context['owner']]
        row = dict(id=key, context='reviewed-unused-definition', owner_pin_hash=pin['download']['hash'],
                   owner_pin_hash_format=pin['download']['hash-format'], **owners[key],
                   exclusive_datagen_literals=hits[key]['classes'], external_data_consumers=[], pack_configuration_consumers=[],
                   live_known_code_callers=[], runtime_verdict='PENDING native selected-consumer checks; loaded table is not activity')
        if key.startswith('rats:'):
            with zipfile.ZipFile(cache/'mods'/pin['filename']) as jar:
                resource='data/rats/structures/plague_doctor_hut.nbt'; raw=jar.read(resource)
            nbt=NbtReader(gzip.decompress(raw)).root()
            chest=[block for block in nbt['blocks'] if block.get('nbt',{}).get('id')=='minecraft:chest']
            assert len(chest)==1 and chest[0]['pos']==[3,4,6]
            assert nbt['palette'][chest[0]['state']]['Name']=='minecraft:chest'
            assert chest[0]['nbt'].get('Items')==[] and 'LootTable' not in chest[0]['nbt']
            row['actual_template_witness']=dict(resource=resource, sha256=sha(raw), position=[3,4,6],
                block='minecraft:chest', block_entity='minecraft:chest', items=[], loot_table_present=False)
        records.append(row)
    # Archaeology is an exact separate nonphysical context, never a substring
    # exception for mod:suspicious_chest or any other physical container.
    pin=dict(pins)['goety.pw.toml']
    with zipfile.ZipFile(cache/'mods'/pin['filename']) as jar:
        resource='data/goety/structures/ruined_monastery/rubbles_4.nbt'; raw=jar.read(resource)
    nbt=NbtReader(gzip.decompress(raw)).root()
    blocks=[b for b in nbt['blocks'] if b.get('nbt',{}).get('LootTable')=='goety:archaelogy/ruined_monastery']
    assert len(blocks)==1
    block=blocks[0]; state=nbt['palette'][block['state']]['Name']
    assert state=='minecraft:suspicious_gravel' and block['nbt']['id']=='minecraft:brushable_block'
    return dict(schema='bc.structure_loot_unused.v2', scan_identity='sorted-class-and-data-entry-sha256',
        scan_input_sha256=fingerprint(scan_inputs),
        limits='Automatic structure-container consumers only, not arbitrary administrator /loot commands. Closed pinned source contexts only; literal search cannot prove arbitrary dynamic code impossible. Positive native selected consumers invalidate unused classification. Registry existence or absence is not a consumer verdict.',
        definitions=records, nonphysical_context=dict(id='goety:archaelogy/ruined_monastery',
            template='goety:ruined_monastery/rubbles_4', resource=resource, template_sha256=sha(raw),
            block=state, block_entity='minecraft:brushable_block', position=block['pos']))


if __name__ == '__main__':
    import sys
    print(json.dumps(compute(Path(sys.argv[1]) if len(sys.argv)>1 else Path.cwd()),indent=2))

#!/usr/bin/env python3
"""Bind the audited bc-interface.3 recipe to one generated LOCAL_ONLY package pair.

Usage: prepare PACK RELEASE PROVIDER_SHA256; apply PACK RELEASE CLIENT_ZIP SERVER_TREE PROVIDER_SHA256.
No source mod, Packwiz descriptor, cache, or accepted archive is modified.
"""
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import shutil
import stat
import subprocess
import sys
import tempfile
import tomllib
from urllib.parse import urlparse
from urllib.request import Request, urlopen
import zipfile

EPIC = 'epic x parcool-forge-20.12.0.1-1.20.1.jar'
VS = 'valkyrienskies-120-2.4.11.jar'
PROVENANCE = 'better-content-interface-overrides.json'
MAX_INPUT = 128 * 1024 * 1024


def fail(message):
    raise ValueError(message)


def require(ok, message):
    if not ok:
        fail(message)


def unique(pairs):
    result = {}
    for key, value in pairs:
        require(key not in result, f'duplicate JSON key: {key}')
        result[key] = value
    return result


def read_json(path):
    return json.loads(path.read_bytes(), object_pairs_hook=unique)


def sha(path, algorithm='sha256'):
    digest = hashlib.new(algorithm)
    with path.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(chunk)
    return digest.hexdigest()


def safe(path, *, regular=False):
    path = Path(path).absolute()
    require('..' not in path.parts, f'escaping path: {path}')
    for part in (path, *path.parents):
        require(not part.is_symlink(), f'symlink packaging path: {part}')
    if regular:
        require(path.is_file(), f'missing regular file: {path}')
    return path


def digest_matches(path, expected, algorithm='sha256'):
    require(isinstance(expected, str) and len(expected) == hashlib.new(algorithm).digest_size * 2
            and sha(safe(path, regular=True), algorithm) == expected,
            f'{algorithm} mismatch: {path}')


def descriptor(root, row):
    path = safe(root / 'mods' / row['manifest'], regular=True)
    item = tomllib.loads(path.read_text())
    require(item.get('filename') == row['input'] and item.get('side', 'both') == 'both',
            f'original descriptor changed: {path}')
    download = item.get('download', {})
    identity = item.get('update', {}).get('curseforge', {})
    require(download.get('hash-format') == 'sha1' and download.get('hash') == row['input_sha1']
            and identity.get('project-id') == row['project_id'] and identity.get('file-id') == row['file_id'],
            f'original descriptor pin changed: {path}')


def contract(root, provider_sha):
    root = safe(root)
    require(root.is_dir() and len(provider_sha) == 64 and all(c in '0123456789abcdef' for c in provider_sha),
            'explicit fresh provider SHA-256 is required')
    recipe_path = safe(root / 'config/better-content-interface-runtime-overrides.json', regular=True)
    recipe = read_json(recipe_path)
    require(recipe.get('schema') == 'bc.interface_runtime_archive_overrides.v1'
            and recipe.get('revision') == 'bc-interface.3'
            and recipe.get('producer') == 'better-compat-fixes'
            and recipe.get('provider_artifact') == 'better-compat-fixes-0.1.9.jar',
            'unsupported interface recipe')
    source = safe(root.parent / 'mod_source/better-compat-fixes')
    paths = {}
    for key in ('spec', 'builder', 'legacy_builder'):
        item = recipe[key]
        rel = PurePosixPath(item['path'])
        require(not rel.is_absolute() and '..' not in rel.parts, f'unsafe {key} path')
        paths[key] = safe(source.joinpath(*rel.parts), regular=True)
        digest_matches(paths[key], item['sha256'])
    spec = read_json(paths['spec'])
    require(spec.get('schema') == 'bc.upstream_interface_runtime_overrides.v1'
            and spec.get('revision') == 'bc-interface.3', 'unsupported source-owned spec')
    rows = recipe['artifacts']
    require(isinstance(rows, list) and len(rows) == 2 and {r['input'] for r in rows} == {EPIC, VS},
            'interface recipe must contain exact Epic and VS inputs')
    spec_rows = {r['input']: r for r in spec['artifacts']}
    for row in rows:
        base = spec_rows[row['input']]
        require(all(row[key] == base[key] for key in ('output', 'manifest', 'project_id', 'file_id'))
                and row['input_sha1'] == base['sha1'] and row['input_sha256'] == base['sha256']
                and row['resource']['path'] == base['patches'][0]['path']
                and row['resource']['before_sha256'] == base['patches'][0]['sha256']
                and row['resource']['move_to_client'] == base['patches'][0]['move_to_client']
                and row['interface_transfer'] == base.get('interface_transfer')
                and base['redistribution'] == 'LOCAL_ONLY_NO_GRANT_BY_THIS_RECIPE',
                f'recipe/source mismatch: {row["input"]}')
    require(recipe.get('publication') == 'LOCAL_ONLY_NOT_AUTHORIZED'
            and recipe.get('runtime_acceptance') == 'NOT_EVALUATED', 'unsafe publication or runtime assertion')
    provider = safe(root / 'mods' / recipe['provider_artifact'], regular=True)
    digest_matches(provider, provider_sha)
    vs_row = next(r for r in rows if r['input'] == VS)
    digest_matches(root / 'mods' / vs_row['output'], vs_row['output_sha256'])
    require(not (root / 'mods' / EPIC.replace('.jar', '-bc-interface.3.jar')).exists(),
            'derived Epic must never enter source mods')
    descriptor(root, next(r for r in rows if r['input'] == EPIC))
    # The bundled VS and provider bytes must be the exact Packwiz-indexed inputs.
    index = tomllib.loads(safe(root / 'index.toml', regular=True).read_text())
    require(all(sum(r['file'] == 'mods/' + name for r in index['files']) == 1
                for name in (vs_row['output'], provider.name)),
            'Packwiz index has duplicate or missing staged interface JAR')
    indexed = {r['file']: r['hash'] for r in index['files']}
    require(indexed.get('mods/' + vs_row['output']) == vs_row['output_sha256']
            and indexed.get('mods/' + provider.name) == provider_sha,
            'Packwiz index differs from staged VS/provider bytes')
    return recipe, paths, provider


def obtain_input(row, inputs, cache_root):
    target = safe(inputs / row['input'])
    cache = safe(cache_root / 'mods' / row['input'])
    if cache.is_file() and sha(cache) == row['input_sha256'] and sha(cache, 'sha1') == row['input_sha1']:
        shutil.copyfile(cache, target)
    else:
        spec_url = row['input_url']
        require(spec_url.startswith('https://mediafilez.forgecdn.net/files/'), 'unreviewed input URL')
        request = Request(spec_url, headers={'User-Agent': 'BetterContent-local-interface-package/1'})
        with urlopen(request, timeout=120) as response, target.open('xb') as out:
            final = urlparse(response.geturl())
            require(final.scheme == 'https' and final.hostname in
                    ('mediafilez.forgecdn.net', 'media.forgecdn.net', 'edge.forgecdn.net'),
                    'unreviewed upstream redirect')
            size = 0
            while chunk := response.read(1024 * 1024):
                size += len(chunk)
                require(size <= MAX_INPUT, 'upstream input exceeds audited bound')
                out.write(chunk)
    digest_matches(target, row['input_sha256'])
    digest_matches(target, row['input_sha1'], 'sha1')


def verify_receipt(recipe, paths, release, provider, provider_sha):
    derived = safe(release / 'interface-derived')
    receipt_path = safe(derived / 'receipt.json', regular=True)
    receipt = read_json(receipt_path)
    require(receipt == recipe['receipt'], 'audited builder receipt differs from pinned recipe')
    require(receipt['spec_sha256'] == recipe['spec']['sha256']
            and receipt['builder_sha256'] == recipe['builder']['sha256']
            and receipt['legacy_builder_sha256'] == recipe['legacy_builder']['sha256'],
            'receipt source hashes mismatch')
    digest_matches(provider, provider_sha)
    for row in recipe['artifacts']:
        output = safe(derived / row['output'], regular=True)
        digest_matches(output, row['output_sha256'])
        digest_matches(release / 'interface-inputs' / row['input'], row['input_sha256'])
        digest_matches(release / 'interface-inputs' / row['input'], row['input_sha1'], 'sha1')
        expected = next(r for r in receipt['artifacts'] if r['input'] == row['input'])
        require(expected['output'] == row['output'] and expected['output_sha256'] == row['output_sha256']
                and expected['input_sha256'] == row['input_sha256']
                and expected['redistribution'] == 'LOCAL_ONLY_NO_GRANT_BY_THIS_RECIPE', 'receipt artifact mismatch')
    selection = read_json(safe(release / 'interface-selection.json', regular=True))
    require(selection == {'schema': 'bc.local_interface_package_selection.v1',
                          'recipe_sha256': sha(safe(release.parent.parent / 'config/better-content-interface-runtime-overrides.json', regular=True)),
                          'provider_sha256': provider_sha, 'receipt_sha256': sha(receipt_path),
                          'outputs': {r['output']: r['output_sha256'] for r in recipe['artifacts']},
                          'publication': 'LOCAL_ONLY_NOT_AUTHORIZED'}, 'local selection receipt mismatch')
    return selection


def prepare(root, release, provider_sha):
    root, release = safe(root), safe(release)
    require(release.is_dir() and release.parent == root / 'dist', 'fresh distribution directory required')
    recipe, paths, provider = contract(root, provider_sha)
    inputs = release / 'interface-inputs'
    derived = release / 'interface-derived'
    require(not inputs.exists() and not derived.exists() and not (release / 'interface-selection.json').exists(),
            'interface attempt must be fresh; no automatic retry')
    inputs.mkdir()
    spec_rows = {r['input']: r for r in read_json(paths['spec'])['artifacts']}
    cache_root = Path(os.environ.get('BC_PACKAGE_ARTIFACT_CACHE', Path.home() / '.cache/bc/packwiz-downloads')).expanduser()
    for row in recipe['artifacts']:
        pin = dict(row, input_url=spec_rows[row['input']]['url'])
        obtain_input(pin, inputs, cache_root)
    subprocess.run([sys.executable, '-B', str(paths['builder']), '--inputs', str(inputs),
                    '--output', str(derived), '--provider-jar', str(provider)], check=True)
    receipt_path = safe(derived / 'receipt.json', regular=True)
    require(read_json(receipt_path) == recipe['receipt'], 'audited builder receipt differs from pinned recipe')
    for row in recipe['artifacts']:
        digest_matches(derived / row['output'], row['output_sha256'])
    selection = {'schema': 'bc.local_interface_package_selection.v1',
                 'recipe_sha256': sha(root / 'config/better-content-interface-runtime-overrides.json'),
                 'provider_sha256': provider_sha, 'receipt_sha256': sha(receipt_path),
                 'outputs': {r['output']: r['output_sha256'] for r in recipe['artifacts']},
                 'publication': 'LOCAL_ONLY_NOT_AUTHORIZED'}
    (release / 'interface-selection.json').write_text(json.dumps(selection, indent=2) + '\n')
    verify_receipt(recipe, paths, release, provider, provider_sha)
    print('verified local bc-interface.3 derivation; no publication permission')


def safe_zip_member(info, seen):
    name = info.filename
    parts = name.rstrip('/').split('/')
    require(name not in seen and not name.startswith('/') and '\\' not in name and ':' not in name
            and all(part not in ('', '.', '..') for part in parts)
            and all(ord(c) >= 32 for c in name)
            and not stat.S_ISLNK(info.external_attr >> 16), f'unsafe or duplicate ZIP entry: {name!r}')
    seen.add(name)


def preflight_client(archive, recipe, provider_sha):
    seen = set()
    infos = archive.infolist()
    for info in infos:
        safe_zip_member(info, seen)
    require({'manifest.json', 'better-content-artifacts.json'} <= seen, 'client export lacks manifest or lock')
    manifest = json.loads(archive.read('manifest.json'), object_pairs_hook=unique)
    lock = json.loads(archive.read('better-content-artifacts.json'), object_pairs_hook=unique)
    require(lock.get('schema') == 'bc.client_artifacts.v1'
            and isinstance(lock.get('artifacts'), list), 'client artifact lock schema')
    require(isinstance(manifest.get('files'), list), 'client manifest files')
    prefix = manifest.get('overrides')
    require(isinstance(prefix, str) and prefix and not prefix.startswith('/')
            and all(p not in ('', '.', '..') for p in prefix.split('/'))
            and '\\' not in prefix and ':' not in prefix and all(ord(c) >= 32 for c in prefix),
            'unsafe client overrides prefix')
    epic = next(r for r in recipe['artifacts'] if r['input'] == EPIC)
    vs = next(r for r in recipe['artifacts'] if r['input'] == VS)
    identity = (epic['project_id'], epic['file_id'])
    require(sum((f.get('projectID'), f.get('fileID')) == identity for f in manifest['files']) == 1,
            'Epic original must occur exactly once in client manifest')
    require(sum((a.get('project_id'), a.get('file_id')) == identity for a in lock['artifacts']) == 1,
            'Epic original must occur exactly once in client artifact lock')
    require(sum(a.get('path') == 'mods/' + EPIC for a in lock['artifacts']) == 1,
            'Epic original path must occur exactly once in client artifact lock')
    original_lock = next(a for a in lock['artifacts'] if (a.get('project_id'), a.get('file_id')) == identity)
    require(original_lock.get('path') == 'mods/' + EPIC
            and original_lock.get('hash_format') == 'sha1'
            and original_lock.get('hash') == epic['input_sha1'], 'Epic remote lock pin changed')
    vs_name = f'{prefix}/mods/{vs["output"]}'
    require(vs_name in seen and hashlib.sha256(archive.read(vs_name)).hexdigest() == vs['output_sha256'],
            'bundled VS is absent or differs from pinned corrected bytes')
    provider_name = f'{prefix}/mods/{recipe["provider_artifact"]}'
    require(provider_name in seen and hashlib.sha256(archive.read(provider_name)).hexdigest() == provider_sha,
            'client provider differs from freshly staged provider')
    require(f'{prefix}/mods/{epic["output"]}' not in seen
            and f'{prefix}/mods/{EPIC}' not in seen
            and f'{prefix}/mods/{VS}' not in seen, 'client export already contains duplicate interface artifact')
    require(not any((f.get('projectID'), f.get('fileID')) == (vs['project_id'], vs['file_id'])
                    for f in manifest['files']), 'VS original remote identity remains in client manifest')
    require(not any(a.get('path') in ('mods/' + vs['input'], 'mods/' + vs['output']) for a in lock['artifacts']),
            'VS original/derived remote lock collision')
    return infos, manifest, lock, prefix


def preflight_server(server, recipe, provider_sha):
    mods = safe(server / 'mods')
    epic = next(r for r in recipe['artifacts'] if r['input'] == EPIC)
    vs = next(r for r in recipe['artifacts'] if r['input'] == VS)
    digest_matches(mods / EPIC, epic['input_sha1'], 'sha1')
    digest_matches(mods / vs['output'], vs['output_sha256'])
    digest_matches(mods / recipe['provider_artifact'], provider_sha)
    safe(mods / epic['manifest'], regular=True)
    require(not (mods / epic['output']).exists() and not (mods / VS).exists()
            and not (server / PROVENANCE).exists(), 'server Epic/VS staging is ambiguous')


def apply(root, release, client, server, provider_sha):
    root, release, client, server = map(safe, (root, release, client, server))
    require(release.parent == root / 'dist' and client.parent == release / 'client'
            and server == release / 'server/server-tree/better-content-server',
            'apply targets must be this generated local candidate')
    recipe, paths, provider = contract(root, provider_sha)
    selection = verify_receipt(recipe, paths, release, provider, provider_sha)
    epic = next(r for r in recipe['artifacts'] if r['input'] == EPIC)
    preflight_server(server, recipe, provider_sha)
    with zipfile.ZipFile(safe(client, regular=True)) as archive:
        infos, manifest, lock, prefix = preflight_client(archive, recipe, provider_sha)
        manifest['files'] = [f for f in manifest['files']
                             if (f.get('projectID'), f.get('fileID')) != (epic['project_id'], epic['file_id'])]
        lock['artifacts'] = [a for a in lock['artifacts']
                             if (a.get('project_id'), a.get('file_id')) != (epic['project_id'], epic['file_id'])]
        epic_receipt = next(r for r in recipe['receipt']['artifacts'] if r['input'] == EPIC)
        provenance = {**selection, 'receipt': recipe['receipt'], 'license': epic_receipt['license'],
                      'redistribution': epic_receipt['redistribution']}
        skipped = {f'{prefix}/mods/{epic["manifest"]}'}
        added = f'{prefix}/mods/{epic["output"]}'
        require(PROVENANCE not in {i.filename for i in infos}, 'provenance already in client archive')
        fd, temporary_name = tempfile.mkstemp(prefix='.interface-', suffix='.zip', dir=client.parent)
        os.close(fd)
        temporary = Path(temporary_name)
        try:
            with zipfile.ZipFile(temporary, 'w') as output:
                output.comment = archive.comment
                for info in infos:
                    if info.filename in skipped:
                        continue
                    data = (json.dumps(manifest, indent=2) + '\n').encode() if info.filename == 'manifest.json' else (
                        (json.dumps(lock, indent=2) + '\n').encode() if info.filename == 'better-content-artifacts.json' else archive.read(info))
                    output.writestr(info, data)
                output.write(release / 'interface-derived' / epic['output'], added, compress_type=zipfile.ZIP_DEFLATED)
                output.writestr(PROVENANCE, json.dumps(provenance, indent=2) + '\n')
            with zipfile.ZipFile(temporary) as check:
                require(sum(i.filename == added for i in check.infolist()) == 1, 'derived Epic ZIP member collision')
                require(hashlib.sha256(check.read(added)).hexdigest() == epic['output_sha256'],
                        'derived Epic ZIP hash mismatch')
            os.replace(temporary, client)
        finally:
            temporary.unlink(missing_ok=True)
    mods = server / 'mods'
    (mods / EPIC).unlink()
    (mods / epic['manifest']).unlink()
    shutil.copyfile(release / 'interface-derived' / epic['output'], mods / epic['output'])
    digest_matches(mods / epic['output'], epic['output_sha256'])
    (server / PROVENANCE).write_text(json.dumps(provenance, indent=2) + '\n')
    print('applied one local Epic replacement to client archive and server tree; VS remained single')


def main(argv):
    if len(argv) == 5 and argv[1] == 'prepare':
        prepare(Path(argv[2]), Path(argv[3]), argv[4])
    elif len(argv) == 7 and argv[1] == 'apply':
        apply(Path(argv[2]), Path(argv[3]), Path(argv[4]), Path(argv[5]), argv[6])
    else:
        fail('usage: package_interface_overrides.py prepare PACK RELEASE PROVIDER_SHA256 | apply PACK RELEASE CLIENT_ZIP SERVER_TREE PROVIDER_SHA256')


if __name__ == '__main__':
    try:
        main(sys.argv)
    except (OSError, ValueError, KeyError, TypeError, subprocess.CalledProcessError,
            zipfile.BadZipFile, tomllib.TOMLDecodeError) as error:
        raise SystemExit(f'package failed: local interface overrides: {error}') from error

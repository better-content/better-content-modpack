#!/usr/bin/env python3
"""Fail-closed, nonshipping materialization of the exact main Build22 Smoke pair.
Never imports worlds/credentials, overwrites an attempt, follows a symlink, or edits ZIPs.
Synthetic parent initialization and all-24 succession require separate evidence.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import stat
import sys
import zipfile
from datetime import datetime, timezone

PACK = Path(__file__).absolute().parents[1]
ROOT = PACK
RUNTIMES = PACK / 'generated/world-axiom-runtimes'
RELEASE = PACK / 'dist/build-26'
SERVER = RELEASE / 'server/better-content.zip'
CLIENT = RELEASE / 'client/better-content.zip'
SERVER_SHA = 'c18aebc94e5b5d14860c5ef72725f65003d6f313c37295f7833216ec40d0c6fa'
CLIENT_SHA = 'e6bad4c0e1598dfd917e8494d3640a18b11d8268e7dcf26687c7743596145af9'
FEATURES = {'better-world-management': 'better-world-management-0.1.0.jar',
            'better-celestial-events': 'better-celestial-events-0.1.0.jar',
            'better-village-fortifications': 'better-village-fortifications-1.0.0.jar',
            'better-spirit-commerce': 'better-spirit-commerce-1.0.1.jar'}
FORBIDDEN = {'world', 'saves', 'logs', 'crash-reports', '.better_world_management', '.prestige',
             'ops.json', 'whitelist.json', 'usercache.json', 'banned-ips.json', 'banned-players.json',
             'credentials.json', 'accounts.json', 'session.lock'}
MAX_ENTRY = 2 * 1024**3
MAX_TOTAL = 12 * 1024**3
SCHEMA = 'world_axioms_main_build26_materialization_v1'


def need(value, reason):
    if not value:
        raise ValueError(reason)


def safe(path):
    path = Path(path).absolute()
    need('..' not in path.parts and not any(part.is_symlink() for part in (path, *path.parents)), 'unsafe symlink/parent path: ' + str(path))
    return path


def sha(path):
    with safe(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def strict(raw):
    def pairs(items):
        out = {}
        for key, value in items:
            need(key not in out, 'duplicate JSON key: ' + key)
            out[key] = value
        return out
    return json.loads(raw, object_pairs_hook=pairs, parse_constant=lambda s: need(False, 'nonfinite JSON constant: '+s))


def fsync_dir(path):
    fd = os.open(path, os.O_RDONLY | os.O_DIRECTORY)
    try: os.fsync(fd)
    finally: os.close(fd)


def receipt(path, value):
    partial = path.with_name('.'+path.name+'.partial')
    need(not partial.exists(), 'existing receipt partial')
    with partial.open('xb') as stream:
        stream.write((json.dumps(value, sort_keys=True, indent=2)+'\n').encode())
        stream.flush(); os.fsync(stream.fileno())
    os.replace(partial, path)
    fsync_dir(path.parent)


def validate_zip(archive):
    entries, total = [], 0
    seen = set()
    for member in archive.infolist():
        raw = member.filename
        parts = PurePosixPath(raw).parts
        need(raw not in seen and parts and parts[0] == 'better-content-server' and '..' not in parts
             and '\\' not in raw and ':' not in raw and not raw.startswith('/') and not member.flag_bits & 1,
             'unsafe/duplicate/encrypted ZIP member: '+raw)
        seen.add(raw)
        need(not any(part in FORBIDDEN for part in parts[1:]), 'world/credential/state in ZIP: '+raw)
        need(stat.S_IFMT(member.external_attr >> 16) != stat.S_IFLNK, 'ZIP symlink: '+raw)
        need(member.file_size <= MAX_ENTRY, 'oversized ZIP member: '+raw)
        total += member.file_size
        need(total <= MAX_TOTAL, 'oversized ZIP expanded total')
        if not member.is_dir():
            need(len(parts) > 1 and raw == '/'.join(parts), 'malformed ZIP member: '+raw)
            entries.append((member, '/'.join(parts[1:])))
    return entries, total


def admit():
    ready_path = safe(RELEASE / 'ready.json')
    ready = strict(ready_path.read_bytes())
    need(ready.get('schema') == 'bc.release_ready.v4' and ready.get('status') == 'passed', 'ready schema/status mismatch')
    need(ready.get('tiers', {}).get('smoke', {}).get('status') == 'passed', 'not Smoke passed')
    need(ready.get('candidates', {}).get('server') == str(SERVER)
         and ready['candidates'].get('server_sha256') == SERVER_SHA
         and ready['candidates'].get('client') == str(CLIENT)
         and ready['candidates'].get('client_sha256') == CLIENT_SHA, 'not exact pinned main Build26 pair')
    package_ready = safe(RELEASE / 'package-ready.json')
    need(sha(package_ready) == ready.get('package_ready_sha256'), 'package marker changed')
    need(sha(SERVER) == SERVER_SHA and sha(CLIENT) == CLIENT_SHA, 'ZIP changed')
    modules = strict(safe(PACK / 'gradle/active-custom-mods.json').read_bytes())
    need(modules.get('schema') == 'bc.active_custom_mods.v1' and len(modules.get('mods', [])) == 49,
         'not the exact 49-mod candidate')
    names = {m['repository']: m['artifact'] for m in modules['mods']}
    need(len(names) == 49 and all(names.get(n) == artifact for n, artifact in FEATURES.items()), 'feature manifest drift')
    source_hashes = {n: sha(PACK.parent / 'mod_source' / n / 'build/libs' / artifact)
                     for n, artifact in FEATURES.items()}
    return {'ready_sha256':sha(ready_path),'package_ready_sha256':sha(package_ready),
            'custom_manifest_sha256':sha(PACK / 'gradle/active-custom-mods.json'), 'source_build_sha256':source_hashes}


def materialize(destination):
    destination = safe(destination)
    need(destination.parent == safe(RUNTIMES) and
         re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._-]{0,127}',destination.name), 'runtime must be fresh direct nonce')
    need(not destination.exists(), 'never reuse a runtime, even failed')
    pins = admit()  # all trust checks BEFORE creating a destination
    with zipfile.ZipFile(safe(SERVER)) as archive:
        entries, total = validate_zip(archive)
        files = {rel for _, rel in entries}
        need(len(files) == len(entries) and 'run.sh' in files and 'run-forge.sh' in files
             and 'better-world-management-server.sh' in files and 'world-axioms.py' in files
             and 'server.properties' in files and 'user_jvm_args.txt' in files
             and 'kubejs/config/crafting_policy.json' in files, 'incomplete production server ZIP')
        need(all('mods/'+jar in files for jar in FEATURES.values()), 'feature runtime JAR missing')
        state = {'schema':SCHEMA,'status':'preparing','native_acceptance':False,
                 'destination':str(destination),'accepted_zip':str(SERVER),
                 'accepted_zip_expected_sha256':SERVER_SHA,'client_zip':str(CLIENT),
                 'client_zip_expected_sha256':CLIENT_SHA,'created_utc':datetime.now(timezone.utc).isoformat(),
                 'pinned_release':pins,'expected_archive_files':len(entries), 'expected_archive_bytes':total,
                 'eula':'explicit_user_permission_flag','final_files':{},'overrides':[],
                 'warning':'Main Build22 Smoke input; materialization alone is not all-24 succession acceptance.'}
        destination.parent.mkdir(mode=0o700, exist_ok=True)
        need(safe(destination.parent) == safe(RUNTIMES), 'runtime parent changed')
        fsync_dir(destination.parent.parent)
        destination.mkdir(mode=0o700)
        fsync_dir(destination.parent)
        try:
            for member, rel in entries:
                target = safe(destination / rel)
                need(target.is_relative_to(destination), 'escaped runtime')
                target.parent.mkdir(parents=True, exist_ok=True)
                digest = hashlib.sha256()
                copied = 0
                with archive.open(member) as source, target.open('xb') as output:
                    while block := source.read(1024*1024):
                        copied += len(block)
                        need(copied <= MAX_ENTRY, 'oversized member while extracting')
                        digest.update(block)
                        output.write(block)
                    output.flush(); os.fsync(output.fileno())
                need(copied == member.file_size, 'ZIP member size changed')
                os.chmod(target, 0o755 if rel in ('run.sh','run-forge.sh','better-world-management-server.sh') else 0o600)
                state['final_files'][rel] = {'sha256':digest.hexdigest(),'bytes':copied,'origin':'pinned_main_build26_server_zip'}
            for repo, artifact in FEATURES.items():
                rel = 'mods/'+artifact
                need(state['final_files'][rel]['sha256'] == pins['source_build_sha256'][repo], 'built feature JAR mismatch: '+repo)
                state['overrides'].append({'repository':repo,'path':rel,'sha256':pins['source_build_sha256'][repo],
                                           'mode':'already_packaged_exact_source_build_no_replacement'})
            # The package supplies only eula=false; never import a preaccepted world or user EULA.
            eula = destination / 'eula.txt'
            need(eula.is_file() and eula.read_bytes() == b'eula=false\n', 'packaged EULA is not explicit false')
            state['packaged_eula_original_sha256'] = sha(eula)
            with eula.open('wb') as output:
                output.write(b'eula=true\n');output.flush();os.fsync(output.fileno())
            state['final_files']['eula.txt'] = {'sha256':sha(eula),'bytes':len(b'eula=true\n'),
                                                'origin':'explicit_disposable_test_permission_from_packaged_false'}
            need(sha(SERVER) == SERVER_SHA and sha(CLIENT) == CLIENT_SHA, 'ZIP changed during materialization')
            for rel, info in state['final_files'].items():
                need(sha(destination/rel) == info['sha256'], 'copied file changed: '+rel)
            state['status'] = 'materialized_unaccepted'
            receipt(destination/'runtime-inputs-v1.json',state)
            print(json.dumps({'status':state['status'],'destination':str(destination),'files':len(state['final_files'])}))
        except BaseException as error:
            state['status']='failed';state['error']=repr(error)
            receipt(destination/'runtime-inputs-v1.json',state)
            raise


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--destination',required=True,type=Path)
    args=parser.parse_args()
    try: materialize(args.destination)
    except Exception as error:
        print('candidate materialization FAILED; '+('partial retained' if args.destination.exists() else 'no runtime created')+': '+repr(error),file=sys.stderr)
        raise SystemExit(1)

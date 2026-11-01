#!/usr/bin/env python3
"""Read-only main Build22 materialization preflight for a fresh all-24 source.

This does not grant a native pass, create a world, or synthesize reset history.
"""
from pathlib import Path, PurePosixPath
import hashlib
import importlib.util
import json
import os
import re
import uuid

ROOT = Path(__file__).absolute().parents[1]
JAVA_HOME = Path('/home/dev/.sdkman/candidates/java/17.0.17-tem')
MATERIALIZER_SHA = '9bedf246b97ef7c8255fd8a1ad725e6e8207a7963ceba7c687df69fcfeb75f7a'


def need(condition, message):
    if not condition:
        raise ValueError(message)


def safe(path):
    path = Path(path)
    need('..' not in path.parts, 'Parent traversal forbidden')
    path = path.absolute()
    need(not any(part.is_symlink() for part in (path, *path.parents)), 'Symlink forbidden: ' + str(path))
    return path


def sha(path):
    with safe(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def pairs(items):
    result = {}
    for key, value in items:
        need(key not in result, 'Duplicate JSON field: ' + key)
        result[key] = value
    return result


def sync_dir(path):
    fd = os.open(safe(path), os.O_RDONLY | os.O_DIRECTORY)
    try:
        os.fsync(fd)
    finally:
        os.close(fd)


def publish(path, data):
    """No replacement or retrospective repair; retain partials after interruption."""
    path = safe(path)
    partial = path.with_name(path.name + '.' + uuid.uuid4().hex + '.partial')
    with partial.open('xb') as stream:
        os.fchmod(stream.fileno(), 0o600)
        stream.write(data)
        stream.flush()
        os.fsync(stream.fileno())
    os.link(partial, path, follow_symlinks=False)
    sync_dir(path.parent)
    partial.unlink()
    sync_dir(path.parent)


def write_json(path, value):
    publish(path, (json.dumps(value, indent=2, sort_keys=True, allow_nan=False) + '\n').encode())


def materializer():
    path = safe(ROOT / 'scripts/all24_materialize_main.py')
    need(sha(path) == MATERIALIZER_SHA, 'Pinned main materializer source changed')
    spec = importlib.util.spec_from_file_location('_main_all24_materializer', path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def validate_runtime(runtime, receipt_sha256, *, lab=ROOT):
    """Read-only exact main Build22 admission before any synthetic fixture writes."""
    need(safe(lab) == safe(ROOT), 'All-24 preparation is main-pack only')
    runtime = safe(runtime)
    need(runtime.parent == safe(ROOT / 'generated/world-axiom-runtimes') and re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._-]{0,127}', runtime.name),
         'Runtime must be a fresh main-pack nonce')
    need(runtime.is_dir() and runtime.stat().st_uid == os.getuid(), 'Missing/unowned runtime')
    need(isinstance(receipt_sha256, str) and re.fullmatch('[0-9a-f]{64}', receipt_sha256), 'Explicit receipt SHA required')
    path = safe(runtime / 'runtime-inputs-v1.json')
    need(path.is_file() and path.stat().st_nlink == 1 and path.stat().st_size <= 32 * 1024**2
         and sha(path) == receipt_sha256, 'Missing/changed materialization receipt')
    module = materializer()
    report = module.strict(path.read_bytes())
    need(report.get('schema') == module.SCHEMA and report.get('status') == 'materialized_unaccepted'
         and report.get('native_acceptance') is False and report.get('destination') == str(runtime)
         and report.get('created_utc') and 'completed_utc' not in report,
         'Not a complete unaccepted main Build22 materialization')
    pins = module.admit()  # the unchanged exact ZIP pair, ready marker, 49-mod manifest and four JARs
    need(report.get('accepted_zip_expected_sha256') == module.SERVER_SHA
         and report.get('client_zip_expected_sha256') == module.CLIENT_SHA
         and report.get('accepted_zip') == str(module.SERVER)
         and report.get('client_zip') == str(module.CLIENT)
         and report.get('pinned_release') == pins, 'Materialization not bound to exact main Build22 release')
    inventory = report.get('final_files')
    need(isinstance(inventory, dict) and len(inventory) == report.get('expected_archive_files')
         and len(inventory) >= 3000, 'Incomplete copied-file inventory')
    required = {'run.sh', 'run-forge.sh', 'better-world-management-server.sh', 'world-axioms.py',
                'server.properties', 'user_jvm_args.txt', 'eula.txt'}
    need(required <= inventory.keys(), 'Required native/projector inputs absent')
    need(report.get('eula') == 'explicit_user_permission_flag'
         and safe(runtime / 'eula.txt').read_bytes() == b'eula=true\n', 'Disposable EULA binding changed')
    overrides = report.get('overrides')
    need(isinstance(overrides, list) and len(overrides) == len(module.FEATURES)
         and {row.get('repository'): row.get('path') for row in overrides} ==
             {repo: 'mods/' + artifact for repo, artifact in module.FEATURES.items()}
         and all(row.get('mode') == 'already_packaged_exact_source_build_no_replacement'
                 and row.get('sha256') == pins['source_build_sha256'][row['repository']] for row in overrides),
         'Wrong exact production JAR overrides')
    forbidden_root = {'world', 'saves', 'logs', 'crash-reports', '.better_world_management', '.prestige',
                      'ops.json', 'whitelist.json', 'usercache.json', 'playerdata', 'advancements', 'stats'}
    need(not any((runtime / name).exists() for name in forbidden_root)
         and not (runtime / 'kubejs/config/world_axioms_runtime.json').exists(),
         'Runtime already contains state or a projection')
    actual = {}
    for member in runtime.rglob('*'):
        member = safe(member)
        need(member.is_dir() or (member.is_file() and member.stat().st_nlink == 1), 'Nonregular/hardlinked runtime entry')
        need(member.stat().st_uid == os.getuid(), 'Unowned runtime entry')
        if member.is_file():
            name = member.relative_to(runtime).as_posix()
            actual[name] = {'sha256': sha(member), 'bytes': member.stat().st_size}
    expected = {name: {'sha256': row['sha256'], 'bytes': row['bytes']} for name, row in inventory.items()}
    expected['runtime-inputs-v1.json'] = {'sha256': receipt_sha256, 'bytes': path.stat().st_size}
    need(actual == expected, 'Extra/missing/drifted materialized runtime bytes')
    return report

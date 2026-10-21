#!/usr/bin/env python3
"""Bind required Crops native inputs to one supervised owning source invocation.

Not a packager, alternate verifier, or data projector. The owning Crops task still
prepares and verifies its fresh UUID snapshot and every mandatory native oracle.
"""
import argparse
import os
from pathlib import Path
import subprocess
import sys
import tomllib
import json
import hashlib
import uuid

from prepare_dev_inputs import ensure_pin

CROPS = 'better-bumblezone-crops'


def canonical_directory(value):
    path = Path(os.path.abspath(value))
    if not path.is_dir() or path.resolve() != path:
        raise ValueError('Expected existing canonical directory: ' + str(path))
    return path


def hydrate_archive_inputs(root, repository, archives):
    """Rehydrate only the reviewed recipe's named raw parents, using real pins."""
    policy = json.loads((repository / 'gradle/native-canonical-glm-policy.json').read_bytes())
    owners = [row['origin'] for row in policy['selected_sdk_parent_hashes']]
    owners += [owner['origin'] for row in policy['root_rows']
               for owner in row['production_resource_owners']]
    names = {owner.split('::', 1)[0][len('archive://'):]
             for owner in owners if owner.startswith('archive://')}
    if not names or any(Path(name).name != name or not name.endswith('.jar') for name in names):
        raise ValueError('Invalid/empty reviewed raw archive parent scope')
    pins = {}
    for path in sorted((root / 'mods').glob('*.pw.toml')):
        pin = tomllib.loads(path.read_text())
        if pin['filename'] in names:
            if pin['filename'] in pins:
                raise ValueError('Ambiguous actual raw archive pin: ' + pin['filename'])
            pins[pin['filename']] = pin
    if names != pins.keys():
        raise ValueError('Reviewed raw archive lacks actual Root pin: ' + ', '.join(sorted(names - pins.keys())))
    for name in sorted(names):
        target, pin = archives / name, pins[name]
        # Never replace anomalous retained cache bytes as a side effect of prep.
        if target.exists() or target.is_symlink():
            if not target.is_file() or target.is_symlink():
                raise ValueError('Unsafe existing raw archive: ' + name)
            digest = hashlib.new(pin['download']['hash-format'])
            with target.open('rb') as stream:
                for chunk in iter(lambda: stream.read(1024 * 1024), b''):
                    digest.update(chunk)
            if digest.hexdigest() != pin['download']['hash']:
                raise ValueError('Changed retained raw archive: ' + name)
        ensure_pin(pin, archives)


def run(root, repository, command, *, environment=None, runner=None, hydrate=None):
    root, repository = canonical_directory(root), canonical_directory(repository)
    if repository.name != CROPS or not command or Path(command[0]) != repository / 'gradlew':
        raise ValueError('Crops binding requires its explicit owning Gradle invocation')
    environment = dict(os.environ if environment is None else environment)
    runner = subprocess.run if runner is None else runner
    hydrate = hydrate_archive_inputs if hydrate is None else hydrate
    cache = environment.get('BC_PACKAGE_ARTIFACT_CACHE') or str(Path.home() / '.cache/bc/packwiz-downloads')
    archives = Path(os.path.abspath(root / cache / 'mods'))
    for path in (archives, *archives.parents):
        if path.is_symlink():
            raise ValueError('Symbolic-link raw archive ancestry')
    snapshots = repository / '.native-fixture-inputs'
    if snapshots.is_symlink():
        raise ValueError('Symbolic-link snapshot scope')
    activity = 'crops-source-' + uuid.uuid4().hex
    owner = environment.get('BC_OWNER_SESSION') or environment.get('PI_SESSION_ID') or activity
    maintenance = [sys.executable, '-B', str(root / 'scripts/workspace-maintenance.py'),
                   '--workspace', str(root.parent)]
    # Registration precedes cache hydration, snapshot producers, and Gradle itself.
    runner(maintenance + ['begin', '--task', activity, '--owner', owner,
                         '--path', str(snapshots), '--path', str(archives)], check=True)
    hydrate(root, repository, archives)
    environment.update(BC_CANONICAL_PACK_ROOT=str(root), BC_CANONICAL_ARCHIVES=str(archives),
                       BC_CANONICAL_ACTIVITY=activity, BC_OWNER_SESSION=owner)
    result = runner(command, env=environment)
    if result.returncode:
        # Failed/interrupted activity is retained active for explicit quiet/recovery,
        # not automatically adopted/closed and never deleted.
        print('Crops source failed; retained activity ' + activity, file=sys.stderr)
        return result.returncode
    runner(maintenance + ['finish', '--task', activity, '--owner', owner], check=True)
    return 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', required=True, type=Path)
    parser.add_argument('--repository', required=True, type=Path)
    parser.add_argument('command', nargs=argparse.REMAINDER)
    args = parser.parse_args()
    command = args.command[1:] if args.command[:1] == ['--'] else args.command
    return run(args.root, args.repository, command)


if __name__ == '__main__':
    sys.exit(main())

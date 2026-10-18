#!/usr/bin/env python3
"""Exact candidate path selector shared by the facade and JVM harness (no preparation)."""
import argparse
import hashlib
import json
import re
from pathlib import Path


def ordinary(path, directory=False):
    path = Path(path)
    if not path.is_absolute() or path != Path(__import__('os').path.normpath(path)):
        raise ValueError(f'path must be absolute and normalized: {path}')
    for ancestor in [*reversed(path.parents), path]:
        if ancestor.is_symlink():
            raise ValueError(f'symlink path component: {ancestor}')
    if not (path.is_dir() if directory else path.is_file()):
        raise ValueError(f'missing ordinary path: {path}')
    return path


def document(path):
    path = ordinary(Path(path))
    if path.stat().st_size > 1024 * 1024:
        raise ValueError('candidate document exceeds one MiB')
    return json.loads(path.read_text())


def select(root, binding=None, handoff=None):
    root = ordinary(Path(root), directory=True)
    bound = document(binding)['candidates'] if binding else None
    queued = document(handoff)['candidate'] if handoff else None
    if bound:
        paths = {side: Path(bound[side]) for side in ('client', 'server')}
    elif queued:
        paths = {side: Path(queued[side]['path']) for side in ('client', 'server')}
    else:
        pack = ordinary(root / 'pack.toml')
        if pack.stat().st_size > 1024 * 1024:
            raise ValueError('pack.toml exceeds one MiB')
        lines = []
        for line in pack.read_text().splitlines():
            if line.lstrip().startswith('['):
                break
            if re.match(r'''\s*(?:version|"version"|'version')\s*=''', line):
                lines.append(line.strip())
        if len(lines) != 1:
            raise ValueError('pack.toml requires exactly one top-level Build version')
        version = re.fullmatch(r'version\s*=\s*"Build ([0-9]+)([a-z]?)"\s*(?:#.*)?', lines[0])
        if not version:
            raise ValueError('pack.toml has an invalid Build version')
        number, suffix = version.groups()
        dist = ordinary(root / 'dist', directory=True)
        for entry in dist.iterdir():
            other = re.fullmatch(r'build-([0-9]+)([a-z]?)', entry.name)
            if other and (int(other[1]), other[2]) > (int(number), suffix):
                raise ValueError('dist has a build newer than pack.toml')
        release = dist / f'build-{number}{suffix}'
        try:
            for directory in (release, release / 'client', release / 'server'):
                ordinary(directory, directory=True)
        except ValueError as error:
            raise ValueError(f'current Build {number}{suffix} candidate directories are missing or unsafe') from error
        paths = {side: release / side / 'better-content.zip' for side in ('client', 'server')}
    result = {}
    for side, path in paths.items():
        ordinary(path)
        relative = path.relative_to(root / 'dist')
        if len(relative.parts) != 3 or not re.fullmatch(r'build-[0-9]+[a-z]?', relative.parts[0]) or relative.parts[1:] != (side, 'better-content.zip'):
            raise ValueError('candidate is not a canonical dist side')
        digest = hashlib.sha256()
        with path.open('rb') as stream:
            for block in iter(lambda: stream.read(1024 * 1024), b''):
                digest.update(block)
        result[side] = str(path)
        result[side + '_sha256'] = digest.hexdigest()
        if bound and (str(path) != bound[side] or digest.hexdigest() != bound[side + '_sha256']):
            raise ValueError('candidate binding hash mismatch')
        if queued and (str(path) != queued[side]['path'] or digest.hexdigest() != queued[side]['sha256']):
            raise ValueError('handoff candidate mismatch')
    if paths['client'].parent.parent != paths['server'].parent.parent:
        raise ValueError('candidate sides belong to different releases')
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, required=True)
    parser.add_argument('--binding')
    parser.add_argument('--handoff')
    args = parser.parse_args()
    print(json.dumps(select(args.root, args.binding, args.handoff)))


if __name__ == '__main__':
    main()

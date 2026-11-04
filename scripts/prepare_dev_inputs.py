#!/usr/bin/env python3
"""Rehydrate hash-pinned native archives for Minecraft-free source inspection only."""
import hashlib
from pathlib import Path
import re
import tempfile
import tomllib
import urllib.parse
import urllib.request


def ensure_pin(pin, cache, opener=urllib.request.urlopen):
    name = pin['filename']
    if Path(name).name != name or name in {'.', '..'} or '/' in name or '\\' in name:
        raise ValueError('unsafe pinned filename')
    for parent in (cache, *cache.parents):
        if parent.is_symlink():
            raise ValueError('symbolic-link cache ancestor')
    target = cache / name
    if target.is_symlink():
        raise ValueError('symbolic-link cache target')
    download = pin['download']
    algorithm = download['hash-format']
    if algorithm not in {'sha1', 'sha256', 'sha512'}:
        raise ValueError('unsupported pinned hash')
    def digest(path):
        h = hashlib.new(algorithm)
        with path.open('rb') as stream:
            while chunk := stream.read(1024 * 1024):
                h.update(chunk)
        return h.hexdigest()
    if target.is_file() and digest(target) == download['hash']:
        return target
    url = download.get('url')
    if not url:
        if download.get('mode') != 'metadata:curseforge':
            raise ValueError('missing pinned download URL')
        file_id = str(pin['update']['curseforge']['file-id'])
        url = f'https://edge.forgecdn.net/files/{file_id[:-3]}/{int(file_id[-3:])}/{urllib.parse.quote(name)}'
    if urllib.parse.urlsplit(url).scheme != 'https':
        raise ValueError('native dependency URL must use HTTPS')
    cache.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(dir=cache, prefix='.download-', delete=False) as temp:
        temporary = Path(temp.name)
        try:
            with opener(url, timeout=120) as response:
                while chunk := response.read(1024 * 1024):
                    temp.write(chunk)
            temp.flush()
            if digest(temporary) != download['hash']:
                raise ValueError('pinned native JAR checksum mismatch: ' + name)
            temporary.replace(target)
        finally:
            temporary.unlink(missing_ok=True)
    return target


def required_inputs(root):
    tests = '\n'.join(path.read_text() for path in (root / 'src/test/kotlin').rglob('*.kt'))
    inputs = []
    for domain, helper in [('mods', 'pinnedModJar'), ('tacz', 'pinnedGunPack')]:
        names = set(re.findall(rf'{helper}\("([^"\n]+)"\)', tests))
        pins = {}
        for path in (root / domain).glob('*.pw.toml'):
            with path.open('rb') as stream:
                pin = tomllib.load(stream)
            if pin['filename'] in names:
                if pin['filename'] in pins:
                    raise ValueError('duplicate native source-test Packwiz pin')
                pins[pin['filename']] = pin
        if names != pins.keys():
            raise ValueError(f'native source-test archive lacks an active {domain} Packwiz pin')
        inputs.extend((domain, pins[name]) for name in sorted(names))
    return inputs


def main():
    root = Path(__file__).resolve().parents[1]
    inputs = required_inputs(root)
    for domain, pin in inputs:
        ensure_pin(pin, Path.home() / '.cache/bc/packwiz-downloads' / domain)
    print(f'Prepared {len(inputs)} hash-pinned native archives for source inspection; no refresh/deployment/runtime launch.')


if __name__ == '__main__':
    main()

#!/usr/bin/env python3
"""Restore the exact, SHA-256-pinned Forge installer required by packaging."""
from pathlib import Path
import sys
import tomllib
import urllib.error
import urllib.request

from prepare_dev_inputs import ensure_pin

# Same published installer and independently pinned digest used by pack-tests CI.
COORDINATE = '1.20.1-47.4.22'
SHA256 = 'a66b95e67eba6b3f72047aa73318e4b80edb616d5834cd05a5baae10d25699d6'


def prepare(root, opener=None):
    versions = tomllib.loads((root / 'pack.toml').read_text())['versions']
    coordinate = f'{versions["minecraft"]}-{versions["forge"]}'
    if coordinate != COORDINATE:
        raise ValueError(f'no integrity pin for Forge {coordinate}; update the reviewed installer pin before packaging')
    name = f'forge-{coordinate}-installer.jar'
    pin = {'filename': name, 'download': {
        'url': f'https://maven.minecraftforge.net/net/minecraftforge/forge/{coordinate}/{name}',
        'hash-format': 'sha256', 'hash': SHA256}}
    def official_download(url, timeout):
        request = urllib.request.Request(url, headers={'User-Agent': 'BetterContent-installer/1.0'})
        return urllib.request.urlopen(request, timeout=timeout)
    return ensure_pin(pin, root, opener=opener or official_download)


if __name__ == '__main__':
    try:
        print(prepare(Path(__file__).resolve().parents[1]))
    except (OSError, ValueError, KeyError, urllib.error.URLError) as error:
        sys.exit(f'Forge installer preparation failed: {error}')

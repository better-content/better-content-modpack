#!/usr/bin/env python3
"""Restore an ignored, checksum-pinned Gradle wrapper JAR from its signed-off distribution.

The distribution SHA comes from gradle-wrapper.properties; the nested JAR SHA is
pinned independently below. This file is source, not a bundled runtime binary.
Requires Python 3 (available on GitHub-hosted Linux runners).
"""
import hashlib
import io
import os
from pathlib import Path
import re
import sys
import urllib.request
import zipfile

WRAPPERS = {
    '8.8': 'cb0da6751c2b753a16ac168bb354870ebb1e162e9083f116729cec9c781156b8',
    '8.5': 'd3b261c2820e9e3d8d639ed084900f11f4a86050a8f83342ade7b6bc9b0d2bdd',
}
DISTRIBUTIONS = {
    '8.8': 'a4b4158601f8636cdeeab09bd76afb640030bb5b144aafe261a5e8af027dc612',
    '8.5': '9d926787066a081739e8200858338b4a69e837c3a821a33aca9db09dd4a41026',
}


def digest(data):
    return hashlib.sha256(data).hexdigest()


def fail(message):
    raise RuntimeError(message)


def main():
    root = Path(__file__).resolve().parent.parent
    props = (root / 'gradle/wrapper/gradle-wrapper.properties').read_text()
    match = re.search(r'^distributionUrl=https\\?://services\.gradle\.org/distributions/gradle-(8\.8|8\.5)-bin\.zip$', props, re.M)
    if not match:
        fail('Unsupported or changed Gradle distributionUrl; inspect the pinned wrapper version')
    version = match.group(1)
    expected_dist = re.search(r'^distributionSha256Sum=([0-9a-f]{64})$', props, re.M)
    if not expected_dist or expected_dist.group(1) != DISTRIBUTIONS[version]:
        fail('Changed/missing Gradle distribution SHA-256; fail closed')
    expected = WRAPPERS[version]
    output = root / 'gradle/wrapper/gradle-wrapper.jar'
    if output.exists() or output.is_symlink():
        if not output.is_file() or digest(output.read_bytes()) != expected:
            fail('Existing local Gradle wrapper JAR has unexpected bytes; not replacing it')
        return
    gradle_home = Path(os.environ.get('GRADLE_USER_HOME', str(Path.home() / '.gradle')))
    plugins = sorted((gradle_home / 'wrapper/dists' / f'gradle-{version}-bin').glob(
        f'*/gradle-{version}/lib/plugins/gradle-wrapper-{version}.jar'))
    for plugin in plugins:
        with zipfile.ZipFile(plugin) as archive:
            embedded = archive.read('gradle-wrapper.jar')
        if digest(embedded) == expected:
            break
        fail('Cached Gradle plugin contains the wrong wrapper bytes')
    else:
        cache = Path.home() / '.cache/better-content/gradle'
        cache.mkdir(parents=True, exist_ok=True)
        archive_path = cache / f'gradle-{version}-bin.zip'
        if archive_path.exists():
            raw = archive_path.read_bytes()
            if digest(raw) != DISTRIBUTIONS[version]:
                fail('Cached Gradle distribution has unexpected bytes; not replacing it')
        else:
            if os.environ.get('BC_WRAPPER_OFFLINE') == '1':
                fail('Exact Gradle wrapper absent and offline; supply the pinned distribution')
            url = f'https://services.gradle.org/distributions/gradle-{version}-bin.zip'
            request = urllib.request.Request(url, headers={'User-Agent': 'BetterContentWrapperBootstrap/1'})
            with urllib.request.urlopen(request, timeout=90) as stream:
                raw = stream.read()
            if digest(raw) != DISTRIBUTIONS[version]:
                fail('Downloaded Gradle distribution SHA-256 mismatch; nothing installed')
            temporary_cache = archive_path.with_name(archive_path.name + f'.{os.getpid()}.part')
            temporary_cache.write_bytes(raw)
            os.replace(temporary_cache, archive_path)
        with zipfile.ZipFile(io.BytesIO(raw)) as dist:
            plugin_bytes = dist.read(f'gradle-{version}/lib/plugins/gradle-wrapper-{version}.jar')
        with zipfile.ZipFile(io.BytesIO(plugin_bytes)) as plugin:
            embedded = plugin.read('gradle-wrapper.jar')
        if digest(embedded) != expected:
            fail('Gradle distribution contains an unexpected wrapper JAR; nothing installed')
    temporary = output.with_name(output.name + f'.{os.getpid()}.part')
    temporary.write_bytes(embedded)
    os.replace(temporary, output)


if __name__ == '__main__':
    try:
        main()
    except Exception as exc:
        print(f'Gradle wrapper bootstrap failed: {exc}', file=sys.stderr)
        sys.exit(1)

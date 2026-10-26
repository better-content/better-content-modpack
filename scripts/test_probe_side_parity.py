#!/usr/bin/env python3
"""Offline TOP packaging regressions; no Forge, Packwiz, downloads or real cache writes."""
import hashlib
import os
from pathlib import Path
import subprocess
import tempfile
import tomllib
import unittest


ROOT = Path(__file__).resolve().parents[1]


class ProbeSideParityTest(unittest.TestCase):
    def test_top_manifest_preserves_pinned_artifact_on_both_sides(self):
        manifest = tomllib.loads((ROOT / 'mods/the-one-probe.pw.toml').read_text())
        self.assertEqual('both', manifest['side'],
                         'TOP registers items and a recipe serializer, not just a client overlay')
        self.assertEqual('theoneprobe-1.20.1-10.0.3.jar', manifest['filename'])
        self.assertEqual('160ead07479f9f5d678fff4c57fbf97d019c69a6', manifest['download']['hash'])
        self.assertEqual({'file-id': 6106996, 'project-id': 245211}, manifest['update']['curseforge'])

    def resolve_fixture(self, filename, side):
        # Use the production resolver and manifest classification with synthetic bytes.
        # A deliberately invalid URL makes any unexpected download fail rather than
        # accidentally consulting the network or an existing user's artifact cache.
        temporary_root = Path.home() / '.tmp'
        temporary_root.mkdir(exist_ok=True)
        with tempfile.TemporaryDirectory(prefix='top-side-parity-', dir=temporary_root) as temporary:
            base = Path(temporary)
            source = base / 'pack/mods'
            source.mkdir(parents=True)
            cache = base / 'cache/mods'
            cache.mkdir(parents=True)
            payload = b'synthetic TOP packaging artifact\n'
            (cache / filename).write_bytes(payload)
            original = (ROOT / 'mods/the-one-probe.pw.toml').read_text()
            original = original.replace('theoneprobe-1.20.1-10.0.3.jar', filename)
            original = original.replace('160ead07479f9f5d678fff4c57fbf97d019c69a6',
                                        hashlib.sha1(payload).hexdigest())
            original = original.replace('mode = "metadata:curseforge"',
                                        'url = "invalid://offline-fixture"')
            (source / 'the-one-probe.pw.toml').write_text(original)
            env = os.environ.copy()
            env['BC_PACKAGE_ARTIFACT_CACHE'] = str(base / 'cache')
            target = base / 'target'
            result = subprocess.run(
                ['bash', str(ROOT / 'package.sh'), 'resolve', str(source.parent), str(target), side],
                text=True, capture_output=True, env=env, timeout=15)
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
            self.assertTrue((target / 'mods' / filename).is_file(),
                            f'{side} must retain registered TOP items: {result.stdout}')
            self.assertEqual(payload, (target / 'mods' / filename).read_bytes())
            self.assertIn('hits=1 downloads=0 uncached=0', result.stdout)

    def test_pinned_top_filename_is_materialized_for_client_and_server(self):
        for side in ('client', 'server'):
            with self.subTest(side=side):
                self.resolve_fixture('theoneprobe-1.20.1-10.0.3.jar', side)

    def test_legacy_hyphenated_name_is_not_silently_server_excluded(self):
        # Guard the obsolete filename mask too: it did not match today's actual
        # "theoneprobe" filename, but must not recur on any future rename.
        self.resolve_fixture('the-one-probe-offline-fixture.jar', 'server')


if __name__ == '__main__':
    unittest.main()

#!/usr/bin/env python3
"""Offline regression tests for reproducible Forge installer recovery."""
import hashlib
import io
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import prepare_forge_installer as installer


class InstallerTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        (self.root / 'pack.toml').write_text('[versions]\nminecraft="1.20.1"\nforge="47.4.22"\n')
        self.target = self.root / f'forge-{installer.COORDINATE}-installer.jar'
        self.payload = b'fixture installer'
        self.pin = patch.object(installer, 'SHA256', hashlib.sha256(self.payload).hexdigest())
        self.pin.start()
        self.addCleanup(self.pin.stop)
        self.requests = []

    def opener(self, url, timeout):
        self.requests.append(url)
        self.assertEqual(120, timeout)
        return io.BytesIO(self.payload)

    def test_missing_input_downloads_exact_official_coordinate(self):
        self.assertEqual(self.target, installer.prepare(self.root, self.opener))
        self.assertEqual(self.payload, self.target.read_bytes())
        self.assertEqual([f'https://maven.minecraftforge.net/net/minecraftforge/forge/{installer.COORDINATE}/{self.target.name}'], self.requests)
        installer.prepare(self.root, self.opener)
        self.assertEqual(1, len(self.requests), 'valid cached installer should not download again')

    def test_corrupt_cache_is_recovered(self):
        self.target.write_bytes(b'corrupt')
        installer.prepare(self.root, self.opener)
        self.assertEqual(self.payload, self.target.read_bytes())

    def test_mismatch_never_promotes_download(self):
        with self.assertRaisesRegex(ValueError, 'checksum mismatch'):
            installer.prepare(self.root, lambda *a, **k: io.BytesIO(b'wrong bytes'))
        self.assertFalse(self.target.exists())
        self.assertFalse(list(self.root.glob('.download-*')))

    def test_unknown_coordinate_fails_before_network(self):
        (self.root / 'pack.toml').write_text('[versions]\nminecraft="1.20.1"\nforge="47.4.23"\n')
        with self.assertRaisesRegex(ValueError, 'no integrity pin for Forge'):
            installer.prepare(self.root, self.opener)
        self.assertFalse(self.requests)

    def test_network_failure_is_not_a_valid_installer(self):
        def unavailable(*args, **kwargs):
            raise OSError('offline')
        with self.assertRaisesRegex(OSError, 'offline'):
            installer.prepare(self.root, unavailable)
        self.assertFalse(self.target.exists())
        self.assertFalse(list(self.root.glob('.download-*')))


if __name__ == '__main__':
    unittest.main()

#!/usr/bin/env python3
import hashlib
import io
from pathlib import Path
import tempfile
import unittest
from prepare_dev_inputs import ensure_pin


class PinnedInputsTests(unittest.TestCase):
    def setUp(self):
        root = Path.home() / '.tmp'
        root.mkdir(exist_ok=True)
        self.temp = tempfile.TemporaryDirectory(dir=root)
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.cache = self.root / 'cache'
        self.body = b'pinned-native-test-fixture'
        self.pin = {'filename': 'native.jar', 'download': {'hash-format': 'sha256',
                    'hash': hashlib.sha256(self.body).hexdigest(), 'url': 'https://example.invalid/native.jar'}}
        self.open = lambda *args, **kwargs: io.BytesIO(self.body)

    def test_cold_warm_corrupt_and_removed_cache(self):
        target = ensure_pin(self.pin, self.cache, self.open)
        self.assertEqual(self.body, target.read_bytes())
        def unavailable(*args, **kwargs):
            raise AssertionError('warm pinned input should not download')
        ensure_pin(self.pin, self.cache, unavailable)
        target.write_bytes(b'corrupt')
        ensure_pin(self.pin, self.cache, self.open)
        target.unlink()
        ensure_pin(self.pin, self.cache, self.open)
        self.assertEqual(self.body, target.read_bytes())

    def test_bad_download_does_not_replace_existing_cache(self):
        self.cache.mkdir()
        target = self.cache / 'native.jar'
        target.write_bytes(b'old')
        with self.assertRaises(ValueError):
            ensure_pin(self.pin, self.cache, lambda *args, **kwargs: io.BytesIO(b'wrong'))
        self.assertEqual(b'old', target.read_bytes())
        self.assertEqual([target], list(self.cache.iterdir()))

    def test_traversal_and_symbolic_cache_are_rejected(self):
        self.pin['filename'] = '../input.jar'
        with self.assertRaises(ValueError):
            ensure_pin(self.pin, self.cache, self.open)
        self.pin['filename'] = 'native.jar'
        self.cache.symlink_to(self.root, target_is_directory=True)
        with self.assertRaises(ValueError):
            ensure_pin(self.pin, self.cache, self.open)

    def test_curseforge_pin_uses_exact_file_id_and_filename(self):
        self.pin['download'].pop('url')
        self.pin['download']['mode'] = 'metadata:curseforge'
        self.pin['update'] = {'curseforge': {'file-id': 8554528}}
        calls = []
        def fetch(url, **kwargs):
            calls.append(url)
            return io.BytesIO(self.body)
        ensure_pin(self.pin, self.cache, fetch)
        self.assertEqual(['https://edge.forgecdn.net/files/8554/528/native.jar'], calls)


if __name__ == '__main__':
    unittest.main(verbosity=2)

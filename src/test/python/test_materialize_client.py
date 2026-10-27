import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

spec = importlib.util.spec_from_file_location("materializer", Path(__file__).parents[2] / "main/resources/materialize_client.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class MaterializerTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.archive = self.root / "client.zip"
        self.client = self.root / "client"
        self.cache = self.root / "cache"
        self.env = patch.dict(os.environ, BC_SESSION_ARTIFACT_CACHE=str(self.cache))
        self.env.start()
        self.addCleanup(self.env.stop)

    def bundle(self, artifacts=None, files=None, extra=None):
        manifest = {"overrides": "overrides", "files": files or [], "minecraft": {"version": "1.20.1"}}
        lock = {"schema": "bc.client_artifacts.v1", "artifacts": artifacts or []}
        with zipfile.ZipFile(self.archive, "w") as archive:
            archive.writestr("manifest.json", json.dumps(manifest))
            archive.writestr("better-content-artifacts.json", json.dumps(lock))
            archive.writestr("overrides/options.txt", "packaged setting")
            for name, content in (extra or {}).items():
                archive.writestr(name, content)

    def test_uses_packaged_overrides_not_a_live_checkout(self):
        self.bundle()
        (self.root / "options.txt").write_text("unpackaged change")
        module.materialize(self.archive, self.client)
        self.assertEqual("packaged setting", (self.client / "options.txt").read_text())
        self.assertTrue((self.root / "client-manifest.json").is_file())

    def test_will_not_reuse_an_existing_fixture(self):
        self.bundle()
        self.client.mkdir()
        with self.assertRaises(ValueError):
            module.materialize(self.archive, self.client)

    def test_required_exported_ids_must_be_in_the_lock(self):
        self.bundle(files=[{"projectID": 1, "fileID": 2, "required": True}])
        with self.assertRaisesRegex(ValueError, "missing exported"):
            module.materialize(self.archive, self.client)

    def test_matching_content_addressed_cache_needs_no_download(self):
        payload = b"pinned fixture artifact"
        digest = hashlib.sha256(payload).hexdigest()
        cached = self.cache / "sha256" / digest
        cached.parent.mkdir(parents=True)
        cached.write_bytes(payload)
        self.bundle(artifacts=[{"path": "mods/pinned.jar", "url": "https://example.invalid/pinned", "hash_format": "sha256", "hash": digest}])
        with patch.object(module.urllib.request, "urlopen", side_effect=AssertionError("unexpected network")):
            module.materialize(self.archive, self.client)
        self.assertEqual(payload, (self.client / "mods/pinned.jar").read_bytes())

    def test_wrong_download_cannot_populate_cache(self):
        digest = hashlib.sha256(b"expected").hexdigest()
        self.bundle(artifacts=[{"path": "mods/pinned.jar", "url": "https://example.invalid/pinned", "hash_format": "sha256", "hash": digest}])
        with patch.object(module.urllib.request, "urlopen", return_value=io.BytesIO(b"wrong")):
            with self.assertRaisesRegex(ValueError, "hash mismatch"):
                module.materialize(self.archive, self.client)
        self.assertFalse((self.cache / "sha256" / digest).exists())

    def test_archive_traversal_is_rejected(self):
        self.bundle(extra={"overrides/../../escape": "unsafe"})
        with self.assertRaises(ValueError):
            module.materialize(self.archive, self.client)
        self.assertFalse((self.root / "escape").exists())

    def test_symlink_is_rejected(self):
        self.bundle()
        entry = zipfile.ZipInfo("overrides/link")
        entry.external_attr = 0o120777 << 16
        with zipfile.ZipFile(self.archive, "a") as archive:
            archive.writestr(entry, "../outside")
        with self.assertRaisesRegex(ValueError, "symlink"):
            module.materialize(self.archive, self.client)


if __name__ == "__main__":
    unittest.main()

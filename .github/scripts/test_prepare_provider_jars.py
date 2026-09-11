import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
import zipfile

spec = importlib.util.spec_from_file_location("providers", Path(__file__).with_name("prepare-provider-jars.py"))
providers = importlib.util.module_from_spec(spec)
spec.loader.exec_module(providers)


class ProviderBootstrapTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.pack = Path(self.temp.name) / "pack"
        self.output = Path(self.temp.name) / "output"
        (self.pack / "gradle").mkdir(parents=True)
        (self.pack / "mods").mkdir()
        self.mods = [dict(repository=n, modId=n, artifact=f"{n}-1.0.jar", dependsOn=d)
                     for n, d in [("base", []), ("provider", ["base"]), ("consumer", ["provider", "base"])]]
        for mod in self.mods:
            with zipfile.ZipFile(self.pack / "mods" / mod["artifact"], "w") as jar:
                jar.writestr("META-INF/better-content-source.properties",
                             f"schema=bc.custom_mod_source.v1\nrepository={mod['repository']}\nmod_id={mod['modId']}\ncommit={'a'*40}\n")
        self.inventory()
        self.index()

    def inventory(self):
        (self.pack / "gradle/active-custom-mods.json").write_text(json.dumps(
            dict(schema="bc.active_custom_mods.v1", mods=self.mods)))

    def index(self):
        (self.pack / "index.toml").write_text('hash-format = "sha256"\n' + ''.join(
            f'[[files]]\nfile = "mods/{p.name}"\nhash = "{hashlib.sha256(p.read_bytes()).hexdigest()}"\n'
            for p in (self.pack / "mods").iterdir()))

    def test_transitive_closure_is_ordered_unique_and_excludes_consumer(self):
        result = providers.prepare(self.pack, "consumer", self.output)
        self.assertEqual([p["repository"] for p in result["providers"]], ["base", "provider"])
        self.assertFalse((self.output / "consumer-1.0.jar").exists())
        self.assertEqual(providers.prepare(self.pack, "consumer", self.output), result)

    def test_corrupt_provider_fails_before_any_copy(self):
        (self.pack / "mods/provider-1.0.jar").write_bytes(b"corrupt")
        with self.assertRaisesRegex(ValueError, "hash mismatch"):
            providers.prepare(self.pack, "consumer", self.output)
        self.assertFalse(self.output.exists())

    def test_missing_provider_fails_before_any_copy(self):
        (self.pack / "mods/provider-1.0.jar").unlink()
        with self.assertRaises(FileNotFoundError):
            providers.prepare(self.pack, "consumer", self.output)
        self.assertFalse(self.output.exists())

    def test_wrong_identity_fails_even_with_valid_hash(self):
        self.mods[1]["modId"] = "wrong"
        self.inventory()
        with self.assertRaisesRegex(ValueError, "identity mismatch"):
            providers.prepare(self.pack, "consumer", self.output)

    def test_cycle_and_unknown_dependency_are_rejected(self):
        self.mods[0]["dependsOn"] = ["consumer"]
        self.inventory()
        with self.assertRaisesRegex(ValueError, "cycle"):
            providers.prepare(self.pack, "consumer", self.output)
        self.mods[0]["dependsOn"] = ["unknown"]
        self.inventory()
        with self.assertRaisesRegex(ValueError, "unknown provider"):
            providers.prepare(self.pack, "consumer", self.output)

    def test_existing_unrelated_output_is_preserved(self):
        self.output.mkdir()
        marker = self.output / "keep.txt"
        marker.write_text("owned")
        with self.assertRaisesRegex(ValueError, "unrelated"):
            providers.prepare(self.pack, "consumer", self.output)
        self.assertEqual(marker.read_text(), "owned")

    def test_duplicate_and_unknown_repository_are_rejected(self):
        with self.assertRaisesRegex(ValueError, "unknown active repository"):
            providers.prepare(self.pack, "absent", self.output)
        self.mods.append(self.mods[0])
        self.inventory()
        with self.assertRaisesRegex(ValueError, "duplicate"):
            providers.prepare(self.pack, "consumer", self.output)

    def test_unsafe_artifact_path_is_rejected(self):
        self.mods[0]["artifact"] = "../outside.jar"
        self.inventory()
        with self.assertRaisesRegex(ValueError, "unsafe"):
            providers.prepare(self.pack, "consumer", self.output)


if __name__ == "__main__":
    unittest.main()

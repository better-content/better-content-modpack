#!/usr/bin/env python3
"""Offline regressions; all synthetic fixtures/logs are retained under build/."""
from pathlib import Path
import json
import subprocess
import tomllib
import unittest
import uuid
from prepare_native_fml import prepare, DEFAULTS

ROOT = Path(__file__).resolve().parents[1]


class NativeFmlTest(unittest.TestCase):
    def fixture(self):
        path = ROOT / "build/native-fml-tests" / uuid.uuid4().hex
        path.mkdir(parents=True)
        return path

    def test_first_launch_disables_only_unqualified_update_news(self):
        root = self.fixture()
        result = prepare(root)
        self.assertEqual(result["status"], "seeded")
        self.assertEqual((root / "config/fml.toml").read_bytes(), DEFAULTS)
        self.assertEqual(tomllib.loads(DEFAULTS.decode()), {"versionCheck": False})

    def test_authored_existing_settings_are_byte_preserved(self):
        root = self.fixture()
        (root / "config").mkdir()
        path = root / "config/fml.toml"
        data = b"versionCheck=true\n# authored \xff\n"
        path.write_bytes(data)
        self.assertEqual(prepare(root)["status"], "preserved")
        self.assertEqual(path.read_bytes(), data)

    def test_symlink_target_and_ancestry_rejected(self):
        root = self.fixture()
        real = root / "real"
        real.mkdir()
        (root / "config").symlink_to(real, target_is_directory=True)
        with self.assertRaises(ValueError):
            prepare(root)
        self.assertFalse((real / "fml.toml").exists())
        (real / "config").mkdir()
        victim = root / "victim"
        victim.write_bytes(b"retained")
        (real / "config/fml.toml").symlink_to(victim)
        with self.assertRaises(ValueError):
            prepare(real)
        self.assertEqual(victim.read_bytes(), b"retained")

    def test_nonfile_and_traversal_rejected(self):
        root = self.fixture()
        (root / "config/fml.toml").mkdir(parents=True)
        with self.assertRaises(ValueError):
            prepare(root)
        with self.assertRaises(ValueError):
            prepare(root / "../escape")

    def test_delivered_pack_is_pinned_not_background_update_driven(self):
        self.assertIs(tomllib.loads((ROOT / "config/fml.toml").read_text())["versionCheck"], False)
        inventory = json.loads((ROOT / "gradle/active-custom-mods.json").read_text())
        for name in ["config/fml.toml", "scripts/prepare_native_fml.py", "gradle/source-native-fixture.gradle"]:
            self.assertIn(name, inventory["verificationInputs"])

    def test_actual_gradle_hook_is_native_only_idempotent_and_rejects_source_root(self):
        root = self.fixture()
        (root / "settings.gradle").write_text("rootProject.name='synthetic-source-native-fixture'\n")
        script = str(ROOT / "gradle/source-native-fixture.gradle")
        build = """
extensions.add('minecraft', new Expando(runs: [
    new Expando(name: 'gameTestServer', taskName: 'runGameTestServer',
        workingDirectory: findProperty('nativeAtRoot') ? projectDir : file('native')),
    new Expando(name: 'client', taskName: 'runClient', workingDirectory: file('client'))]))
apply from: SCRIPT
apply from: SCRIPT
// Reproduce Forge's late Run task registration, after the fixture callback.
gradle.projectsEvaluated {
tasks.register('runGameTestServer') {
    doLast {
        assert file('native/config/fml.toml').text.contains('versionCheck = false')
        println('SYNTHETIC_NATIVE_HOOK_PASSED')
    }
}
tasks.register('runClient') {
    doLast { assert !file('client/config/fml.toml').exists() }
}
}
""".replace("SCRIPT", json.dumps(script))
        (root / "build.gradle").write_text(build)
        base = [str(ROOT / "gradlew"), "--no-daemon", "--max-workers=1", "--console=plain", "-p", str(root)]
        for blocked in [False, True]:
            log = root / ("rejected-root.log" if blocked else "native-only.log")
            with log.open("w") as output:
                result = subprocess.run(base + (["-PnativeAtRoot=true"] if blocked else []) +
                                        ["runGameTestServer", "runClient"], cwd=ROOT, stdout=output,
                                        stderr=subprocess.STDOUT, timeout=180, check=False)
            text = log.read_text()
            if blocked:
                self.assertNotEqual(result.returncode, 0, text)
                self.assertIn("Refusing source-root native fixture config", text)
                self.assertFalse((root / "config/fml.toml").exists())
            else:
                self.assertEqual(result.returncode, 0, text[-12000:])
                self.assertIn("SYNTHETIC_NATIVE_HOOK_PASSED", text)
                self.assertEqual(text.count('"schema": "bc.source_native_fml.v1"'), 1)
                self.assertFalse((root / "client/config/fml.toml").exists())


if __name__ == "__main__":
    unittest.main()

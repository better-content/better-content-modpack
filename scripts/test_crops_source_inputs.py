#!/usr/bin/env python3
"""Targeted controls for the Crops source input/retention adapter; no Forge."""
import json
from pathlib import Path
import subprocess
import tempfile
import unittest

import crops_source_inputs as m


class CropsSourceInputsTest(unittest.TestCase):
    def setUp(self):
        # Retain synthetic evidence just like the other source fixtures.
        outputs = Path(__file__).resolve().parents[1] / 'build/test-fixtures/crops-source-inputs'
        outputs.mkdir(parents=True, exist_ok=True)
        self.base = Path(tempfile.mkdtemp(prefix='case-', dir=outputs))
        self.root = self.base / 'pack'
        self.repo = self.base / 'mod_source' / m.CROPS
        self.root.mkdir()
        self.repo.mkdir(parents=True)
        self.command = [str(self.repo / 'gradlew'), '--no-daemon', 'clean', 'verifyFull', 'stageRuntimeJar']
        self.environment = {'BC_PACKAGE_ARTIFACT_CACHE': str(self.base / 'raw-cache'),
                            'PI_SESSION_ID': 'fixture-conversation', 'BC_CUSTOM_MOD_JAR_DIR': '/immutable/providers'}
        self.events = []

    def runner(self, command, **kwargs):
        if command == self.command:
            self.events.append(('gradle', list(command), kwargs['env']))
        else:
            self.events.append(('maintenance', list(command)))
        return subprocess.CompletedProcess(command, 0)

    def hydrate(self, *args):
        self.events.append(('hydrate', args))

    def run_adapter(self, **kwargs):
        return m.run(self.root, self.repo, self.command, environment=self.environment,
                     runner=kwargs.pop('runner', self.runner),
                     hydrate=kwargs.pop('hydrate', self.hydrate), **kwargs)

    def test_registered_before_hydration_and_exact_owning_command(self):
        self.assertEqual(0, self.run_adapter())
        self.assertEqual(['maintenance', 'hydrate', 'gradle', 'maintenance'], [e[0] for e in self.events])
        begin, _, actual, finish = self.events
        self.assertIn('begin', begin[1])
        self.assertIn(str(self.repo / '.native-fixture-inputs'), begin[1])
        self.assertIn(str(self.base / 'raw-cache/mods'), begin[1])
        self.assertEqual(self.command, actual[1])
        env = actual[2]
        self.assertEqual(str(self.root), env['BC_CANONICAL_PACK_ROOT'])
        self.assertEqual(str(self.base / 'raw-cache/mods'), env['BC_CANONICAL_ARCHIVES'])
        self.assertEqual('fixture-conversation', env['BC_OWNER_SESSION'])
        self.assertEqual('/immutable/providers', env['BC_CUSTOM_MOD_JAR_DIR'])
        self.assertEqual(env['BC_CANONICAL_ACTIVITY'], begin[1][begin[1].index('--task') + 1])
        self.assertIn('finish', finish[1])
        self.assertEqual(begin[1][-7:-4], finish[1][-3:])

    def test_explicit_owner_precedes_session_and_does_not_mutate_parent_environment(self):
        self.environment['BC_OWNER_SESSION'] = 'explicit-owner'
        before = dict(self.environment)
        self.run_adapter()
        self.assertEqual('explicit-owner', self.events[2][2]['BC_OWNER_SESSION'])
        self.assertEqual(before, self.environment)

    def test_failure_retains_activity_and_exact_exit(self):
        def failed(command, **kwargs):
            result = self.runner(command, **kwargs)
            return subprocess.CompletedProcess(command, 7) if command == self.command else result
        self.assertEqual(7, self.run_adapter(runner=failed))
        self.assertEqual(['maintenance', 'hydrate', 'gradle'], [e[0] for e in self.events])

    def test_preparation_failure_never_runs_gradle_or_finishes_unknown_activity(self):
        def failed(*args):
            raise ValueError('actual pin changed')
        with self.assertRaisesRegex(ValueError, 'actual pin changed'):
            self.run_adapter(hydrate=failed)
        self.assertEqual(['maintenance'], [e[0] for e in self.events])

    def test_finish_failure_cannot_be_reported_as_success(self):
        def failed(command, **kwargs):
            self.runner(command, **kwargs)
            if 'finish' in command:
                raise subprocess.CalledProcessError(9, command)
            return subprocess.CompletedProcess(command, 0)
        with self.assertRaises(subprocess.CalledProcessError):
            self.run_adapter(runner=failed)

    def test_wrong_command_and_symlink_scope_rejected_before_registration(self):
        with self.assertRaisesRegex(ValueError, 'owning Gradle'):
            m.run(self.root, self.repo, ['sh', '-c', 'true'], runner=self.runner)
        (self.repo / '.native-fixture-inputs').symlink_to(self.root, target_is_directory=True)
        with self.assertRaisesRegex(ValueError, 'snapshot scope'):
            self.run_adapter()
        self.assertEqual([], self.events)

    def test_named_raw_parents_rehydrate_with_actual_root_pin_and_nested_owner(self):
        (self.repo / 'gradle').mkdir()
        policy = {'selected_sdk_parent_hashes': [{'origin': 'archive://native.jar::META-INF/jarjar/inner.jar'}],
                  'root_rows': [{'production_resource_owners': [{'origin': 'archive://native.jar'}]}]}
        (self.repo / 'gradle/native-canonical-glm-policy.json').write_text(json.dumps(policy))
        (self.root / 'mods').mkdir()
        data = b'actual pinned archive bytes'
        import hashlib
        (self.root / 'mods/native.pw.toml').write_text(
            'filename = "native.jar"\n[download]\nurl = "https://example.invalid/native.jar"\n'
            'hash-format = "sha256"\nhash = "' + hashlib.sha256(data).hexdigest() + '"\n')
        archives = self.base / 'archive-fixture'
        archives.mkdir()
        (archives / 'native.jar').write_bytes(data)
        m.hydrate_archive_inputs(self.root, self.repo, archives)
        (archives / 'native.jar').write_bytes(b'changed')
        # Actual mismatch guard retains bytes and fails before a download can replace them.
        with self.assertRaisesRegex(ValueError, 'Changed retained raw archive'):
            m.hydrate_archive_inputs(self.root, self.repo, archives)
        self.assertEqual(b'changed', (archives / 'native.jar').read_bytes())
        (self.root / 'mods/native.pw.toml').rename(self.root / 'native-pin.retained')
        with self.assertRaisesRegex(ValueError, 'lacks actual Root pin'):
            m.hydrate_archive_inputs(self.root, self.repo, archives)


if __name__ == '__main__':
    unittest.main()

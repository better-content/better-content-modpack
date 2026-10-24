#!/usr/bin/env python3
"""Pure stdlib controls for package.sh assembly; no Packwiz or Minecraft invocation.

Fixtures are synthetic and retained under HOME/.tmp, including failed controls.
"""
import copy
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
import zipfile


PACKAGE = Path(__file__).resolve().parents[1] / 'package.sh'


class RuntimeOverridesTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        temporary_root = Path.home() / '.tmp'
        temporary_root.mkdir(exist_ok=True)
        cls.evidence = Path(tempfile.mkdtemp(prefix='package-runtime-overrides-', dir=temporary_root))
        print(f'Retained synthetic packaging fixtures: {cls.evidence}', flush=True)

    def setUp(self):
        self.base = self.evidence / self._testMethodName
        self.root = self.base / 'pack'
        (self.root / 'config').mkdir(parents=True)
        (self.root / 'config/better-content-runtime-overrides.json').write_text('{}')
        self.prepared = self.base / 'prepared'
        self.prepared.mkdir()
        self.plan = {'schema': 'bc.package_runtime_overrides.v1', 'artifacts': [
            {'input': f'original-{n}.jar', 'output': f'original-{n}-bc-runtime.1.jar',
             'manifest': f'original-{n}.pw.toml', 'project_id': 100 + n, 'file_id': 200 + n}
            for n in range(5)
        ]}
        for n, entry in enumerate(self.plan['artifacts']):
            with zipfile.ZipFile(self.prepared / entry['output'], 'w') as archive:
                archive.writestr('test.txt', f'corrected {n}')
        self.save_plan()
        self.target = self.base / 'target'
        self.target.mkdir()

    def save_plan(self):
        (self.prepared / 'selection.json').write_text(json.dumps(self.plan))

    def call(self, mode='tree', target=None, prepared=True, script=None, success=True):
        env = os.environ.copy()
        env.pop('BC_PACKAGE_RUNTIME_OVERRIDES', None)
        env.pop('BC_PACKAGE_INTERFACE_OVERRIDES', None)
        env.pop('BC_PACKAGE_STARTUP_OVERRIDES', None)
        if getattr(self, 'startup_env', False):
            env['BC_PACKAGE_STARTUP_OVERRIDES'] = str(self.startup_directory)
        if getattr(self, 'interface_env', False):
            env['BC_PACKAGE_INTERFACE_OVERRIDES'] = str(self.interface_directory)
        if prepared:
            env['BC_PACKAGE_RUNTIME_OVERRIDES'] = str(self.prepared)
        result = subprocess.run(
            ['bash', '-c', 'source "$1"; ROOT="$2"; ' + (script or 'apply_runtime_overrides "$3" "$4"'),
             'test', str(PACKAGE), str(self.root), mode, str(target or self.target)],
            env=env, text=True, capture_output=True, timeout=15)
        (self.base / f'call-{len(list(self.base.glob("call-*")))}.log').write_text(
            result.stdout + result.stderr)
        if success:
            self.assertEqual(result.returncode, 0, result.stderr)
        else:
            self.assertNotEqual(result.returncode, 0)
            self.assertIn('package failed: runtime overrides:', result.stderr)
        return result

    def tree(self):
        mods = self.target / 'mods'
        mods.mkdir()
        for entry in self.plan['artifacts']:
            (mods / entry['input']).write_bytes(b'original bytes')
            (mods / entry['manifest']).write_bytes(b'original manifest')
        (mods / 'unrelated.jar').write_bytes(b'unrelated\x00payload')
        (mods / 'unrelated.pw.toml').write_bytes(b'unrelated pin')
        return mods

    def assert_tree(self, mods):
        for entry in self.plan['artifacts']:
            self.assertFalse((mods / entry['input']).exists())
            self.assertFalse((mods / entry['manifest']).exists())
            self.assertEqual((mods / entry['output']).read_bytes(),
                             (self.prepared / entry['output']).read_bytes())
        self.assertEqual((mods / 'unrelated.jar').read_bytes(), b'unrelated\x00payload')
        self.assertEqual((mods / 'unrelated.pw.toml').read_bytes(), b'unrelated pin')
        self.assertEqual(len(list(mods.glob('*-bc-runtime.1.jar'))), 5)

    def client_zip(self, overrides='overrides', files=None, extra=None):
        self.archive = self.base / 'better-content.zip'
        self.manifest = {
            'minecraft': {'version': '1.20.1', 'modLoaders': [{'id': 'forge-47.4.22', 'primary': True}]},
            'manifestType': 'minecraftModpack', 'manifestVersion': 1, 'name': 'Synthetic',
            'version': 'Build 1', 'author': 'Test', 'overrides': overrides,
            'files': files if files is not None else [
                {'projectID': e['project_id'], 'fileID': e['file_id'], 'required': True}
                for e in self.plan['artifacts']
            ] + [{'projectID': 999, 'fileID': 888, 'required': False, 'extra': {'keep': True}},
                 {'projectID': 100, 'fileID': 777, 'required': True},
                 {'projectID': 777, 'fileID': 200, 'required': True}],
            'custom': {'nested': [1, 'keep', False]}}
        self.payloads = {'manifest.json': json.dumps(self.manifest).encode(),
                         f'{overrides}/mods/unrelated.jar': b'\x00unrelated jar\xff',
                         f'{overrides}/mods/unrelated.pw.toml': b'unrelated pin',
                         'unrelated/readme.txt': b'readme\r\n', 'empty/': b''}
        for entry in self.plan['artifacts']:
            self.payloads[f'{overrides}/mods/{entry["input"]}'] = b'original'
            self.payloads[f'{overrides}/mods/{entry["manifest"]}'] = b'pin'
        self.payloads.update(extra or {})
        with zipfile.ZipFile(self.archive, 'w', compression=zipfile.ZIP_DEFLATED) as archive:
            archive.comment = b'keep archive comment'
            for name, data in self.payloads.items():
                info = zipfile.ZipInfo(name, (2025, 1, 2, 3, 4, 6))
                info.comment = b'keep entry comment'
                info.extra = b'\xfe\xca\x00\x00'
                info.external_attr = 0o100644 << 16
                archive.writestr(info, data)
        return self.archive

    def assert_client(self, overrides):
        with zipfile.ZipFile(self.archive) as archive:
            names = archive.namelist()
            self.assertEqual(len(names), len(set(names)))
            self.assertEqual(archive.comment, b'keep archive comment')
            manifest = json.loads(archive.read('manifest.json'))
            expected = copy.deepcopy(self.manifest)
            expected['files'] = expected['files'][5:]
            self.assertEqual(manifest, expected)
            removed = {f'{overrides}/mods/{e[k]}' for e in self.plan['artifacts'] for k in ('input', 'manifest')}
            for name, data in self.payloads.items():
                if name in removed:
                    self.assertNotIn(name, names)
                elif name != 'manifest.json':
                    self.assertEqual(archive.read(name), data)
                    self.assertEqual(archive.getinfo(name).comment, b'keep entry comment')
                    self.assertEqual(archive.getinfo(name).extra, b'\xfe\xca\x00\x00')
            for entry in self.plan['artifacts']:
                name = f'{overrides}/mods/{entry["output"]}'
                self.assertEqual(names.count(name), 1)
                self.assertEqual(archive.read(name), (self.prepared / entry['output']).read_bytes())

    def test_stage_both_sides_after_resolution(self):
        for side in ('server', 'client'):
            target = self.base / side
            # Stub only download resolution: require original files before replacement.
            script = '''resolve_artifacts() { test -f "$1/mods/original-0.jar"; printf '%s' "$2" > "$1/resolved-side"; }
stage_side "$3" "$4"'''
            if not (self.root / 'mods').exists():
                source = self.tree()
                (self.root / 'mods').mkdir()
                for item in source.iterdir():
                    (self.root / 'mods' / item.name).write_bytes(item.read_bytes())
            self.call(mode=side, target=target, script=script)
            self.assert_tree(target / 'mods')
            self.assertEqual((target / 'resolved-side').read_text(), side)
            self.assertEqual((self.root / 'mods/original-0.jar').read_bytes(), b'original bytes')

    def test_tree_preserves_hardlinked_original(self):
        mods = self.tree()
        original = mods / self.plan['artifacts'][0]['input']
        cache = self.base / 'cache-original.jar'
        os.link(original, cache)
        self.call()
        self.assert_tree(mods)
        self.assertEqual(cache.read_bytes(), b'original bytes')

    def test_client_zip(self):
        self.call('client-zip', self.client_zip())
        self.assert_client('overrides')

    def test_custom_overrides_root(self):
        self.call('client-zip', self.client_zip('custom/content'))
        self.assert_client('custom/content')

    def test_relative_target(self):
        self.tree()
        self.call(target=Path('target'), script='cd "$(dirname -- "$ROOT")"; apply_runtime_overrides "$3" "$4"')
        self.assert_tree(self.target / 'mods')

    def test_pointer_fallback(self):
        pointer = self.root / '.worklane/runtime-overrides/current.path'
        pointer.parent.mkdir(parents=True)
        pointer.write_text(str(self.prepared) + '\n')
        self.tree()
        self.call(prepared=False)
        self.assert_tree(self.target / 'mods')

    def test_no_policy_noop(self):
        (self.root / 'config/better-content-runtime-overrides.json').unlink()
        mods = self.tree()
        before = {p.name: p.read_bytes() for p in mods.iterdir()}
        self.call(prepared=False)
        self.assertEqual(before, {p.name: p.read_bytes() for p in mods.iterdir()})
        archive = self.client_zip()
        before = archive.read_bytes()
        self.call('client-zip', archive, prepared=False)
        self.assertEqual(archive.read_bytes(), before)

    def test_missing_prepared_pointer(self):
        self.call(prepared=False, success=False)

    def test_missing_prepared_jar_rejects_before_mutation(self):
        mods = self.tree()
        (self.prepared / self.plan['artifacts'][-1]['output']).unlink()
        self.call(success=False)
        self.assertTrue((mods / 'original-0.jar').exists())

    def test_unsafe_plan_basenames(self):
        for key, value in [('input', '../escape.jar'), ('output', '/escape.jar'),
                           ('manifest', 'nested/pin.pw.toml'), ('input', 'bad\\name.jar'),
                           ('output', 'C:drive.jar'), ('input', 'bad\n.jar')]:
            with self.subTest(key=key, value=value):
                original = self.plan['artifacts'][0][key]
                self.plan['artifacts'][0][key] = value
                self.save_plan()
                self.call(success=False)
                self.plan['artifacts'][0][key] = original

    def test_duplicate_plan_inputs(self):
        for key in ('input', 'output', 'manifest'):
            with self.subTest(key=key):
                original = self.plan['artifacts'][1][key]
                self.plan['artifacts'][1][key] = self.plan['artifacts'][0][key]
                self.save_plan()
                self.call(success=False)
                self.plan['artifacts'][1][key] = original

    def test_ambiguous_input_output(self):
        self.plan['artifacts'][1]['output'] = self.plan['artifacts'][0]['input']
        self.save_plan()
        self.call(success=False)

    def test_duplicate_plan_identities(self):
        self.plan['artifacts'][1].update(project_id=100, file_id=200)
        self.save_plan()
        self.call(success=False)

    def test_invalid_plan_identity_types(self):
        for value in (True, '100', 0, -1, None):
            self.plan['artifacts'][0]['project_id'] = value
            self.save_plan()
            self.call(success=False)

    def test_duplicate_json_keys(self):
        (self.prepared / 'selection.json').write_text('{"schema":1,"schema":2,"artifacts":[]}')
        self.call(success=False)

    def test_symlink_prepared_jar(self):
        jar = self.prepared / self.plan['artifacts'][0]['output']
        other = self.base / 'external.jar'
        jar.rename(other)
        jar.symlink_to(other)
        self.call(success=False)

    def test_symlink_destination(self):
        mods = self.tree()
        external = self.base / 'external-original.jar'
        original = mods / self.plan['artifacts'][0]['input']
        original.rename(external)
        original.symlink_to(external)
        self.call(success=False)
        self.assertEqual(external.read_bytes(), b'original bytes')

    def test_duplicate_client_manifest_identity(self):
        files = [{'projectID': 100, 'fileID': 200}] * 2
        archive = self.client_zip(files=files)
        before = archive.read_bytes()
        self.call('client-zip', archive, success=False)
        self.assertEqual(before, archive.read_bytes())

    def test_missing_client_original_identity(self):
        self.call('client-zip', self.client_zip(files=[]), success=False)

    def test_unsafe_client_overrides_root(self):
        for root in ('../escape', '/absolute', 'a//b', 'a/./b', 'bad\\path', 'C:drive', ''):
            with self.subTest(root=root):
                self.call('client-zip', self.client_zip(root), success=False)

    def test_unsafe_zip_entry(self):
        self.call('client-zip', self.client_zip(extra={'../escape': b'bad'}), success=False)

    def test_ambiguous_zip_paths(self):
        for extra in ({'overrides/mods': b'not a directory'},
                      {'overrides/mods/original-0-bc-runtime.1.jar/': b''}):
            with self.subTest(extra=extra):
                self.call('client-zip', self.client_zip(extra=extra), success=False)

    def test_duplicate_zip_entry(self):
        archive = self.client_zip()
        import warnings
        with warnings.catch_warnings():
            warnings.simplefilter('ignore', UserWarning)
            with zipfile.ZipFile(archive, 'a') as output:
                output.writestr('manifest.json', '{}')
        self.call('client-zip', archive, success=False)

    def test_existing_corrected_output_rejected(self):
        mods = self.tree()
        output = self.plan['artifacts'][0]['output']
        (mods / output).write_bytes(b'existing')
        self.call(success=False)
        self.assertTrue((mods / 'original-0.jar').exists())
        archive = self.client_zip(extra={f'overrides/mods/{output}': b'existing'})
        self.call('client-zip', archive, success=False)

    def interface_selection(self, old_active=True, pointer=False):
        (self.root / 'config/better-content-interface-runtime-overrides.json').write_text('{}')
        if not old_active:
            (self.root / 'config/better-content-runtime-overrides.json').unlink()
        self.interface_directory = self.base / 'interface-prepared'
        self.interface_directory.mkdir()
        self.interface_env = not pointer
        self.interface_plan = {'schema': 'bc.package_runtime_overrides.v1', 'artifacts': [
            {'input': 'epic x parcool-forge-20.12.0.1-1.20.1.jar',
             'output': 'epic x parcool-forge-20.12.0.1-1.20.1-bc-interface.1.jar',
             'manifest': 'epic-parcool.pw.toml', 'project_id': 700, 'file_id': 800},
            {'input': 'valkyrienskies-120-2.4.11.jar',
             'output': 'valkyrienskies-120-2.4.11-bc-interface.1.jar',
             'manifest': 'valkyrienskies.pw.toml', 'project_id': 701, 'file_id': 801}]}
        (self.interface_directory / 'selection.json').write_text(json.dumps(self.interface_plan))
        for entry in self.interface_plan['artifacts']:
            with zipfile.ZipFile(self.interface_directory / entry['output'], 'w') as archive:
                archive.writestr('interface.txt', entry['output'])
        if pointer:
            path = self.root / '.worklane/interface-overrides/current.path'
            path.parent.mkdir(parents=True)
            path.write_text(str(self.interface_directory) + '\n')
        self.active_artifacts = (self.plan['artifacts'] if old_active else []) + self.interface_plan['artifacts']
        self.plan = {'schema': 'bc.package_runtime_overrides.v1', 'artifacts': self.active_artifacts}

    def corrected_bytes(self, entry):
        if entry in getattr(self, 'startup_plan', {}).get('artifacts', []):
            source = self.startup_directory
        elif entry in getattr(self, 'interface_plan', {}).get('artifacts', []):
            source = self.interface_directory
        else:
            source = self.prepared
        return (source / entry['output']).read_bytes()

    def v2_selection(self, pointer=False):
        # Keep the legacy policy/pointer present: the explicit successor wins.
        (self.root / 'config/better-content-runtime-overrides-v2.json').write_text('{}')
        entry = self.plan['artifacts'][-1]
        previous = self.prepared / entry['output']
        entry['output'] = entry['output'].replace('-bc-runtime.1.jar', '-bc-runtime.2.jar')
        previous.rename(self.prepared / entry['output'])
        self.save_plan()
        self.active_artifacts = self.plan['artifacts']
        if pointer:
            path = self.root / '.worklane/runtime-overrides-v2/current.path'
            path.parent.mkdir(parents=True)
            path.write_text(str(self.prepared) + '\n')
            legacy = self.root / '.worklane/runtime-overrides/current.path'
            legacy.parent.mkdir(parents=True)
            legacy.write_text('/missing/legacy-input\n')

    def startup_selection(self, pointer=False, only=False):
        if only:
            (self.root / 'config/better-content-runtime-overrides.json').unlink()
            self.active_artifacts = []
        (self.root / 'config/better-content-startup-runtime-overrides.json').write_text('{}')
        self.startup_directory = self.base / 'startup-prepared'
        self.startup_directory.mkdir()
        self.startup_env = not pointer
        self.startup_plan = {'schema': 'bc.package_runtime_overrides.v1', 'artifacts': [
            {'input': 'alltheleaks-1.1.1+1.20.1-forge.jar',
             'output': 'alltheleaks-1.1.1+1.20.1-forge-bc-startup.1.jar',
             'manifest': 'alltheleaks.pw.toml', 'project_id': 900, 'file_id': 901}]}
        (self.startup_directory / 'selection.json').write_text(json.dumps(self.startup_plan))
        for entry in self.startup_plan['artifacts']:
            with zipfile.ZipFile(self.startup_directory / entry['output'], 'w') as archive:
                archive.writestr('startup.txt', entry['output'])
        if pointer:
            path = self.root / '.worklane/startup-overrides/current.path'
            path.parent.mkdir(parents=True)
            path.write_text(str(self.startup_directory) + '\n')
        self.active_artifacts = self.active_artifacts + self.startup_plan['artifacts']
        self.plan = {'schema': 'bc.package_runtime_overrides.v1', 'artifacts': self.active_artifacts}

    def assert_combined_tree(self, mods):
        self.assertEqual(len(list(mods.glob('*.jar'))), len(self.active_artifacts) + 1)
        for entry in self.active_artifacts:
            self.assertFalse((mods / entry['input']).exists())
            self.assertFalse((mods / entry['manifest']).exists())
            self.assertEqual((mods / entry['output']).read_bytes(), self.corrected_bytes(entry))
        self.assertEqual((mods / 'unrelated.jar').read_bytes(), b'unrelated\x00payload')
        self.assertEqual((mods / 'unrelated.pw.toml').read_bytes(), b'unrelated pin')

    def assert_combined_zip(self, overrides):
        selected_pairs = {(e['project_id'], e['file_id']) for e in self.active_artifacts}
        removed = {f'{overrides}/mods/{e[key]}' for e in self.active_artifacts for key in ('input', 'manifest')}
        with zipfile.ZipFile(self.archive) as archive:
            names = archive.namelist()
            self.assertEqual(len(names), len(set(names)))
            self.assertEqual(archive.comment, b'keep archive comment')
            expected = copy.deepcopy(self.manifest)
            expected['files'] = [item for item in expected['files'] if (item['projectID'], item['fileID']) not in selected_pairs]
            self.assertEqual(json.loads(archive.read('manifest.json')), expected)
            for name, data in self.payloads.items():
                if name in removed:
                    self.assertNotIn(name, names)
                elif name != 'manifest.json':
                    self.assertEqual(archive.read(name), data)
                    self.assertEqual(archive.getinfo(name).comment, b'keep entry comment')
                    self.assertEqual(archive.getinfo(name).extra, b'\xfe\xca\x00\x00')
            for entry in self.active_artifacts:
                name = f'{overrides}/mods/{entry["output"]}'
                self.assertEqual(names.count(name), 1)
                self.assertEqual(archive.read(name), self.corrected_bytes(entry))

    def test_interface_only_environment(self):
        self.interface_selection(old_active=False)
        mods = self.tree()
        self.call(prepared=False)
        self.assert_combined_tree(mods)

    def test_interface_only_pointer(self):
        self.interface_selection(old_active=False, pointer=True)
        mods = self.tree()
        self.call(prepared=False)
        self.assert_combined_tree(mods)

    def test_interface_only_client_zip(self):
        self.interface_selection(old_active=False)
        self.call('client-zip', self.client_zip(), prepared=False)
        self.assert_combined_zip('overrides')

    def test_both_selections_tree(self):
        self.interface_selection()
        mods = self.tree()
        originals = self.base / 'original-inputs'
        originals.mkdir()
        for entry in self.active_artifacts:
            os.link(mods / entry['input'], originals / entry['input'])
            os.link(mods / entry['manifest'], originals / entry['manifest'])
        self.call()
        self.assert_combined_tree(mods)
        for entry in self.active_artifacts:
            self.assertEqual((originals / entry['input']).read_bytes(), b'original bytes')
            self.assertEqual((originals / entry['manifest']).read_bytes(), b'original manifest')

    def test_both_selections_stage_both_sides_resolve_once(self):
        self.interface_selection()
        source_mods = self.tree()
        (self.root / 'mods').mkdir()
        for item in source_mods.iterdir():
            (self.root / 'mods' / item.name).write_bytes(item.read_bytes())
        script = '''resolve_artifacts() { test -f "$1/mods/original-0.jar"; printf '%s\\n' "$2" >> "$1/resolve-calls"; }
stage_side "$3" "$4"'''
        for side in ('server', 'client'):
            target = self.base / side
            self.call(mode=side, target=target, script=script)
            self.assert_combined_tree(target / 'mods')
            self.assertEqual((target / 'resolve-calls').read_text(), side + '\n')
        for entry in self.active_artifacts:
            self.assertEqual((self.root / 'mods' / entry['input']).read_bytes(), b'original bytes')
            self.assertEqual((self.root / 'mods' / entry['manifest']).read_bytes(), b'original manifest')

    def test_both_selections_zip(self):
        self.interface_selection()
        self.call('client-zip', self.client_zip())
        self.assert_combined_zip('overrides')

    def test_both_selections_custom_overrides_root(self):
        self.interface_selection(pointer=True)
        self.call('client-zip', self.client_zip('custom/content'))
        self.assert_combined_zip('custom/content')

    def test_interface_environment_precedes_pointer(self):
        self.interface_selection(old_active=False)
        pointer = self.root / '.worklane/interface-overrides/current.path'
        pointer.parent.mkdir(parents=True)
        pointer.write_text('/missing/invalid-input\n')
        self.tree()
        self.call(prepared=False)
        self.assert_combined_tree(self.target / 'mods')

    def test_interface_environment_without_policy_is_noop(self):
        self.interface_selection(old_active=False)
        (self.root / 'config/better-content-interface-runtime-overrides.json').unlink()
        mods = self.tree()
        before = {p.name: p.read_bytes() for p in mods.iterdir()}
        self.call(prepared=False)
        self.assertEqual(before, {p.name: p.read_bytes() for p in mods.iterdir()})
        archive = self.client_zip()
        before_zip = archive.read_bytes()
        self.call('client-zip', archive, prepared=False)
        self.assertEqual(archive.read_bytes(), before_zip)

    def test_unsafe_interface_selection(self):
        self.interface_selection(old_active=False)
        self.interface_plan['artifacts'][0]['output'] = '../escape.jar'
        (self.interface_directory / 'selection.json').write_text(json.dumps(self.interface_plan))
        self.tree()
        self.call(prepared=False, success=False)
        self.assertTrue((self.target / 'mods' / self.active_artifacts[0]['input']).exists())

    def test_missing_interface_pointer(self):
        (self.root / 'config/better-content-runtime-overrides.json').unlink()
        (self.root / 'config/better-content-interface-runtime-overrides.json').write_text('{}')
        self.call(prepared=False, success=False)

    def test_missing_interface_selection_preserves_originals(self):
        self.interface_selection(old_active=False)
        mods = self.tree()
        (self.interface_directory / 'selection.json').unlink()
        self.call(prepared=False, success=False)
        self.assertTrue(all((mods / entry['input']).exists() for entry in self.active_artifacts))

    def test_malformed_interface_selection_rejects_zip(self):
        self.interface_selection(old_active=False)
        (self.interface_directory / 'selection.json').write_text('{broken')
        archive = self.client_zip()
        original = archive.read_bytes()
        self.call('client-zip', archive, prepared=False, success=False)
        self.assertEqual(archive.read_bytes(), original)

    def test_missing_interface_jar_rejects_zip(self):
        self.interface_selection(old_active=False)
        (self.interface_directory / self.active_artifacts[-1]['output']).unlink()
        archive = self.client_zip()
        original = archive.read_bytes()
        self.call('client-zip', archive, prepared=False, success=False)
        self.assertEqual(archive.read_bytes(), original)

    def test_wrapper_applies_old_then_interface_without_extra_resolution(self):
        script = '''_apply_runtime_selection() { printf '%s:%s:%s:%s\\n' "$1" "$2" "$4" "$5"; }
resolve_artifacts() { printf 'UNEXPECTED RESOLUTION\\n'; return 1; }
apply_runtime_overrides "$3" "$4"'''
        result = self.call(script=script)
        self.assertEqual(result.stdout.splitlines(), [
            f'config/better-content-runtime-overrides.json:.worklane/runtime-overrides/current.path:tree:{self.target}',
            f'config/better-content-interface-runtime-overrides.json:.worklane/interface-overrides/current.path:tree:{self.target}',
            f'config/better-content-startup-runtime-overrides.json:.worklane/startup-overrides/current.path:tree:{self.target}'])
        source = PACKAGE.read_text()
        self.assertEqual(source.count('packwiz curseforge export -o'), 1)

    def test_old_failure_does_not_silently_continue_to_interface(self):
        self.interface_selection(old_active=False)
        # Re-enable old policy without its prepared input: new-only bytes must not mask failure.
        (self.root / 'config/better-content-runtime-overrides.json').write_text('{}')
        self.tree()
        self.call(prepared=False, success=False)
        self.assertTrue(all((self.target / 'mods' / entry['input']).exists() for entry in self.active_artifacts))

    def test_v2_mixed_five_tree(self):
        self.v2_selection()
        mods = self.tree()
        self.call()
        self.assert_combined_tree(mods)
        self.assertEqual(len(list(mods.glob('*-bc-runtime.1.jar'))), 4)
        self.assertEqual(len(list(mods.glob('*-bc-runtime.2.jar'))), 1)

    def test_v2_mixed_five_zip(self):
        self.v2_selection()
        self.call('client-zip', self.client_zip())
        self.assert_combined_zip('overrides')

    def test_v2_pointer_never_uses_legacy_pointer(self):
        self.v2_selection(pointer=True)
        self.tree()
        self.call(prepared=False)
        self.assert_combined_tree(self.target / 'mods')

    def test_v2_missing_pointer_does_not_fall_back_to_v1(self):
        self.v2_selection(pointer=True)
        (self.root / '.worklane/runtime-overrides-v2/current.path').unlink()
        (self.root / '.worklane/runtime-overrides/current.path').write_text(str(self.prepared) + '\n')
        self.tree()
        self.call(prepared=False, success=False)
        self.assertTrue(all((self.target / 'mods' / entry['input']).exists() for entry in self.active_artifacts))

    def test_v2_missing_output_preserves_client_zip(self):
        self.v2_selection()
        (self.prepared / self.plan['artifacts'][-1]['output']).unlink()
        archive = self.client_zip()
        before = archive.read_bytes()
        self.call('client-zip', archive, success=False)
        self.assertEqual(archive.read_bytes(), before)

    def test_all_three_namespaces_tree(self):
        self.v2_selection()
        self.interface_selection()
        self.startup_selection()
        self.tree()
        self.call()
        self.assert_combined_tree(self.target / 'mods')
        self.assertEqual(len(self.active_artifacts), 8)

    def test_all_three_namespaces_zip_custom_root(self):
        self.v2_selection()
        self.interface_selection(pointer=True)
        self.startup_selection(pointer=True)
        self.call('client-zip', self.client_zip('custom/content'))
        self.assert_combined_zip('custom/content')

    def test_startup_only_environment(self):
        self.startup_selection(only=True)
        self.tree()
        self.call(prepared=False)
        self.assert_combined_tree(self.target / 'mods')

    def test_startup_only_pointer_zip(self):
        self.startup_selection(pointer=True, only=True)
        self.call('client-zip', self.client_zip(), prepared=False)
        self.assert_combined_zip('overrides')

    def test_startup_missing_selection_preserves_zip(self):
        self.startup_selection(only=True)
        (self.startup_directory / 'selection.json').unlink()
        archive = self.client_zip()
        before = archive.read_bytes()
        self.call('client-zip', archive, prepared=False, success=False)
        self.assertEqual(archive.read_bytes(), before)

    def test_startup_failure_does_not_masquerade_as_interface_success(self):
        self.interface_selection(old_active=False)
        self.startup_selection()
        (self.startup_directory / 'selection.json').write_text('{broken')
        self.tree()
        self.call(prepared=False, success=False)
        self.assertTrue((self.target / 'mods' / self.startup_plan['artifacts'][0]['input']).exists())

    def test_wrapper_v2_then_interface_then_startup_without_extra_resolution(self):
        self.v2_selection()
        script = '''_apply_runtime_selection() { printf '%s:%s:%s:%s\\n' "$1" "$2" "$4" "$5"; }
resolve_artifacts() { printf 'UNEXPECTED RESOLUTION\\n'; return 1; }
apply_runtime_overrides "$3" "$4"'''
        result = self.call(script=script)
        self.assertEqual(result.stdout.splitlines(), [
            f'config/better-content-runtime-overrides-v2.json:.worklane/runtime-overrides-v2/current.path:tree:{self.target}',
            f'config/better-content-interface-runtime-overrides.json:.worklane/interface-overrides/current.path:tree:{self.target}',
            f'config/better-content-startup-runtime-overrides.json:.worklane/startup-overrides/current.path:tree:{self.target}'])
        self.assertEqual(PACKAGE.read_text().count('packwiz curseforge export -o'), 1)

    def test_resolve_command_still_does_not_apply_overrides(self):
        # Invoke actual resolve entry point on empty manifests without downloads.
        empty = self.base / 'empty-manifests'
        empty.mkdir()
        result = subprocess.run(['bash', str(PACKAGE), 'resolve', str(empty), str(self.target), 'client'],
                                text=True, capture_output=True, timeout=15,
                                env={**os.environ, 'BC_PACKAGE_RUNTIME_OVERRIDES': '/missing/preparation'})
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertFalse((self.target / 'mods').exists())


if __name__ == '__main__':
    unittest.main(verbosity=2)

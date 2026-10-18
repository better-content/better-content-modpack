#!/usr/bin/env python3
"""Focused synthetic CI/checkout/output/export contracts; no source build or refresh."""
import hashlib
import importlib.util
import json
import re
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import zipfile

ROOT = Path(__file__).absolute().parent.parent


def load(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class CIBoundaries(unittest.TestCase):
    def setUp(self):
        # Retain isolated synthetic evidence for the coordinating task's handoff.
        temporary = Path.home() / '.tmp'
        temporary.mkdir(parents=True, exist_ok=True)
        self.base = Path(tempfile.mkdtemp(dir=temporary, prefix='ci-boundary-'))
        print(f'synthetic boundary fixture: {self.base}', flush=True)

    def test_output_roots_follow_checkout_not_home_or_command_cwd(self):
        facade = load('facade', ROOT / 'scripts/bounded_facade.py')
        # Use a checkout outside the approved home temporary directory so the
        # containment failures below cannot accidentally pass through that root.
        with patch.object(facade, '__file__', '/opt/runner/checkout/scripts/bounded_facade.py'), \
             patch.object(Path, 'mkdir'):
            for path in ('/opt/runner/checkout/build/log', '/opt/runner/checkout/generated/log',
                         '/opt/runner/workspace_artifacts/log'):
                self.assertEqual(Path(path), facade.output_path(path))
            for path in ('/opt/runner/checkout/build/../source/log',
                         '/opt/runner/checkout/build-escape/log', '/opt/runner/elsewhere/log'):
                with self.assertRaises(ValueError):
                    facade.output_path(path)
        alias = self.base / 'alias'
        alias.symlink_to(self.base, target_is_directory=True)
        with self.assertRaisesRegex(ValueError, 'symlink'):
            facade.output_path(alias / 'unsafe.log')
        self.assertFalse((self.base / 'unsafe.log').exists())

    def test_workflow_installer_matches_package_requirement_and_checks_pin(self):
        package = (ROOT / 'package.sh').read_text()
        forge = re.search(r'^FORGE_VERSION=(\S+)$', package, re.MULTILINE).group(1)
        workflow = (ROOT / '.github/workflows/pack-tests.yml').read_text()
        installer = f'forge-1.20.1-{forge}-installer.jar'
        self.assertIn(f'https://maven.minecraftforge.net/net/minecraftforge/forge/1.20.1-{forge}/{installer}', workflow)
        self.assertRegex(workflow, rf'[0-9a-f]{{64}}  {re.escape(installer)}')
        self.assertIn('sha256sum --check --strict', workflow)
        self.assertNotIn('./test.main.kts run ', workflow)
        self.assertNotIn('./test.main.kts run ', (ROOT / '.github/workflows/fast-tests.yml').read_text())

    def test_pinned_checkout_contains_build_and_non_src_inspection_inputs(self):
        checkout = load('ci_checkout', ROOT / '.github/scripts/checkout-ci-sources.py')
        origin = self.base / 'origin'
        origin.mkdir()
        def git(*args):
            return subprocess.run(['git', '-C', str(origin), *args], check=True, capture_output=True, text=True)
        git('init')
        files = ('src/main/Example.java', 'build.gradle.kts', 'settings.gradle.kts', 'gradlew',
                 'gradle/wrapper/gradle-wrapper.jar', 'gradle/wrapper/gradle-wrapper.properties',
                 'contracts/inspection.json')
        for name in files:
            target = origin / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(name)
        git('add', '.')
        git('-c', 'user.name=Synthetic', '-c', 'user.email=synthetic@example.invalid', 'commit', '-m', 'fixture')
        revision = git('rev-parse', 'HEAD').stdout.strip()
        manifest = self.base / 'sources.json'
        manifest.write_text(json.dumps({'schema': 'bc.ci_source_revisions.v1', 'repositories': [
            {'repository': 'example-mod', 'commit': revision}]}))
        real_run = subprocess.run
        def local_remote(command, **kwargs):
            if command[-3:-1] == ['add', 'origin']:
                command = [*command[:-1], str(origin)]
            return real_run(command, **kwargs)
        with patch.object(checkout.subprocess, 'run', side_effect=local_remote):
            checkout.checkout(manifest, self.base / 'workspace')
        target = self.base / 'workspace/mod_source/example-mod'
        for name in files:
            self.assertEqual(name, (target / name).read_text())
        with self.assertRaisesRegex(ValueError, 'overwrite'):
            checkout.checkout(manifest, self.base / 'workspace')

    def test_source_checkout_rejects_symlink_boundaries_before_git_or_external_writes(self):
        checkout = load('ci_checkout_boundaries', ROOT / '.github/scripts/checkout-ci-sources.py')
        manifest = self.base / 'sources.json'
        manifest.write_text(json.dumps({'schema': 'bc.ci_source_revisions.v1', 'repositories': [
            {'repository': 'example-mod', 'commit': '0' * 40}]}))
        external = self.base / 'outside'
        external.mkdir()
        for boundary in ('workspace', 'mod_source', 'target'):
            with self.subTest(boundary=boundary):
                workspace = self.base / boundary
                if boundary == 'workspace':
                    workspace.symlink_to(external, target_is_directory=True)
                else:
                    workspace.mkdir()
                    if boundary == 'mod_source':
                        (workspace / 'mod_source').symlink_to(external, target_is_directory=True)
                    else:
                        (workspace / 'mod_source').mkdir()
                        (workspace / 'mod_source/example-mod').symlink_to(external / 'missing')
                with patch.object(checkout.subprocess, 'run') as run:
                    with self.assertRaisesRegex(ValueError, 'symlink'):
                        checkout.checkout(manifest, workspace)
                    run.assert_not_called()
                self.assertEqual([], list(external.iterdir()))
        with patch.object(checkout.subprocess, 'run') as run:
            with self.assertRaisesRegex(ValueError, 'parent traversal'):
                checkout.checkout(manifest, self.base / 'unused/../outside')
            run.assert_not_called()
        self.assertFalse((self.base / 'unused').exists())

    def test_export_selection_excludes_tooling_but_preserves_runtime_launchers(self):
        # Check authored rules and actual Packwiz selection only in a new tiny
        # synthetic fixture. The production index must not be refreshed here.
        fixture = self.base / 'export'
        fixture.mkdir()
        subprocess.run(['git', 'init', str(fixture)], check=True, capture_output=True)
        shutil.copyfile(ROOT / '.packwizignore', fixture / '.gitignore')
        shutil.copyfile(ROOT / '.packwizignore', fixture / '.packwizignore')
        excluded = ('AGENTS.md', 'maintenance.main.kts', 'test-control.main.kts',
                    'pack-test-lock.main.kts', 'pack-test-queue.main.kts', 'plans/task.md',
                    'review/result.json', '.pi/settings.json', '.worklane/task.json',
                    'dist.sh', 'package.sh', 'scripts/check.py')
        included = ('better-world-management-server.sh', 'config/runtime.cfg',
                    'kubejs/server_scripts/runtime.js', 'options.txt')
        for name in (*excluded, *included):
            target = fixture / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text('synthetic runtime input\n')
        result = subprocess.run(['git', '-C', str(fixture), 'check-ignore', '--no-index', '--stdin'],
                                input='\n'.join((*excluded, *included)) + '\n', text=True,
                                capture_output=True, check=True)
        self.assertEqual(set(excluded), set(result.stdout.splitlines()))
        # run.sh/run-forge.sh are intentionally excluded from client exports and
        # explicitly copied into server staging by package.sh; do not change that.
        packager = (ROOT / 'package.sh').read_text()
        for name in ('run.sh', 'run-forge.sh', 'better-world-management-server.sh'):
            self.assertIn(f'"$ROOT/{name}"', packager)
        if not shutil.which('packwiz'):
            self.skipTest('standalone synthetic export needs installed packwiz')
        index = ''.join(f'[[files]]\nfile = "{name}"\nhash = "{hashlib.sha256((fixture / name).read_bytes()).hexdigest()}"\n\n'
                        for name in included)
        (fixture / 'index.toml').write_text('hash-format = "sha256"\n\n' + index)
        digest = hashlib.sha256((fixture / 'index.toml').read_bytes()).hexdigest()
        (fixture / 'pack.toml').write_text('name = "Synthetic boundary"\nversion = "1"\npack-format = "packwiz:1.1.0"\n'
            f'[index]\nfile = "index.toml"\nhash-format = "sha256"\nhash = "{digest}"\n'
            '[versions]\nminecraft = "1.20.1"\nforge = "47.4.22"\n')
        # A synthetic-only refresh exercises Packwiz's own ignore implementation.
        # No real pack content, downloads, deployment or package.sh is involved.
        subprocess.run(['packwiz', '--cache', str(self.base / 'cache'), 'refresh'], cwd=fixture,
                       check=True, capture_output=True, timeout=30)
        output = self.base / 'synthetic.zip'
        subprocess.run(['packwiz', '--cache', str(self.base / 'cache'), 'curseforge', 'export',
                        '-o', str(output), '-s', 'client', '-y'], cwd=fixture,
                       check=True, capture_output=True, timeout=30)
        with zipfile.ZipFile(output) as archive:
            names = set(archive.namelist())
        self.assertTrue({f'overrides/{name}' for name in included} <= names)
        self.assertFalse({f'overrides/{name}' for name in excluded} & names)


if __name__ == '__main__':
    unittest.main()

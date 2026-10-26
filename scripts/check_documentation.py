#!/usr/bin/env python3
"""Minecraft-free maintained-document links and executable provider identity checks."""
import argparse
import json
from pathlib import Path
import re
import subprocess
import sys
import tomllib
from urllib.parse import unquote, urlsplit

PACK = Path(__file__).resolve().parents[1]
WORKSPACE = PACK.parent


def files(repo):
    result = subprocess.run(['git', '-C', str(repo), 'ls-files', '-z', '--cached', '--others', '--exclude-standard'],
                            capture_output=True, check=True)
    # Retained task reports are outputs, not maintained documentation inputs.
    return sorted({repo / p.decode() for p in result.stdout.split(b'\0')
                   if p and not p.startswith(b'workspace_artifacts/')
                   and p.decode().endswith('.md') and (repo / p.decode()).is_file()})


def prose(text):
    return re.sub(r'^(```|~~~).*?^\1[^\n]*$', '', text, flags=re.M | re.S)


def anchors(text):
    result = set(re.findall(r'<a\s+(?:id|name)=["\']([^"\']+)', text))
    counts = {}
    for heading in re.findall(r'^#{1,6}\s+(.+?)\s*#*$', text, re.M):
        value = re.sub(r'\[([^]]+)\]\([^)]*\)', r'\1', heading)
        value = re.sub(r'[^\w\s-]', '', value.lower()).replace(' ', '-')
        n = counts.get(value, 0)
        counts[value] = n + 1
        result.add(value if n == 0 else value + '-' + str(n))
    return result


def provider_ids(repo):
    """Read owned mod declarations, not dependency IDs or removed agent prose."""
    template = (repo / 'src/main/resources/META-INF/mods.toml').read_text()
    properties = repo / 'gradle.properties'
    values = dict(re.findall(r'^\s*([\w.]+)\s*=\s*(.*?)\s*$', properties.read_text(), re.M)) if properties.exists() else {}
    for build in (repo / 'build.gradle', repo / 'build.gradle.kts'):
        if build.exists():
            values.update(re.findall(r'\b(modId|mod_id)\s*=\s*[\'"]([a-z0-9_]+)[\'"]', build.read_text()))
    if 'modId' in values:
        values.setdefault('mod_id', values['modId'])
    # Kotlin builds commonly expose the mod_id property as the modId template key.
    if 'mod_id' in values:
        values.setdefault('modId', values['mod_id'])
    expanded = re.sub(r'\$\{(\w+)\}', lambda m: values.get(m[1], m[0]), template)
    metadata = tomllib.loads(expanded)
    return {mod['modId'] for mod in metadata.get('mods', [])}


def check(repositories):
    errors = []
    checked = 0
    for repo in repositories:
        for path in files(repo):
            checked += 1
            text = path.read_text()
            for destination in re.findall(r'\[[^\]\n]*\]\(([^\n)]*)\)', prose(text)):
                destination = destination.strip().split(' "', 1)[0].strip('<>')
                if not destination or urlsplit(destination).scheme or destination.startswith('//'):
                    continue
                dest, _, fragment = unquote(destination).partition('#')
                target = (path.parent / dest).resolve() if dest else path
                if not target.exists():
                    errors.append(f'{path.relative_to(WORKSPACE)}: missing link {destination}')
                elif fragment and target.is_file() and target.suffix == '.md' and fragment not in anchors(target.read_text()):
                    errors.append(f'{path.relative_to(WORKSPACE)}: missing anchor {destination}')
            if path.name not in {'CHANGELOG.md', 'LICENSE-NOTICE.md', 'LICENSE.md', 'THIRD_PARTY_NOTICES.md'}:
                if re.search(r'(?:/home/dev/)?workspace_artifacts/(?:reviews|evidence|deliverables)/[^`\s]*\d{8}', text):
                    errors.append(f'{path.relative_to(WORKSPACE)}: historical artifact path in maintained documentation')
    for name in ('gameplay-design-choices.md', 'aspirational-features.md'):
        if not (PACK / 'docs' / name).is_file():
            errors.append('missing maintained design document: docs/' + name)
    manifest = json.loads((PACK / 'gradle/active-custom-mods.json').read_text())['mods']
    names = {m['repository'] for m in manifest}
    for mod in manifest:
        repo = WORKSPACE / 'mod_source' / mod['repository']
        if not (repo / '.git').exists():
            errors.append('missing active source: ' + mod['repository'])
        else:
            try:
                if mod['modId'] not in provider_ids(repo):
                    errors.append('source/manifest identity mismatch: ' + mod['repository'])
            except (OSError, ValueError, KeyError) as error:
                errors.append(f'{mod["repository"]}: cannot read provider identity: {error}')
        if not set(mod.get('dependsOn', [])) <= names:
            errors.append('unknown dependency: ' + mod['repository'])
    pending = {m['repository']: set(m.get('dependsOn', [])) for m in manifest}
    while pending:
        ready = {name for name, deps in pending.items() if not deps}
        if not ready:
            errors.append('provider build graph is cyclic')
            break
        pending = {name: deps - ready for name, deps in pending.items() if name not in ready}
    print(f'Checked {checked} maintained Markdown files, {len(repositories)} repositories and '
          f'{len(manifest)} active providers against executable metadata.')
    for error in errors:
        print(error, file=sys.stderr)
    return 1 if errors else 0


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo', type=Path, help='limit document scan to one repository; shared contracts still checked')
    args = parser.parse_args()
    repositories = [args.repo.resolve()] if args.repo else [PACK] + sorted(
        p for p in (WORKSPACE / 'mod_source').iterdir() if (p / '.git').exists())
    sys.exit(check(repositories))

#!/usr/bin/env python3
"""Minecraft-free maintained-document links, authority and local-command contracts."""
import argparse
import json
from pathlib import Path
import re
import subprocess
import sys
from urllib.parse import unquote, urlsplit

PACK = Path(__file__).resolve().parents[1]
WORKSPACE = PACK.parent


def files(repo):
    result = subprocess.run(['git', '-C', str(repo), 'ls-files', '-z', '--cached', '--others', '--exclude-standard'],
                            capture_output=True, check=True)
    return sorted({repo / p.decode() for p in result.stdout.split(b'\0')
                   if p and p.decode().endswith('.md') and (repo / p.decode()).is_file()})


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
            if path == repo / 'README.md' and repo != PACK:
                if '## Scope and authority' not in text or 'better-content-modpack/docs/README.md' not in text:
                    errors.append(f'{repo.name}: README lacks canonical authority entry')
            if path == repo / 'AGENTS.md' and repo != PACK:
                if 'docs/policies/workspace.md' not in text:
                    errors.append(f'{repo.name}: AGENTS lacks shared policy link')
                build_files = [repo / 'build.gradle', repo / 'build.gradle.kts'] + \
                    list((repo / 'gradle').glob('*.gradle')) + list((repo / 'gradle').glob('*.gradle.kts'))
                build = '\n'.join(p.read_text() for p in build_files if p.exists())
                for command in re.findall(r'`\./gradlew ([^`]+)`', text):
                    for task in command.split():
                        if task.startswith('-') or task in {'build', 'clean', 'test', 'runData'}:
                            continue
                        if task not in build:
                            errors.append(f'{repo.name}: task {task} absent from build definition')
    manifest = json.loads((PACK / 'gradle/active-custom-mods.json').read_text())['mods']
    names = {m['repository'] for m in manifest}
    for mod in manifest:
        repo = WORKSPACE / 'mod_source' / mod['repository']
        if not (repo / '.git').exists():
            errors.append('missing active source: ' + mod['repository'])
        elif mod['modId'] not in (repo / 'AGENTS.md').read_text():
            errors.append('AGENTS/manifest identity mismatch: ' + mod['repository'])
        if not set(mod.get('dependsOn', [])) <= names:
            errors.append('unknown dependency: ' + mod['repository'])
    pending = {m['repository']: set(m.get('dependsOn', [])) for m in manifest}
    while pending:
        ready = {name for name, deps in pending.items() if not deps}
        if not ready:
            errors.append('provider build graph is cyclic')
            break
        pending = {name: deps - ready for name, deps in pending.items() if name not in ready}
    pi = WORKSPACE / '.pi/agent/AGENTS.md'
    codex = WORKSPACE / '.codex/AGENTS.md'
    guide = WORKSPACE / '.config/agent-guidance/lane.md'
    if not guide.is_file() or pi.is_symlink() or codex.is_symlink() or pi.read_bytes() != codex.read_bytes():
        errors.append('global frozen instruction parity failed')
    if str(guide) not in pi.read_text():
        errors.append('global entry does not identify canonical guide')
    for base in (pi.parent, codex.parent):
        override = base / 'AGENTS.override.md'
        if override.exists() and override.read_text().strip():
            errors.append('effective override shadows global parity: ' + str(override))
    print(f'Checked {checked} maintained Markdown files, {len(repositories)} repositories, '
          f'{len(manifest)} active providers and global instruction parity.')
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

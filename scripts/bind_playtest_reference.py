#!/usr/bin/env python3
"""Read-only verification of an explicit immutable release reference; never launches or packages anything."""
import argparse
import hashlib
import json
import re
from pathlib import Path


def sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def load(path):
    return json.loads(path.read_text(encoding='utf-8'))


def bound(ref):
    path = Path(ref['path']).resolve(strict=True)
    assert sha(path) == ref['sha256'], f'changed proof: {path}'
    return path


def bind(ready_path, package_path):
    ready_path = ready_path.resolve(strict=True)
    package_path = package_path.resolve(strict=True)
    ready, package = load(ready_path), load(package_path)
    assert ready_path.parent == package_path.parent
    assert ready['schema'] == 'bc.release_ready.v4' and ready['status'] == 'passed'
    assert package['schema'] == 'bc.package_ready.v1' and package['status'] == 'packaged'
    assert ready['run_id'] == package['run_id'] and ready['candidates'] == package['candidates']
    assert ready['package_ready_sha256'] == sha(package_path)
    for kind in ('client', 'server'):
        candidate = Path(package['candidates'][kind]).resolve(strict=True)
        assert candidate.parent.parent == ready_path.parent
        assert sha(candidate) == package['candidates'][f'{kind}_sha256'], f'changed {kind} archive'
    proofs = {}
    for name in ('provenance.json', 'archive-members.json'):
        ref = package['proofs'][name]
        assert package['proofs_sha256'][name] == ref['sha256']
        proofs[name] = bound(ref)
    provenance, inventory = load(proofs['provenance.json']), load(proofs['archive-members.json'])
    assert provenance['schema'] == 'bc.fresh_dist_provenance.v1'
    assert inventory['schema'] == 'bc.archive_members.v1'
    for doc in (provenance, inventory):
        assert doc['candidates']['client_sha256'] == package['candidates']['client_sha256']
        assert doc['candidates']['server_sha256'] == package['candidates']['server_sha256']
    members = inventory['members']
    assert members and len({row['path'] for row in members}) == len(members)
    sources = provenance['source_updates']; mods = provenance['mods']
    assert len(mods) == len(sources) == 53
    assert {m['repository'] for m in mods} == {s['repository'] for s in sources}
    for mod in mods:
        packaged = [row for row in members if row['path'].split('/')[-1] == mod['artifact']]
        assert packaged and all(row['sha256'] == mod['sha256'] for row in packaged), mod['repository']
    return {
        'schema': 'bc.playtest_reference.v1', 'status': 'reference-only',
        'release': ready_path.parent.name, 'run_id': ready['run_id'],
        'ready': {'path': str(ready_path), 'sha256': sha(ready_path)},
        'package': {'path': str(package_path), 'sha256': sha(package_path)},
        'provenance': {'path': str(proofs['provenance.json']), 'sha256': sha(proofs['provenance.json'])},
        'inventory': {'path': str(proofs['archive-members.json']), 'sha256': sha(proofs['archive-members.json'])},
        'candidate': {key: package['candidates'][key] for key in ('client_sha256', 'server_sha256')},
        'sources': [{
            'repository': mod['repository'], 'artifact': mod['artifact'], 'artifact_sha256': mod['sha256'],
            'bundled_commit': next(s['bundled_commit'] for s in sources if s['repository'] == mod['repository']),
            'production_fingerprint': next(s['production_fingerprint'] for s in sources if s['repository'] == mod['repository']),
            'source_match': 'unverified' if next(s['production_fingerprint'] for s in sources if s['repository'] == mod['repository']) is None else 'fingerprint-reported'
        } for mod in sorted(mods, key=lambda item: item['repository'])],
        'qualification': 'not-established; source/test/provider/toolchain/configuration fingerprints require independent candidate-bound proof'
    }


# Narrow source-only oracle spot reviews; not complete gameplay-promise or runtime qualification.
PREFERRED_SOURCE_METHOD = {
    'better-runtime-diagnostics': 'globalBudgetSummariesAndTeardownDoNotRearmAllowance',
    'better-spaceflight': 'rejectsNetherHeightBeforeNativeCopy',
}
SOURCE_ORACLE_REVIEW = {
    'better-airtight-machines', 'better-arena-trials', 'better-block-placement-preview',
    'better-bumblezone-crops', 'better-buried-encounters', 'better-celestial-events',
    'better-chemlib-hazards', 'better-craftable-bouquets', 'better-create-kinetic-loss',
    'better-create-train-fuel', 'better-deaths-door', 'better-discovery-guides',
    'better-drinking-water', 'better-electrochemistry', 'better-exploration-load-control',
    'better-gameplay-notices', 'better-hexerei-dynamic-trees', 'better-magic-chunk-anchors',
    'better-malum-dynamic-trees', 'better-oc2r-create-controls', 'better-oc2r-wireless-messaging',
    'better-ore-geology', 'better-pillager-campaigns', 'better-player-traces',
    'better-rail-beetle', 'better-regolith-farming', 'better-rehooked-grappling',
    'better-rpg-progression', 'better-seasonal-daylight', 'better-settlement-roads',
    'better-spawns', 'better-spicy-farmers', 'better-spirit-commerce',
    'better-survival-hud', 'better-village-fortifications',
    'better-weathering-offline-progress', 'better-wild-fruit-trees', 'scalable-tnt',
    'better-runtime-diagnostics', 'better-spaceflight',
}


def source_coverage(reference, sources_root):
    """Map source-declared test methods, never claim runtime discovery or packaged-source equality."""
    for source in reference['sources']:
        repository = sources_root / source['repository']
        assert repository.is_dir(), f'missing source checkout: {repository}'
        entries = []
        for file in sorted((repository / 'src').rglob('*')):
            if file.suffix not in ('.kt', '.java') or not file.is_file():
                continue
            relative = file.relative_to(repository).as_posix()
            if not (relative.startswith('src/test/') or 'gametest' in file.name.lower() or '/test/' in relative.lower()):
                continue
            text = file.read_text(encoding='utf-8', errors='replace')
            package = re.search(r'(?m)^\s*package\s+([\w.]+)\s*;?', text)
            # Source annotation counts are a *discovery requirement*, not a run result.
            annotation = None
            owner_decl = re.search(r'(?m)^\s*(?:(?:public|private|internal|abstract|final|open)\s+)*(?:class|object)\s+(\w+)', text)
            owner = owner_decl.group(1) if owner_decl else file.stem
            for index, line in enumerate(text.splitlines(), 1):
                if re.search(r'@(?:Test|ParameterizedTest|RepeatedTest|GameTest)\b', line):
                    annotation = (index, 'native' if 'GameTest' in line else 'unit')
                    line = re.sub(r'^\s*@(Test|ParameterizedTest|RepeatedTest)\b\s*', '', line)
                match = re.search(r'\bfun\s+(`[^`]+`|\w+)\s*\(', line)
                if not match:
                    match = re.search(r'^\s*(?:(?:public|private|protected|static|final|synchronized)\s+)*void\s+(\w+)\s*\(', line)
                if match and annotation and index - annotation[0] <= 8:
                    entries.append({'source': relative, 'class': (package.group(1) + '.' if package else '') + owner,
                                    'method': match.group(1).strip('`'), 'kind': annotation[1], 'line': index,
                                    'state': 'annotated-source-only; discovery-and-execution-unverified'})
                    annotation = None
                elif annotation and index - annotation[0] > 8:
                    annotation = None
        build_file = repository / 'build.gradle.kts'
        if not build_file.exists():
            build_file = repository / 'build.gradle'
        build_text = build_file.read_text(encoding='utf-8') if build_file.exists() else ''
        tasks = [name for name in ('test', 'verifyFast', 'verifyFull', 'headlessGameTest', 'runGameTestServer')
                 if re.search(r'tasks\.(?:register|named)\s*(?:<[^>]+>)?\s*\(\s*["\']' + name + r'["\']|tasks\.' + name + r'\b', build_text)]
        if 'test' not in tasks and 'useJUnitPlatform' in build_text:
            tasks.insert(0, 'test')
        workflows = []
        for path in sorted((repository / '.github/workflows').glob('*.y*ml')):
            for line in path.read_text(encoding='utf-8').splitlines():
                if 'run:' in line and ('gradlew' in line or 'mvn ' in line):
                    workflows.append({'file': path.relative_to(repository).as_posix(), 'command': line.split('run:', 1)[1].strip()})
        source['test_methods'] = entries
        source['source_annotated_unit_methods'] = sum(item['kind'] == 'unit' for item in entries)
        source['source_annotated_native_methods'] = sum(item['kind'] == 'native' for item in entries)
        source['provider_requirements_observed'] = [
            {'repository': provider, 'artifact': artifact}
            for provider, artifact in sorted(set(re.findall(r'betterContentJar\s*\(\s*"([^"]+)"\s*,\s*"([^"]+)"\s*\)', build_text)))]
        source['provider_environment'] = 'BC_CUSTOM_MOD_JAR_DIR' if 'BC_CUSTOM_MOD_JAR_DIR' in build_text else None
        source['declared_test_tasks'] = [f'./gradlew {task}' for task in tasks]
        source['ci_commands_observed'] = workflows
        source['ci_status'] = 'explicit-run-command-observed' if workflows else 'no inline Gradle/Maven run command observed; inspect workflows manually'
        source['test_discovery'] = 'unverified'
        source['native_runtime_cases'] = 'unverified'
        source['source_to_artifact'] = 'unverified' if source['production_fingerprint'] is None else 'requires independent review'
        source['expected_report_fingerprints'] = {
            'source_sha256': source['production_fingerprint'], 'tests_sha256': None,
            'providers_sha256': None, 'toolchain_sha256': None,
            'configuration_sha256': None, 'artifact_sha256': source['artifact_sha256'],
            'state': 'incomplete; missing fields cannot be inferred from checkout, report or bundled commit'
        }
        source['local_checkout'] = str(repository.resolve())
        assessment = repository / 'docs/playtest-qualification.md'
        source['assessment'] = {'path': str(assessment), 'sha256': sha(assessment)} if assessment.is_file() else None
        # Cite the owning assessment's exact source text, but never promote it to packaged truth.
        if assessment.is_file():
            paragraphs = assessment.read_text(encoding='utf-8').split('\n\n')
            heading = next((i for i, paragraph in enumerate(paragraphs)
                            if re.search(r'(?im)^#{1,3} .*\b(?:shipped|active|primary|scope|promise)\b', paragraph)), None)
            source['assessment_promise_excerpt'] = (paragraphs[heading + 1][:1500] if heading is not None and heading + 1 < len(paragraphs)
                                                   else next((p[:1500] for p in paragraphs[1:] if p and not p.startswith('#')), None))
        else:
            source['assessment_promise_excerpt'] = None
        # An asserted source line is a concrete authored oracle, not a verified runtime result.
        stop = {'the', 'and', 'for', 'with', 'from', 'this', 'that', 'source', 'actual', 'native', 'pack', 'test', 'player', 'mod', 'only', 'not', 'are'}
        claim_words = {word.lower() for word in re.findall(r'[A-Za-z][a-z]{3,}', source['assessment_promise_excerpt'] or '')} - stop
        ranked = []
        for method in entries:
            text_lines = (repository / method['source']).read_text(encoding='utf-8', errors='replace').splitlines()
            begin = method['line']
            # Limit scanning to this method's nearby assertions; do not attach a distant method's oracle.
            end = min(len(text_lines), begin + 35)
            following = [(i + 1, text_lines[i].strip()) for i in range(begin - 1, end)]
            oracles = [(line, (' '.join(text_lines[line - 1:min(len(text_lines), line + 3)]).strip().split(';', 1)[0] + ';')[:360]
                        if ';' in ' '.join(text_lines[line - 1:min(len(text_lines), line + 3)])
                        else ' '.join(text_lines[line - 1:min(len(text_lines), line + 2)]).strip()[:360])
                       for line, body in following
                       if re.search(r'\b(?:assert(?:True|False|Equals|Equal|NotNull|Null|That|Throws|Same|ArrayEquals|BlockPresent|BlockNotPresent|EntityPresent|EntityNotPresent)|check|require|GameTestAssertException|AssertionError)\b', body)
                       and not body.startswith('//')]
            if not oracles:
                continue
            label = (method['method'] + ' ' + method['class'] + ' ' + method['source']).lower()
            matches = sorted(word for word in claim_words if word in label)
            ranked.append((len(matches), method, oracles[0], matches))
        preferred = PREFERRED_SOURCE_METHOD.get(source['repository'])
        ranked.sort(key=lambda item: (0 if preferred and item[1]['method'] == preferred else 1,
                                      -item[0], item[1]['source'], item[1]['line']))
        source['promise_oracle_candidates'] = [{
            'assessment': source['assessment'], 'assessment_excerpt': source['assessment_promise_excerpt'],
            'test': ranked[0][1], 'oracle_source_line': ranked[0][2][0],
            'oracle_expression_excerpt': ranked[0][2][1], 'matching_claim_terms': ranked[0][3],
            'state': ('source-reviewed-narrow-oracle; full promise and runtime unverified' if source['repository'] in SOURCE_ORACLE_REVIEW
                      else 'authored-source-oracle-candidate; semantic equivalence and runtime unverified')
        }] if ranked and (ranked[0][0] > 0 or ranked[0][1]['method'] == preferred) else []
        source['unresolved'] = ['exact raw JUnit/native runtime discovery and successful execution',
                                'exact source/tests/providers/toolchain/configuration hashes for the packaged artifact',
                                'client, persistence and 8/16/32 multiplayer proof where applicable']
    reference['coverage'] = {'concretely_named_source_test_entrypoints': sum(len(s['test_methods']) for s in reference['sources']),
                             'annotated_native_method_candidates': sum(s['source_annotated_native_methods'] for s in reference['sources']),
                             'source_reviewed_narrow_oracle_mappings': sum(s['repository'] in SOURCE_ORACLE_REVIEW and bool(s['promise_oracle_candidates']) for s in reference['sources']),
                             'reviewed_complete_shipped_promise_assertions': 0,
                             'concretely_executed_and_candidate_bound_assertions': 0,
                             'unresolved_native_registration_repositories': sum(s['source_annotated_native_methods'] > 0 for s in reference['sources']),
                             'ci_without_inline_run_command': sum(not s['ci_commands_observed'] for s in reference['sources']),
                             'source_promise_oracle_candidates': sum(bool(s['promise_oracle_candidates']) for s in reference['sources']),
                             'source_to_packaged_artifact_unverified_repositories': sum(s['source_match'] == 'unverified' for s in reference['sources']),
                             'scope': 'all 53 shipped source repositories; annotated methods are static candidates, not proven tests'}
    return reference


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--ready', type=Path, required=True)
    parser.add_argument('--package', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--sources-root', type=Path, help='Optional read-only source checkout root for static annotation inventory')
    args = parser.parse_args()
    try:
        result = bind(args.ready, args.package)
        if args.sources_root:
            result = source_coverage(result, args.sources_root)
        args.output.parent.mkdir(parents=True, exist_ok=True)
        with args.output.open('x', encoding='utf-8') as output:
            json.dump(result, output, indent=2)
            output.write('\n')
    except (AssertionError, KeyError, ValueError, OSError) as error:
        parser.error(f'unbound reference: {error}')


if __name__ == '__main__':
    main()

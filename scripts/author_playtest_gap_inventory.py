#!/usr/bin/env python3
"""Capture verbatim, cited unresolved-assertion sections from all 53 source assessments.

Pure read-only source inventory transformation. It does not run, authorize, or pass tests.
"""
import argparse
import hashlib
import json
import re
from pathlib import Path

HEADING = re.compile(r'^##? (.+)$')
GAP = re.compile(r'gap|blocker|remain|still missing|unrun|release boundary|acceptance|fixture acquisition|outstanding|qualification|proof required', re.I)

def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--reference', required=True, type=Path)
    parser.add_argument('--sources-root', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    reference = json.loads(args.reference.read_text())
    assert reference['schema'] == 'bc.playtest_reference.v1' and len(reference['sources']) == 53
    entries = []
    for source in reference['sources']:
        repo = source['repository']
        assessment = args.sources_root / repo / 'docs/playtest-qualification.md'
        lines = assessment.read_text().splitlines()
        headers = [(i, m.group(1)) for i, text in enumerate(lines) if (m := HEADING.match(text))]
        open_sections = []
        for index, (start, title) in enumerate(headers):
            if not GAP.search(title): continue
            stop = headers[index + 1][0] if index + 1 < len(headers) else len(lines)
            quote = '\n'.join(lines[start:stop]).strip()
            if quote:
                open_sections.append(dict(heading=title, start_line=start + 1, end_line=stop,
                                          exact_assessment_text=quote))
        assert open_sections, f'{repo}: no cited unresolved assessment section; review manually'
        entries.append(dict(repository=repo, assessment=str(assessment.resolve()),
                            assessment_sha256=sha(assessment), open_required_assertions=open_sections,
                            state='not yet candidate-bound or executed; source-only assessment text'))
    assert len(entries) == 53 and len({entry['repository'] for entry in entries}) == 53
    document = dict(schema='bc.playtest_repository_missing_assertions.v1',
                    status='verbatim-source-assessment-gap-inventory-not-a-pass',
                    reference_sha256=sha(args.reference), source_repositories=53,
                    candidate_bound_executed_assertions=0, entries=entries)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open('x') as out:
        json.dump(document, out, indent=2); out.write('\n')
    print('Captured cited, verbatim unresolved assertion sections for 53 source repositories; no tests run.')

if __name__ == '__main__': main()

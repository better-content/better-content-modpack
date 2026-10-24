#!/usr/bin/env python3
"""Pre-invocation exact-2x budget selection from explicitly named raw observations.

BC_TIMING_BASELINES is a JSON array of 1..64 absolute raw-event paths (32 MiB
aggregate, reducer limits apply). No history discovery, report/catalog import, tick
conversion, trimming, padding, or live cap updates. Unknown/invalid inputs retain
the independently declared legacy safety cap, without a compliance claim.

API: catalog(paths=None); resolve(fields, fallback_ns, paths=None);
resolve_catalog(fields, fallback_ns, snapshot) for a frozen in-memory catalog
computed by catalog in this invocation. A catalog is NOT an authenticated receipt.
CLI: catalog; resolve --fields-json JSON --fallback-ns INTEGER.
"""
import argparse
import json
import os
from pathlib import Path
import importlib.util

# Private sibling dependency: embedding through sitecustomize must not alter sys.path,
# shadow application modules, or replace an existing timing_report module.
_reducer_spec = importlib.util.spec_from_file_location('_bc_budget_private_reducer',
                                                       Path(__file__).with_name('timing_report.py'))
reducer = importlib.util.module_from_spec(_reducer_spec)
_reducer_spec.loader.exec_module(reducer)

POLICY_UNKNOWN = 'UNKNOWN_UNTIL_COMPARABLE_SUCCESS_MEASURED'
POLICY_EXACT = 'EXACT_2X_EMPIRICAL_MEDIAN'


def _paths(paths):
    if paths is None:
        raw = os.environ.get('BC_TIMING_BASELINES', '')
        if len(raw) > 65536:
            raise ValueError('baseline ingress exceeds 65536 characters')
        paths = json.loads(raw, object_pairs_hook=reducer._object) if raw else []
    if not isinstance(paths, (list, tuple)) or not 0 < len(paths) <= reducer.MAX_FILES:
        raise ValueError('expected 1..64 explicit absolute raw-event paths')
    result = []
    for path in paths:
        if not isinstance(path, (str, Path)) or len(str(path)) > 4096 or not Path(path).is_absolute():
            raise ValueError('baseline path must be absolute and bounded')
        result.append(Path(path))
    return result


def catalog(paths=None):
    """Bounded validated eligible groups from a fresh raw-record snapshot only."""
    result = dict(schema='bc.timing_budget_catalog.v1', status='UNKNOWN', reason=None,
                  sources=[], groups=[])
    try:
        report = reducer.reduce_records(_paths(paths))
        result['sources'] = report['sources']
        if report['status'] != 'COMPLETE':
            result['reason'] = 'raw observations are ' + report['status']
            return result
        for group in report['groups']:
            samples = group['samples']
            if group['missing_identity'] or not samples:
                continue
            values = sorted(sample['duration_ns'] for sample in samples)
            middle = len(values) // 2
            twice = values[middle] * 2 if len(values) % 2 else values[middle - 1] + values[middle]
            # JVM Duration and the raw schema share signed-int64 nanoseconds. Never
            # saturate, round, pad a zero measurement, or wrap an unrepresentable cap.
            if not 0 < twice <= reducer.MAX_INTEGER:
                continue
            numerator, denominator = (twice // 2, 1) if twice % 2 == 0 else (twice, 2)
            result['groups'].append(dict(identity=group['identity'], samples=samples,
                                         successful_sample_count=len(samples), effective_timeout_ns=twice,
                                         expected_runtime_ns=numerator if denominator == 1 else None,
                                         expected_runtime_rational=dict(numerator=numerator, denominator=denominator)))
        result.update(status='VALID', reason='validated complete raw observations')
    except (ValueError, TypeError, OSError, RecursionError) as error:
        result['reason'] = str(error)
    return result


def resolve_catalog(fields, fallback_ns, snapshot):
    """Select once from this invocation's catalog; caller freezes returned decision."""
    if type(fallback_ns) is not int or not 0 < fallback_ns <= reducer.MAX_INTEGER:
        raise ValueError('fallback_ns must be positive signed-int64 nanoseconds')
    result = dict(effective_timeout_ns=fallback_ns, expected_runtime_ns=None,
                  expected_runtime_rational=None, successful_sample_count=0,
                  timeout_policy=POLICY_UNKNOWN, baseline_sources=snapshot.get('sources', []),
                  baseline_samples=[], selection_reason=snapshot.get('reason'),
                  selection_method='empirical_median_all_comparable_successes',
                  selection_frozen_before_invocation=True, legacy_safety_cap_ns=fallback_ns)
    try:
        identity, missing = reducer._group_identity(fields)
    except (KeyError, TypeError, ValueError):
        result['selection_reason'] = 'unsupported or missing scope identity'
        return result
    if missing:
        result['selection_reason'] = 'unknown identity: ' + ','.join(missing)
        return result
    if snapshot.get('status') != 'VALID':
        return result
    match = next((g for g in snapshot['groups'] if g['identity'] == identity), None)
    if match is None:
        result['selection_reason'] = 'no comparable successful representable sample group'
        return result
    rational = match['expected_runtime_rational']
    result.update(effective_timeout_ns=match['effective_timeout_ns'],
                  expected_runtime_ns=rational['numerator'] if rational['denominator'] == 1 else None,
                  expected_runtime_rational=rational, successful_sample_count=match['successful_sample_count'],
                  timeout_policy=POLICY_EXACT, baseline_samples=match['samples'],
                  selection_reason='exact comparable successful raw samples')
    return result


def resolve(fields, fallback_ns, paths=None):
    return resolve_catalog(fields, fallback_ns, catalog(paths))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='action', required=True)
    commands.add_parser('catalog')
    select = commands.add_parser('resolve')
    select.add_argument('--fields-json', required=True)
    select.add_argument('--fallback-ns', type=int, required=True)
    args = parser.parse_args()
    result = catalog() if args.action == 'catalog' else resolve(
        json.loads(args.fields_json, object_pairs_hook=reducer._object), args.fallback_ns)
    print(json.dumps(result, allow_nan=False, separators=(',', ':')))


if __name__ == '__main__':
    main()

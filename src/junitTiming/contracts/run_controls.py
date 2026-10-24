#!/usr/bin/env python3
"""Direct JDK-only SPI controls. No Gradle, Minecraft, caches, downloads or production baseline.
Usage: python3 -B src/junitTiming/contracts/run_controls.py --api-dir ABS_GRADLE_LIB_PLUGINS --output ABS_FRESH_DIR
All outputs retained, including deliberate worker exit 124 and unhealthy receipts.
"""
import argparse
import json
import os
from pathlib import Path
import subprocess
import time


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--api-dir', type=Path)
    parser.add_argument('--api-jar', type=Path, action='append', default=[])
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[3]
    output = args.output
    if not output.is_absolute() or output.exists():
        raise ValueError('fresh absolute retained output required')
    output.mkdir(parents=True)
    classes = output / 'classes'
    classes.mkdir()
    patterns = ('junit-platform-*.jar', 'apiguardian-api-*.jar', 'opentest4j-*.jar')
    jars = sorted(set(args.api_jar) | ({p for pattern in patterns for p in args.api_dir.glob(pattern)} if args.api_dir else set()))
    if not any('launcher' in p.name for p in jars):
        raise ValueError('actual available launcher API required')
    api = os.pathsep.join(map(str, jars))
    sources = sorted((root / 'src/junitTiming/java').rglob('*.java')) + sorted(Path(__file__).parent.glob('*.java'))
    compile_result = subprocess.run(['javac', '--release', '17', '-cp', api, '-d', str(classes), *map(str, sources)],
                                    stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=30)
    (output / 'javac.log').write_bytes(compile_result.stdout)
    if compile_result.returncode:
        raise AssertionError('javac failed: ' + str(output / 'javac.log'))
    cp = os.pathsep.join((str(classes), str(root / 'src/junitTiming/resources'), api))
    state_result = subprocess.run(['java', '-cp', cp, 'bc.nonshipping.junittiming.JunitTimingRecordsControl', str(output / 'state-controls')],
                                  stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=15)
    (output / 'state-controls.log').write_bytes(state_result.stdout)
    assert state_result.returncode == 0, state_result.stdout
    summaries = []
    for mode in ('success', 'failure', 'aborted', 'skip', 'dynamic', 'unknown', 'timeout', 'hang-hook'):
        case = output / mode
        case.mkdir()
        context = dict(revision='control-revision', input_id='control-input', profile='inert-control',
                       environment_id='control-jdk17', cache_id='control-none', run_id='control-run',
                       build_root='/control', task_path=':control', task_nonce=mode)
        identity = {k: v for k, v in context.items() if k not in ('run_id', 'task_nonce')}
        identity.update(scope='junit_test', units='nanoseconds', clock='monotonic', class_name='control.Cases', name='case',
                        duration_boundary='junit_platform_executionStarted_to_executionFinished')
        # Explicit authored synthetic fixture, NOT observations adopted as real empirical evidence.
        catalog = dict(schema='bc.timing_budget_catalog.v1', status='VALID', sources=[], groups=[])
        if mode != 'unknown':
            catalog['groups'].append(dict(identity=identity, effective_timeout_ns=200_000_000,
                expected_runtime_ns=100_000_000, expected_runtime_rational=dict(numerator=100_000_000, denominator=1),
                successful_sample_count=1, samples=[]))
        bootstrap = case / 'bootstrap.json'
        sink = case / 'events.jsonl'
        health = case / 'health'
        bootstrap.write_text(json.dumps(dict(schema='bc.junit_timing_bootstrap.v1', context=context, catalog=catalog,
                                            sink=str(sink), health_dir=str(health))))
        started = time.monotonic()
        result = subprocess.run(['java', '-Dbc.junit.timing.bootstrap=' + str(bootstrap), '-cp', cp,
                                 'bc.nonshipping.junittiming.JunitTimingControl', mode], stdout=subprocess.PIPE,
                                stderr=subprocess.STDOUT, timeout=8)
        elapsed = time.monotonic() - started
        (case / 'worker.log').write_bytes(result.stdout)
        assert result.returncode == (124 if mode in ('timeout', 'hang-hook') else 0), (mode, result.returncode, result.stdout)
        rows = [json.loads(line) for line in sink.read_text().splitlines()]
        terminals = [row for row in rows if row['event'] == 'terminal']
        receipts = [json.loads(p.read_text()) for p in health.glob('*.json')]
        assert len(receipts) == 1, (mode, receipts)
        expected = 'FAILURE' if mode == 'failure' else 'SKIPPED' if mode in ('skip', 'aborted') else 'TIMED_OUT' if mode in ('timeout', 'hang-hook') else 'SUCCESS'
        assert terminals and terminals[0]['status'] == expected, (mode, terminals)
        assert receipts[0]['healthy'] == (mode not in ('timeout', 'hang-hook'))
        assert len(terminals) == (2 if mode == 'dynamic' else 1)
        if mode == 'unknown':
            assert terminals[0]['configured_timeout_ns'] is None
        if mode == 'failure':
            assert terminals[0]['engine_status'] == 'FAILED'
            assert b'ORIGINAL_RESULT_PRESERVED' in result.stdout
        if mode == 'hang-hook':
            assert b'HANGING_SHUTDOWN_HOOK_ENTERED' in result.stdout
            assert elapsed >= 2 and elapsed < 6
        if mode == 'timeout':
            assert elapsed < 2
        if mode in ('timeout', 'hang-hook'):
            assert terminals[0]['cleanup_grace_ns'] == 2_000_000_000
            assert terminals[0]['duration_ns'] >= terminals[0]['configured_timeout_ns']
        summaries.append(dict(mode=mode, exit_code=result.returncode, elapsed_seconds=elapsed, status=expected, healthy=receipts[0]['healthy']))
    # A broken service recorder must not throw over the original engine failure; it leaves no healthy receipt.
    broken = output / 'broken-bootstrap'
    broken.mkdir()
    malformed = broken / 'bootstrap.json'
    malformed.write_text('{malformed')
    result = subprocess.run(['java', '-Dbc.junit.timing.bootstrap=' + str(malformed), '-cp', cp,
                             'bc.nonshipping.junittiming.JunitTimingControl', 'failure'], stdout=subprocess.PIPE,
                            stderr=subprocess.STDOUT, timeout=8)
    (broken / 'worker.log').write_bytes(result.stdout)
    assert result.returncode == 0 and b'ORIGINAL_RESULT_PRESERVED failure' in result.stdout
    assert not list(broken.glob('*health*'))
    (output / 'summary.json').write_text(json.dumps(dict(scope='inert_JDK_SPI_controls_not_Gradle_acceptance', results=summaries), indent=2) + '\n')
    print('PASS 8 direct JDK SPI modes + broken-recorder original-failure control + deterministic/concurrent controls; retained:', output)


if __name__ == '__main__':
    main()

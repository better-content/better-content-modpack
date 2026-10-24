#!/usr/bin/env python3
"""Lead-only inert Gradle controls; retains all projects, logs and observations.

Requires --gradle absolute executable and a NEW --output directory under /home/dev.
No downloads/providers/native/Forge. Never invoke this helper from a source gate.
"""
import argparse
import json
import os
from pathlib import Path
import shutil
import signal
import subprocess
import time


def records(path):
    return [json.loads(line) for line in path.read_text().splitlines()]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--gradle', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    output = args.output.absolute()
    if not output.is_relative_to('/home/dev') or '..' in output.parts:
        parser.error('output must be a new retained /home/dev directory')
    if any(p.is_symlink() for p in (output, *output.parents)):
        parser.error('output symlinks are forbidden')
    if not args.gradle.is_absolute() or not args.gradle.is_file():
        parser.error('--gradle must name an existing absolute executable')
    output.mkdir(parents=True, exist_ok=False)
    source = Path(__file__).resolve().parent
    root = source.parent.parent
    project = output / 'project'
    project.mkdir()
    for name in ('settings.gradle', 'build.gradle'):
        shutil.copyfile(source / name, project / name)
    shutil.copytree(source / 'src', project / 'src')
    env = os.environ.copy()
    for key in tuple(env):
        if key.startswith('BC_TIMING_'):
            del env[key]
    env.update(BC_TIMING_REVISION='inert-control-v1', BC_TIMING_INPUT_ID='inert-fixture-v1',
               BC_TIMING_PROFILE='inert-not-provider-acceptance',
               BC_TIMING_ENVIRONMENT_ID='inert-jdk-gradle-control', BC_TIMING_CACHE_ID='inert-offline',
               BC_TIMING_NATIVE='false', BC_TIMING_OWNED_GRADLE='true', BC_TIMING_TIMEOUT_SECONDS='180',
               TMPDIR=str(output / 'tmp'), TMP=str(output / 'tmp'), TEMP=str(output / 'tmp'))
    (output / 'tmp').mkdir()
    home = output / 'home'
    home.mkdir()
    env.update(HOME=str(home), GRADLE_USER_HOME=str(home / 'gradle'))
    env['JAVA_TOOL_OPTIONS'] = env.get('JAVA_TOOL_OPTIONS', '') + ' -Duser.home=' + str(home) + ' -Djava.io.tmpdir=' + str(output / 'tmp')
    results = []

    def run(label, tasks, baseline=None, updates=None, success=True, censored=False):
        sink = output / (label + '.jsonl')
        local = env | {'BC_TIMING_FILE': str(sink)} | (updates or {})
        if baseline is not None:
            local['BC_TIMING_BASELINES'] = json.dumps([str(p) for p in baseline])
        command = [str(args.gradle), '--offline', '--no-daemon', '--console=plain',
                   '--max-workers=1', '--no-build-cache', '--rerun-tasks',
                   '--project-dir', str(project), '--init-script', str(root / 'gradle/test-timing.init.gradle'),
                   *tasks]
        started = time.monotonic()
        with (output / (label + '.log')).open('wb') as log:
            process = subprocess.Popen(command, env=local, stdout=log, stderr=subprocess.STDOUT,
                                       start_new_session=True)
            try:
                code = process.wait(timeout=180)
            except subprocess.TimeoutExpired:
                os.killpg(process.pid, signal.SIGTERM)
                try:
                    process.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    os.killpg(process.pid, signal.SIGKILL)
                    process.wait(timeout=5)
                raise AssertionError('control supervisor timeout: ' + label)
        elapsed = time.monotonic() - started
        results.append(dict(label=label, exit_code=code, elapsed_seconds=elapsed,
                            command=command, sink=str(sink)))
        (output / 'invocations.json').write_text(json.dumps(results, indent=2) + '\n')
        assert (code == 0) == success, (label, code, str(output / (label + '.log')))
        rows = records(sink)
        if censored:
            # Abrupt own-daemon termination must NOT fabricate the task's original terminal.
            import importlib.util
            spec = importlib.util.spec_from_file_location('controlled_reducer', root / 'scripts/timing_report.py')
            reducer = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(reducer)
            assert reducer.write_report([sink], output / (label + '.manual-report.json'))['status'] == 'INCOMPLETE'
        else:
            assert json.loads(Path(str(sink) + '.report.json').read_text())['status'] == 'COMPLETE', label
        starts = {r['operation_id']: r for r in rows if r['event'] == 'start'}
        terminals = {r['operation_id']: r for r in rows if r['event'] == 'terminal'}
        assert (bool(starts.keys() - terminals.keys()) if censored else starts.keys() == terminals.keys()), label
        return sink, rows

    def terminal(rows, task):
        matches = [r for r in rows if r['scope'] == 'gradle_task' and
                   r['task_path'] == ':' + task and r['event'] == 'terminal']
        assert len(matches) == 1, matches
        return matches[0]

    first, rows = run('baseline-a', ['probe', '-PsleepMs=200'])
    row = terminal(rows, 'probe')
    assert row['configured_timeout_ns'] == 20_000_000_000
    assert row['timeout_policy'] == 'UNKNOWN_UNTIL_COMPARABLE_SUCCESS_MEASURED'
    second, rows = run('baseline-b', ['probe', '-PsleepMs=300'])
    durations = [terminal(records(first), 'probe')['duration_ns'], terminal(rows, 'probe')['duration_ns']]
    # Exactly twice median of two integers = their sum. No guessed/fixed case budget.
    expected_cap = sum(durations)
    assert 0 < expected_cap < 5_000_000_000, durations
    marker = project / 'probe-completed.txt'
    before = marker.stat().st_mtime_ns
    _, rows = run('actual-timeout', ['probe', '-PsleepMs=8000'], [first, second], success=False)
    row = terminal(rows, 'probe')
    assert row['timeout_policy'] == 'EXACT_2X_EMPIRICAL_MEDIAN'
    assert row['configured_timeout_ns'] == expected_cap == row['selected_timeout_ns']
    assert row['original_configured_timeout_ns'] == 20_000_000_000
    assert row['enclosing_process_timeout_ns'] == 180_000_000_000
    assert row['successful_sample_count'] == 2 and len(row['baseline_samples']) == 2
    assert row['duration_ns'] < 8_000_000_000, row
    assert row['status'] == 'FAILED'  # preserve actual Gradle task outcome
    assert marker.stat().st_mtime_ns == before, 'task action incorrectly completed'
    assert (project / 'probe-interrupted.txt').read_text() == 'java.lang.InterruptedException'
    assert 'timeout' in (output / 'actual-timeout.log').read_text().lower()
    hard_sink, hard_rows = run('ignored-interrupt', ['probe', '-PsleepMs=8000', '-PignoreInterrupt=true'],
                               [first, second], success=False, censored=True)
    health = records(Path(str(hard_sink) + '.owned-deadlines.health.jsonl'))
    request, = [r for r in health if r['event'] == 'owned_jvm_exit_requested']
    assert request['configured_timeout_ns'] == expected_cap
    assert request['deadline_cleanup_grace_ns'] == 2_000_000_000
    assert request['elapsed_ns'] >= expected_cap + 2_000_000_000
    assert (project / 'probe-ignored-interrupt.txt').read_text() == 'ignored original interrupt'
    assert not (project / 'probe-ignored-completed.txt').exists()
    assert not [r for r in hard_rows if r['scope'] == 'gradle_task' and r['event'] == 'terminal']
    _, rows = run('incomparable', ['probe', '-PsleepMs=10'], [first, second],
                  {'BC_TIMING_INPUT_ID': 'different-inert-fixture'})
    row = terminal(rows, 'probe')
    assert row['configured_timeout_ns'] == 20_000_000_000 and row['expected_runtime_ns'] is None
    assert row['timeout_policy'] == 'UNKNOWN_UNTIL_COMPARABLE_SUCCESS_MEASURED'
    _, rows = run('original-failure', ['fails'], success=False)
    assert terminal(rows, 'fails')['status'] == 'FAILED'
    assert 'original-control-failure' in (output / 'original-failure.log').read_text()
    _, rows = run('nonexecution', ['skips'])
    assert terminal(rows, 'skips')['status'] == 'SKIPPED'
    for label, fail in [('junit-success', False), ('junit-failure', True)]:
        callback_path = project / 'original-callbacks.jsonl'
        old_count = len(records(callback_path)) if callback_path.exists() else 0
        _, rows = run(label, ['test', '-PcaseFailure=' + str(fail).lower()], success=not fail)
        callbacks = records(callback_path)[old_count:]
        cases = [r for r in rows if r['scope'] == 'junit_test' and r['event'] == 'terminal']
        assert len(cases) == len(callbacks) == 3, (cases, callbacks)
        assert {r['name']: (r['result'], r['exception_types']) for r in callbacks} == {
            r['name']: (r['status'], r['exception_types']) for r in cases}
        assert sum(r['status'] == 'FAILURE' for r in cases) == int(fail)
        assert sum(r['status'] == 'SKIPPED' for r in cases) == 1
        for case in cases:
            assert case['configured_timeout_ns'] is None
            assert case['expected_runtime_ns'] is None
            assert case['timeout_enforcement'] == 'UNSUPPORTED_BY_GRADLE_TEST_LISTENER'
            assert case['enclosing_process_timeout_ns'] == 180_000_000_000
        suites = [r for r in rows if r['scope'] == 'junit_suite' and r['event'] == 'terminal']
        assert suites and all(r['configured_timeout_ns'] is None for r in suites)
        if fail:
            assert 'original-junit-control-failure' in (output / (label + '.log')).read_text() or any(
                'original-junit-control-failure' in p.read_text()
                for p in (project / 'build/test-results/test').glob('TEST-*.xml'))
        assert list((project / 'build/test-results/test').glob('TEST-*.xml'))
    (output / 'control-result.json').write_text(json.dumps(dict(
        status='PASSED', acceptance='INERT_CONTROLS_ONLY_NOT_PROVIDER_OR_NATIVE_ACCEPTANCE',
        measured_durations_ns=durations, actual_enforced_task_timeout_ns=expected_cap,
        invocations=results), indent=2) + '\n')
    print(output / 'control-result.json')


if __name__ == '__main__':
    main()

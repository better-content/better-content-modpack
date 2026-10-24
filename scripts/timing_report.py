#!/usr/bin/env python3
"""Reduce existing bc.timing.v1 observations, never verification receipts or timeout inputs.

Expected = empirical median of ALL paired comparable successes in the explicit inputs
(no minimum N, padding, history discovery, outlier removal or failed/censored samples).
Recommended cap = exactly twice that median; neither authored nor live caps change.
UNKNOWN identity/boundary/measurement means UNKNOWN baseline, not half a legacy cap.
Native configured ticks and monotonic nanoseconds are separate facts, NEVER 20-TPS
conversions. Synthetic fixtures must stay in isolated inputs/output paths; this reducer
cannot authenticate a producer's identity claims or turn fixtures into real measurements.

Public ABI: write_report(inputs: list[Path], output: Path) -> dict.
CLI: python3 -B scripts/timing_report.py --output REPORT INPUT.jsonl [...]
Exit 0 = complete diagnostic snapshot (NOT test acceptance), 2 = invalid/incomplete.
--allow-incomplete returns 0 for INCOMPLETE live snapshots, never INVALID; the report
still says INCOMPLETE/NOT_EVALUATED. Active enclosing processes can leave such snapshots.
Input failures are retained in an INVALID report; unsafe output/write failures raise.
Snapshots are bounded and POSIX-record-locked to interoperate with existing writers.
Same-output calls serialize the entire reduction/publication; unique temporary files,
fsync, atomic replacement and directory fsync avoid partial reports. Locks have a
finite wait. Different output paths do not share mutable reducer state.
"""
import argparse
from collections import Counter
from contextlib import contextmanager
from datetime import datetime, timezone
import fcntl
import hashlib
import json
import os
from pathlib import Path
import stat
import threading
import time
import uuid

MAX_FILES = 64
MAX_BYTES = 32 * 1024 * 1024
MAX_LINE = 65536
MAX_RECORDS = 100000
MAX_INTEGER = 2**63 - 1
CONTEXT = ('revision', 'input_id', 'profile', 'environment_id', 'cache_id')
IDENTITY = {
    'process': ('cwd', 'command_sha256'),
    'python_test': ('python_module', 'python_class', 'test_name'),
    'python_suite': ('suite_name', 'suite_members_sha256'),
    'gradle_task': ('build_root', 'task_path'),
    'junit_suite': ('build_root', 'task_path', 'name'),
    'junit_test': ('build_root', 'task_path', 'class_name', 'name'),
    'native_test': ('name', 'structure_name', 'result_semantics', 'required', 'flaky',
                    'max_attempts', 'required_successes'),
}
SUCCESS = {'SUCCEEDED', 'SUCCESS'}
FAILURE = {'FAILED', 'FAILURE'}
CANCEL = {'CANCELLED', 'INTERRUPTED'}
CENSORED = {'TIMED_OUT', 'TIMEOUT', 'CANCELLED', 'INTERRUPTED'}
NONEXECUTION = {'SKIPPED', 'UP_TO_DATE', 'NO_SOURCE'}
TERMINAL = SUCCESS | FAILURE | CENSORED | NONEXECUTION
# POSIX record locks serialize processes, not threads. This local mutex complements them.
_LOCAL_LOCK = threading.Lock()


def _path(value):
    path = Path(value).absolute()
    if '..' in path.parts or any(p.is_symlink() for p in (path, *path.parents)):
        raise ValueError('symlink or traversal path: ' + str(path))
    return path


@contextmanager
def _lock(fd, exclusive):
    deadline = time.monotonic() + 10
    while True:
        try:
            fcntl.lockf(fd, (fcntl.LOCK_EX if exclusive else fcntl.LOCK_SH) | fcntl.LOCK_NB)
            break
        except BlockingIOError:
            if time.monotonic() >= deadline:
                raise ValueError('timing lock wait exceeded 10 seconds')
            time.sleep(.01)
    try:
        yield
    finally:
        fcntl.lockf(fd, fcntl.LOCK_UN)


def _object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError('duplicate JSON key: ' + key)
        result[key] = value
    return result


def _number(value):
    return type(value) is int and 0 <= value <= MAX_INTEGER


def _known(value):
    return value is not None and (not isinstance(value, str) or
                                 value.strip().upper() not in ('', 'UNKNOWN', 'NULL'))


def _validate(row):
    if not isinstance(row, dict) or row.get('schema') != 'bc.timing.v1':
        raise ValueError('expected bc.timing.v1 object')
    for key in ('operation_id', 'scope', 'units', 'clock', 'event', 'status', 'observed_at'):
        if not isinstance(row.get(key), str) or not row[key].strip():
            raise ValueError('missing/invalid ' + key)
    if 'deadline_exceeded' in row and type(row['deadline_exceeded']) is not bool:
        raise ValueError('invalid deadline_exceeded')
    uuid.UUID(row['operation_id'])
    if row['scope'] not in IDENTITY or row['units'] != 'nanoseconds' or row['clock'] != 'monotonic':
        raise ValueError('unsupported scope/units/clock (no implicit conversion)')
    datetime.fromisoformat(row['observed_at'].replace('Z', '+00:00'))
    if row['event'] not in ('start', 'terminal'):
        raise ValueError('invalid event')
    if row['event'] == 'start':
        if row['status'] != 'RUNNING' or row.get('duration_ns') is not None:
            raise ValueError('invalid start state')
    elif row['status'] not in TERMINAL and not (
            row['scope'] == 'python_test' and row['status'] in
            {'ERROR', 'EXPECTED_FAILURE', 'UNEXPECTED_SUCCESS', 'UNKNOWN'}):
        raise ValueError('unknown terminal status')
    elif row['status'] in SUCCESS and row['status'] != ('SUCCESS' if row['scope'].startswith('junit_') else 'SUCCEEDED'):
        raise ValueError('success enum does not match producer scope')
    elif row.get('duration_ns') is not None and not _number(row['duration_ns']):
        raise ValueError('invalid duration_ns')
    for key in ('configured_timeout_ns', 'enclosing_process_timeout_ns', 'expected_runtime_ns',
                'configured_timeout_ticks', 'native_duration_ms'):
        if row.get(key) is not None and not _number(row[key]):
            raise ValueError('invalid ' + key)
    for key in CONTEXT + ('run_id', 'invocation_id', 'duration_boundary', 'measurement_boundary', 'process_name'):
        if row.get(key) is not None and not isinstance(row[key], str):
            raise ValueError('invalid ' + key)
    for key in IDENTITY[row['scope']]:
        if row.get(key) is not None and not isinstance(row[key], (str, int, bool)):
            raise ValueError('invalid identity ' + key)


def _snapshot(path, remaining):
    path = _path(path)
    fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
    try:
        if not stat.S_ISREG(os.fstat(fd).st_mode):
            raise ValueError('input is not regular file')
        with _lock(fd, False):
            size = os.fstat(fd).st_size
            if size > remaining:
                raise ValueError('input byte bound exceeded')
            chunks = []
            left = size
            while left:
                part = os.read(fd, min(left, 65536))
                if not part:
                    raise ValueError('input shortened during snapshot')
                chunks.append(part)
                left -= len(part)
            return b''.join(chunks)
    finally:
        os.close(fd)


def _group_identity(row):
    identity_fields = IDENTITY[row['scope']]
    # Explicit reviewed logical commands may cross private worker paths. Their exact
    # recipe/source/dependency input binding must be a real SHA-256, never UNKNOWN.
    input_id = row.get('input_id')
    if (row['scope'] == 'process' and _known(row.get('process_name')) and
            isinstance(input_id, str) and len(input_id) == 64 and
            all(char in '0123456789abcdef' for char in input_id)):
        identity_fields = ('process_name',)
    fields = ('scope', 'units', 'clock') + CONTEXT + identity_fields
    identity = {key: row.get(key) for key in fields}
    # Native writer calls this measurement_boundary; normalize that exact alias only.
    identity['duration_boundary'] = row.get('duration_boundary') or row.get('measurement_boundary')
    if row['scope'].startswith('junit_'):
        # These can legitimately be null for aggregate Gradle descriptors.
        identity.update(class_name=row.get('class_name'))
    missing = [key for key in fields if not _known(row.get(key))]
    if not _known(identity['duration_boundary']):
        missing.append('duration_boundary')
    return identity, missing


def reduce_records(inputs):
    """Read only explicit sources. Return diagnostic report; never discover old baselines."""
    report = dict(schema='bc.timing_report.v1', status='COMPLETE', acceptance='NOT_EVALUATED',
                  generated_at=datetime.now(timezone.utc).isoformat(), sources=[], issues=[], groups=[],
                  expected_statistic='empirical_median_all_comparable_successes', timeout_multiplier=2,
                  policy='RECOMMENDATION_ONLY_NO_TIMEOUT_MUTATION', counts={})
    operations = {}
    total = records = 0
    paths = set()
    def issue(kind, **detail):
        report['issues'].append(dict(kind=kind, **detail))
    if not 0 < len(inputs) <= MAX_FILES:
        issue('invalid', reason='expected 1..64 explicit input files')
        inputs = []
    for source in inputs:
        source = str(Path(source).absolute())
        try:
            if source in paths:
                raise ValueError('duplicate input path')
            paths.add(source)
            data = _snapshot(source, MAX_BYTES - total)
            total += len(data)
            report['sources'].append(dict(path=source, bytes=len(data), sha256=hashlib.sha256(data).hexdigest()))
            if not data:
                issue('incomplete', source=source, reason='empty input')
            for line_no, line in enumerate(data.splitlines(keepends=True), 1):
                records += 1
                if records > MAX_RECORDS:
                    raise ValueError('record count bound exceeded')
                if len(line) > MAX_LINE:
                    raise ValueError('record line bound exceeded')
                if not line.endswith(b'\n'):
                    issue('invalid', source=source, line=line_no, reason='truncated/uncommitted final record')
                    continue
                try:
                    row = json.loads(line, object_pairs_hook=_object,
                                     parse_constant=lambda value: (_ for _ in ()).throw(ValueError(value)))
                    _validate(row)
                    entry = operations.setdefault(row['operation_id'], {})
                    if row['event'] in entry:
                        entry['invalid'] = True
                        raise ValueError('duplicate operation event')
                    entry[row['event']] = dict(record=row, source=source, line=line_no)
                except (ValueError, TypeError, UnicodeError, RecursionError) as error:
                    issue('invalid', source=source, line=line_no, reason=str(error))
        except (OSError, ValueError) as error:
            issue('invalid', source=source, reason=str(error))
    groups = {}
    counts = Counter()
    for operation_id, entry in operations.items():
        if entry.get('invalid'):
            counts['invalid_operations'] += 1
            continue
        start, terminal = entry.get('start'), entry.get('terminal')
        row = (terminal or start)['record']
        identity, missing = _group_identity(row)
        key = json.dumps(identity, sort_keys=True, separators=(',', ':'))
        group = groups.setdefault(key, dict(identity=identity, missing_identity=missing, counts=Counter(),
                                            successful_sample_count=0, samples=[], observations=[]))
        observation = dict(operation_id=operation_id, deadline_exceeded=row.get('deadline_exceeded', False), run_id=row.get('run_id'),
                           invocation_id=row.get('invocation_id'), status=row['status'],
                           configured_timeout_ns=row.get('configured_timeout_ns'), timeout_scope=row.get('timeout_scope'),
                           enclosing_process_timeout_ns=row.get('enclosing_process_timeout_ns'),
                           configured_timeout_ticks=row.get('configured_timeout_ticks'),
                           native_duration_ms=row.get('native_duration_ms'), duration_ns=row.get('duration_ns'),
                           provenance=[dict(source=e['source'], line=e['line']) for e in (start, terminal) if e])
        group['observations'].append(observation)
        category = ('started_only' if not terminal else 'missing_start' if not start else
                    'success' if row['status'] in SUCCESS else
                    'failure' if row['status'] in FAILURE | {'ERROR', 'UNEXPECTED_SUCCESS'} else
                    'expected_failure' if row['status'] == 'EXPECTED_FAILURE' else
                    'unknown' if row['status'] == 'UNKNOWN' else
                    'cancelled' if row['status'] in CANCEL else 'censored' if row['status'] in CENSORED else 'nonexecution')
        counts[category] += 1
        group['counts'][category] += 1
        if terminal and row['status'] in CENSORED:
            counts['censored_total'] += 1
            group['counts']['censored_total'] += 1
        if not start or not terminal:
            issue('incomplete', operation_id=operation_id, reason=category)
            continue
        before = start['record']
        # Only terminal/result fields may differ. Identity, configured caps and provenance context must agree.
        stable = set(identity) | set(CONTEXT) | set(IDENTITY[row['scope']]) | {
            'scope', 'units', 'clock', 'run_id', 'invocation_id', 'parent_name', 'configured_timeout_ns',
            'timeout_scope', 'enclosing_process_timeout_ns', 'configured_timeout_ticks',
            'duration_boundary', 'measurement_boundary', 'process_name'}
        if any(before.get(field) != row.get(field) for field in stable):
            issue('invalid', operation_id=operation_id, reason='start/terminal identity or cap mismatch')
            continue
        if row['status'] == 'UNKNOWN':
            issue('incomplete', operation_id=operation_id, reason='unknown terminal outcome')
        if row['status'] in SUCCESS:
            if row.get('duration_ns') is None:
                issue('incomplete', operation_id=operation_id, reason='success missing measurement')
            elif not missing and row.get('deadline_exceeded') is not True:
                group['samples'].append(dict(operation_id=operation_id, run_id=row.get('run_id'),
                                             duration_ns=row['duration_ns'], provenance=observation['provenance']))
    invalid = any(i['kind'] == 'invalid' for i in report['issues'])
    report['status'] = 'INVALID' if invalid else 'INCOMPLETE' if report['issues'] else 'COMPLETE'
    for key in sorted(groups):
        group = groups[key]
        samples = group['samples']
        durations = sorted(s['duration_ns'] for s in samples)
        group['successful_sample_count'] = len(samples)
        expected = None
        if durations and not invalid:
            middle = len(durations) // 2
            # Preserve exact half-nanosecond medians: recommendation is integer sum.
            twice = durations[middle] * 2 if len(durations) % 2 else durations[middle - 1] + durations[middle]
            expected = twice // 2 if twice % 2 == 0 else twice / 2
        group.update(baseline_status='MEASURED' if expected is not None else 'UNKNOWN',
                     expected_runtime_ns=expected, recommended_timeout_ns=twice if expected is not None else None,
                     statistic=report['expected_statistic'])
        for observation in group['observations']:
            cap = observation['configured_timeout_ns']
            observation['configured_cap_vs_recommendation'] = (
                'UNKNOWN' if expected is None or cap is None or observation['timeout_scope'] != group['identity']['scope']
                else 'MATCHES_2X_EMPIRICAL_MEDIAN' if cap == twice else 'DOES_NOT_MATCH_2X_EMPIRICAL_MEDIAN')
        report['groups'].append(group)
    report['counts'] = dict(counts)
    return report


def write_report(inputs: list[Path], output: Path) -> dict:
    """Serialize reduction + atomic durable publication; input errors appear in report."""
    output = _path(output)
    if output in [Path(p).absolute() for p in inputs]:
        raise ValueError('report cannot overwrite its input')
    output.parent.mkdir(parents=True, exist_ok=True)
    lockpath = _path(str(output) + '.lock')
    with _LOCAL_LOCK:
        fd = os.open(lockpath, os.O_CREAT | os.O_RDWR | os.O_NOFOLLOW | os.O_NONBLOCK, 0o600)
        try:
            if not stat.S_ISREG(os.fstat(fd).st_mode):
                raise ValueError('report lock is not a regular file')
            with _lock(fd, True):
                report = reduce_records(inputs)
                payload = (json.dumps(report, sort_keys=True, indent=2, allow_nan=False) + '\n').encode()
                temporary = output.with_name('.' + output.name + '.' + str(uuid.uuid4()) + '.tmp')
                # Retain even an interrupted temporary report for diagnosis; never purge inputs/evidence.
                with open(temporary, 'xb') as stream:
                    stream.write(payload)
                    stream.flush()
                    os.fsync(stream.fileno())
                _path(output)
                os.replace(temporary, output)
                directory = os.open(output.parent, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)
                try:
                    os.fsync(directory)
                finally:
                    os.close(directory)
                return report
        finally:
            os.close(fd)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--allow-incomplete', action='store_true',
                        help='allow a diagnostic live snapshot; never accepts INVALID records')
    parser.add_argument('inputs', type=Path, nargs='+')
    args = parser.parse_args()
    report = write_report(args.inputs, args.output)
    print(json.dumps(dict(output=str(args.output), status=report['status'], acceptance='NOT_EVALUATED')))
    return 0 if (report['status'] == 'COMPLETE' or
                 (args.allow_incomplete and report['status'] == 'INCOMPLETE')) else 2


if __name__ == '__main__':
    raise SystemExit(main())

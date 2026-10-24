#!/usr/bin/env python3
"""Linux subprocess supervisor: finite wall clock, owned subreaper tree, no agent creation.
Subreaping closes the short-lived/detached-parent polling race. SIGKILL of this supervisor
itself cannot prove cleanup; callers must reconcile that interruption explicitly.
"""
import argparse
import ctypes
import fcntl
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import signal
import subprocess
import sys
import time
import uuid
from decimal import Decimal
from timing_budget import resolve
from datetime import datetime, timezone


class TimingObservation:
    """Durable observations, not acceptance or an inferred expected-runtime baseline."""
    def __init__(self, args, command):
        self.result = dict(cleanup_complete=None, process_exit_code=None)
        self.path = output_path(os.environ.get('BC_TIMING_FILE') or
                                (args.log + '.timing.jsonl' if args.log else
                                 str(Path(__file__).absolute().parent.parent / 'generated' / 'timing' /
                                     (str(uuid.uuid4()) + '.jsonl'))))
        self.fields = dict(schema='bc.timing.v1', operation_id=str(uuid.uuid4()), scope='process',
                           units='nanoseconds', clock='monotonic', duration_boundary='supervision_including_cleanup',
                           configured_timeout_ns=int(Decimal(str(args.budget_seconds)) * 1_000_000_000), timeout_scope='process',
                           enclosing_process_timeout_ns=None, expected_runtime_ns=None, timeout_policy='UNKNOWN_UNTIL_COMPARABLE_SUCCESS_MEASURED',
                           cwd=str(Path(args.cwd).absolute()), log=args.log, command=list(command),
                           command_sha256=hashlib.sha256('\0'.join(command).encode()).hexdigest())
        for key in ('RUN_ID', 'REVISION', 'INPUT_ID', 'PROFILE', 'ENVIRONMENT_ID', 'CACHE_ID'):
            self.fields[key.lower()] = os.environ.get('BC_TIMING_' + key) or (
                os.environ.get('BC_TEST_RUN_ID') if key == 'RUN_ID' else None) or 'UNKNOWN'
        if os.environ.get('BC_TIMING_PROCESS_NAME', '').strip():
            self.fields['process_name'] = os.environ['BC_TIMING_PROCESS_NAME']
        decision = resolve(self.fields, self.fields['configured_timeout_ns'])
        self.fields.update(decision)
        self.budget_ns = decision['effective_timeout_ns']
        self.fields['configured_timeout_ns'] = self.budget_ns
        self.started = time.monotonic_ns()
        self.append(event='start', status='RUNNING', duration_ns=None)

    def append(self, **fields):
        payload = (json.dumps(dict(self.fields, observed_at=datetime.now(timezone.utc).isoformat(),
                                   **fields), separators=(',', ':')) + '\n').encode()
        fd = os.open(self.path, os.O_CREAT | os.O_WRONLY | os.O_NOFOLLOW, 0o600)
        try:
            # POSIX record locks interoperate with JVM FileChannel.lock (flock does not).
            fcntl.lockf(fd, fcntl.LOCK_EX)
            os.lseek(fd, 0, os.SEEK_END)
            view = memoryview(payload)
            while view:
                written = os.write(fd, view)
                if not written:
                    raise OSError('timing append made no progress')
                view = view[written:]
            os.fsync(fd)
        finally:
            os.close(fd)

    def finish(self, status, failure):
        reason = self.result.get('reason')
        outcome = ('CANCELLED' if isinstance(failure, (KeyboardInterrupt, SystemExit)) or
                   reason in ('cancelled', 'parent exited') else
                   'TIMED_OUT' if reason == 'deadline exhausted' else
                   'SUCCEEDED' if failure is None and status == 0 else 'FAILED')
        self.append(event='terminal', status=outcome,
                    duration_ns=max(0, time.monotonic_ns() - self.started), exit_code=status,
                    exception_type=type(failure).__name__ if failure else None, **self.result)
        # Explicit sibling dependency: copied fixtures must include the reporter too. Never
        # silently degrade a production invocation to manual-only/missing reports.
        module_path = Path(__file__).with_name('timing_report.py')
        spec = importlib.util.spec_from_file_location('bc_timing_report', module_path)
        reporter = importlib.util.module_from_spec(spec)
        sys.modules[spec.name] = reporter
        spec.loader.exec_module(reporter)
        report = reporter.write_report([self.path], self.path.with_name(self.path.name + '.report.json'))
        if report.get('status') not in ('COMPLETE', 'INCOMPLETE'):
            raise ValueError('automatic timing report is invalid: ' + str(self.path))


def snapshot():
    result = {}
    for item in Path('/proc').iterdir():
        if not item.name.isdecimal():
            continue
        try:
            # comm may contain spaces or ')'; only the numeric stat fields are used.
            fields = (item / 'stat').read_text().rsplit(')', 1)[1].split()
            result[int(item.name)] = (int(fields[1]), int(fields[19]), fields[0])
        except (OSError, ValueError, IndexError):
            continue
    return result


def owned_now(known, include_zombies=False):
    table = snapshot()
    owner = os.getpid()
    live = {pid: identity for pid, identity in known.items() if pid in table and table[pid][1] == identity}
    while True:
        added = {pid: row[1] for pid, row in table.items() if pid != owner and
                 (row[0] == owner or row[0] in live) and pid not in live}
        if not added:
            break
        live.update(added)
    known.update(live)
    return {pid: identity for pid, identity in live.items() if include_zombies or table[pid][2] != 'Z'}


def send(known, signum):
    for pid in sorted(owned_now(known), reverse=True):
        try:
            os.kill(pid, signum)
        except ProcessLookupError:
            pass


def reap(exclude):
    # Reap adopted descendants DURING execution, without stealing Popen's root
    # wait status. JVM ProcessHandle liveness includes zombies until this happens.
    for pid, row in snapshot().items():
        if row[0] != os.getpid() or pid == exclude:
            continue
        try:
            os.waitpid(pid, os.WNOHANG)
        except (ChildProcessError, ProcessLookupError):
            pass


def cleanup(process, known, grace=2.0, force=5.0):
    # Popen reaps its own root; adopted orphans are reaped after the root terminates.
    for signum, seconds in ((signal.SIGTERM, grace), (signal.SIGKILL, force)):
        end = time.monotonic() + seconds
        while True:
            send(known, signum)
            process.poll()
            reap(process.pid)
            # A zombie is stopped but still owned until waitpid reaps it. Do not
            # return in the race between child exit and the preceding reap call.
            if not owned_now(known, include_zombies=True):
                return []
            if time.monotonic() >= end:
                break
            time.sleep(0.025)
    return sorted(owned_now(known, include_zombies=True))


def output_path(path):
    path = Path(path).absolute()
    home = Path.home()
    pack = Path(__file__).absolute().parent.parent
    approved = [home / '.tmp', pack.parent / 'workspace_artifacts',
                pack / 'build', pack / 'generated']
    if '..' in path.parts or not any(path.is_relative_to(root) for root in approved):
        raise ValueError('supervisor log must use an approved output root')
    if any(parent.is_symlink() for parent in [path, *path.parents]):
        raise ValueError('supervisor log ancestry is a symlink')
    path.parent.mkdir(parents=True, exist_ok=True)
    return path


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--budget-seconds', type=float, required=True)
    parser.add_argument('--cwd', required=True)
    parser.add_argument('--log')
    parser.add_argument('--parent-pid', type=int)
    parser.add_argument('--capture-limit', type=int, default=0)
    parser.add_argument('--quiet-capture', action='store_true')
    parser.add_argument('command', nargs=argparse.REMAINDER)
    args = parser.parse_args()
    command = args.command[1:] if args.command[:1] == ['--'] else args.command
    if not command or not 0 < args.budget_seconds <= 28800 or not 0 <= args.capture_limit <= 4194304:
        raise ValueError('invalid bounded command contract')
    timing = TimingObservation(args, command)
    status = None
    failure = None
    try:
        status = supervise(args, command, timing)
        return status
    except BaseException as error:
        failure = error
        raise
    finally:
        try:
            timing.finish(status, failure)
        except Exception as timing_error:
            if failure is not None or (status is not None and status != 0):
                # Do not replace the process failure/status with a telemetry error.
                print('timing recording failed: ' + str(timing_error), file=sys.stderr)
            else:
                raise


def supervise(args, command, timing):
    # PR_SET_CHILD_SUBREAPER is unprivileged and applies to this invocation only.
    if ctypes.CDLL(None, use_errno=True).prctl(36, 1, 0, 0, 0) != 0:
        raise OSError(ctypes.get_errno(), 'owned subprocess subreaper unavailable')
    parent = args.parent_pid if args.parent_pid is not None else os.getppid()
    if parent <= 1 or os.getppid() != parent:
        raise ValueError('command owner exited before supervisor initialization')
    cancellation = []
    for signum in (signal.SIGTERM, signal.SIGINT, signal.SIGHUP):
        signal.signal(signum, lambda number, frame: cancellation.append(number))
    log = output_path(args.log) if args.log else None
    if args.capture_limit and log is None:
        raise ValueError('bounded capture requires file-backed output')
    stream = log.open('wb') if log else None
    try:
        environment = dict(os.environ)
        environment.pop('BC_TIMING_OWNED_GRADLE', None)
        if '--no-daemon' in command and Path(command[0]).name in {'gradle', 'gradlew'}:
            environment['BC_TIMING_OWNED_GRADLE'] = 'true'
        # The root Gradle/JUnit writer shares this invocation sink unless a narrower source
        # profile deliberately binds another sink. No environment dump is recorded.
        environment.setdefault('BC_TIMING_FILE', str(timing.path))
        # A logical selector names this supervised process, not nested invocations.
        environment.pop('BC_TIMING_PROCESS_NAME', None)
        # Decimal serialization preserves the ACTUAL chosen cap, even below a millisecond.
        environment['BC_TIMING_TIMEOUT_SECONDS'] = str(Decimal(timing.budget_ns) / Decimal(1_000_000_000))
        environment['BC_TIMING_TIMEOUT_NS'] = str(timing.budget_ns)
        launch_started = time.monotonic_ns()
        process = subprocess.Popen(command, cwd=args.cwd, stdout=stream, stderr=subprocess.STDOUT if stream else None,
                                   start_new_session=True, env=environment)
    except BaseException:
        if stream:
            stream.close()
        raise
    table = snapshot()
    known = {process.pid: table[process.pid][1]} if process.pid in table else {}
    end = launch_started + timing.budget_ns
    status = None
    reason = None
    survivors = []
    try:
        while True:
            owned_now(known)
            reap(process.pid)
            status = process.poll()
            if status is not None:
                break
            if cancellation or os.getppid() != parent:
                reason = 'cancelled' if cancellation else 'parent exited'
                status = 128 + (cancellation[0] if cancellation else signal.SIGTERM)
                break
            if args.capture_limit and log.stat().st_size > args.capture_limit:
                reason = 'capture output limit exhausted'
                status = 125
                break
            if time.monotonic_ns() >= end:
                reason = 'deadline exhausted'
                status = 124
                break
            time.sleep(min(0.025, max(0, end - time.monotonic_ns()) / 1_000_000_000))
    finally:
        survivors = cleanup(process, known)
        if stream:
            stream.close()
    if survivors:
        reason = (reason or 'command completed') + '; owned process cleanup incomplete'
        status = 125
    timing.result.update(reason=reason, surviving_pids=survivors, cleanup_complete=not survivors,
                         process_exit_code=process.returncode)
    if reason or status:
        diagnostic = dict(schema='bc.bounded_facade.v1', pid=process.pid, status=status, reason=reason,
                          surviving_pids=survivors, budget_seconds=str(Decimal(timing.budget_ns) / Decimal(1_000_000_000)),
                          effective_timeout_ns=timing.budget_ns, cwd=args.cwd, command=command)
        print(json.dumps(diagnostic), file=sys.stderr)
        if log:
            log.with_name(log.name + '.diagnostic.json').write_text(json.dumps(diagnostic) + '\n')
    if args.capture_limit:
        if log.stat().st_size > args.capture_limit:
            raise ValueError('bounded facade capture exceeds declared output limit')
        if not args.quiet_capture:
            sys.stdout.buffer.write(log.read_bytes())
    return status if status >= 0 else 128 - status


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except (OSError, ValueError) as error:
        print(json.dumps(dict(schema='bc.bounded_facade.v1', state='invalid', error=str(error))), file=sys.stderr)
        raise SystemExit(2)

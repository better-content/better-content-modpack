#!/usr/bin/env python3
"""Linux subprocess supervisor: finite wall clock, owned subreaper tree, no agent creation.
Subreaping closes the short-lived/detached-parent polling race. SIGKILL of this supervisor
itself cannot prove cleanup; callers must reconcile that interruption explicitly.
"""
import argparse
import ctypes
import json
import os
from pathlib import Path
import signal
import subprocess
import sys
import time


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
    process = subprocess.Popen(command, cwd=args.cwd, stdout=stream, stderr=subprocess.STDOUT if stream else None,
                               start_new_session=True)
    table = snapshot()
    known = {process.pid: table[process.pid][1]} if process.pid in table else {}
    end = time.monotonic() + args.budget_seconds
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
            if time.monotonic() >= end:
                reason = 'deadline exhausted'
                status = 124
                break
            time.sleep(0.025)
    finally:
        survivors = cleanup(process, known)
        if stream:
            stream.close()
    if survivors:
        reason = (reason or 'command completed') + '; owned process cleanup incomplete'
        status = 125
    if reason or status:
        diagnostic = dict(schema='bc.bounded_facade.v1', pid=process.pid, status=status, reason=reason,
                          surviving_pids=survivors, budget_seconds=args.budget_seconds, cwd=args.cwd, command=command)
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

#!/usr/bin/env python3
"""Bounded live-conversation receipts. Opening one never prepares the pack."""
import argparse
import contextlib
import fcntl
import json
import os
from pathlib import Path
import re
import tempfile
import uuid

SCHEMA = 'bc.testing_session.v1'


def caller_owner():
    owner = os.environ.get('PI_SESSION_ID') or os.environ.get('BC_OWNER_SESSION')
    if not owner:
        raise ValueError('stable conversation identity unavailable; set BC_OWNER_SESSION explicitly')
    return owner


def session_path(root, session):
    if not re.fullmatch(r'[a-f0-9]{32}', session):
        raise ValueError('invalid verification session ID')
    directory = Path(root) / '.worklane/testing-sessions'
    if directory.is_symlink() or directory.parent.is_symlink():
        raise ValueError('session directory must not be a symlink')
    directory.mkdir(parents=True, exist_ok=True)
    path = directory / (session + '.json')
    if path.is_symlink():
        raise ValueError('session receipt must not be a symlink')
    return path


@contextlib.contextmanager
def locked(root):
    directory = Path(root) / '.worklane/testing-sessions'
    if directory.is_symlink() or directory.parent.is_symlink():
        raise ValueError('session directory must not be a symlink')
    directory.mkdir(parents=True, exist_ok=True)
    lock = directory / '.lock'
    fd = os.open(lock, os.O_CREAT | os.O_RDWR | os.O_NOFOLLOW, 0o600)
    with os.fdopen(fd, 'w') as stream:
        fcntl.lockf(stream, fcntl.LOCK_EX)
        yield


def atomic_write(path, value):
    data = json.dumps(value, sort_keys=True, indent=2) + '\n'
    if len(data.encode()) > 1024 * 1024:
        raise ValueError('session receipt exceeds bounded size')
    fd, temporary = tempfile.mkstemp(prefix='.receipt-', dir=path.parent)
    try:
        with os.fdopen(fd, 'w') as output:
            output.write(data)
            output.flush()
            os.fsync(output.fileno())
        os.replace(temporary, path)
    finally:
        Path(temporary).unlink(missing_ok=True)


def load(root, session, owner):
    path = session_path(root, session)
    if path.stat().st_size > 1024 * 1024:
        raise ValueError('oversized session receipt')
    value = json.loads(path.read_text())
    if value.get('schema') != SCHEMA or value.get('owner') != owner or value.get('session_id') != session:
        raise ValueError('verification session belongs to another conversation or has invalid schema')
    return value


def begin(root, owner):
    if not owner or len(owner) > 512:
        raise ValueError('invalid owner identity')
    session = uuid.uuid4().hex
    value = dict(schema=SCHEMA, session_id=session, owner=owner, generation=0,
                 baseline='pending', shared_fingerprint=None, mods={}, invalidations=['baseline not started'])
    with locked(root):
        atomic_write(session_path(root, session), value)
    return value


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--root', default=str(Path(__file__).resolve().parent.parent))
    sub = parser.add_subparsers(dest='action', required=True)
    start = sub.add_parser('begin')
    start.add_argument('--owner-session', required=True)
    sub.add_parser('status').add_argument('session')
    end = sub.add_parser('end')
    end.add_argument('session')
    end.add_argument('--confirm-terminated-run', help='explicit owner confirmation after complete owned-process cleanup')
    args = parser.parse_args()
    owner = caller_owner()
    if args.action == 'begin':
        if args.owner_session != owner:
            raise ValueError('owner-session must match the calling conversation')
        print(json.dumps(begin(args.root, owner)))
    else:
        with locked(args.root):
            value = load(args.root, args.session, owner)
            if args.action == 'end':
                if value.get('active_run'):
                    confirmed = args.confirm_terminated_run
                    if confirmed != value['active_run'] or not re.fullmatch(r'\d{8}T\d{6}Z-\d+', confirmed or ''):
                        raise ValueError('session is in use; uncertain owner liveness requires reconciliation')
                    pid = int(confirmed.rsplit('-', 1)[1])
                    if not 1 <= pid <= 2**31 - 1:
                        raise ValueError('invalid confirmed run PID')
                    try:
                        os.kill(pid, 0)
                    except ProcessLookupError:
                        pass
                    else:
                        raise ValueError('confirmed run coordinator still exists; stop and clean its owned tree first')
                session_path(args.root, args.session).unlink()
                value = dict(schema=SCHEMA, session_id=args.session, state='ended')
            print(json.dumps(value))


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError, json.JSONDecodeError) as failure:
        print(json.dumps(dict(schema=SCHEMA, state='invalid', error=str(failure))))
        raise SystemExit(2)

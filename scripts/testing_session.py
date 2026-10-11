#!/usr/bin/env python3
"""Bounded live-conversation receipts. Opening one never prepares the pack."""
import argparse
import contextlib
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
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


def load(root, session, owner, allow_helpers=False):
    path = session_path(root, session)
    if path.stat().st_size > 1024 * 1024:
        raise ValueError('oversized session receipt')
    value = json.loads(path.read_text())
    admitted = allow_helpers and owner in value.get('helpers', [])
    if value.get('schema') != SCHEMA or (value.get('owner') != owner and not admitted) or value.get('session_id') != session:
        raise ValueError('verification session belongs to another conversation or has invalid schema')
    return value


def begin(root, owner):
    if not owner or len(owner) > 512:
        raise ValueError('invalid owner identity')
    session = uuid.uuid4().hex
    value = dict(schema=SCHEMA, session_id=session, owner=owner, generation=0,
                 baseline='pending', shared_fingerprint=None, mods={}, helpers=[], shared_context={}, invalidations=['baseline not started'])
    with locked(root):
        atomic_write(session_path(root, session), value)
    return value


def git_text(repo, *args):
    directory = Path.home() / '.tmp/bounded-commands'
    directory.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryFile(dir=directory) as output:
        status = subprocess.run(['git', *args], cwd=repo, stdout=output, stderr=subprocess.STDOUT, timeout=30)
        if status.returncode:
            raise ValueError('live source inspection failed for ' + str(repo))
        if output.tell() > 4 * 1024 * 1024:
            raise ValueError('live source inspection exceeds bounded output')
        output.seek(0)
        return output.read().decode()


def file_identity(path):
    if path.is_symlink():
        return 'link:' + os.readlink(path)
    if not path.is_file():
        return 'missing'
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(block)
    return digest.hexdigest()


def source_identity(repo):
    names = set(git_text(repo, 'ls-files', '-z', '--cached', '--others', '--exclude-standard').split('\0')) - {''}
    entries = [(name, file_identity(repo / name)) for name in names]
    entries += [('HEAD', git_text(repo, 'rev-parse', 'HEAD')), ('status', git_text(repo, 'status', '--porcelain'))]
    digest = hashlib.sha256()
    for name, value in sorted(entries):
        digest.update((name + '\0' + value + '\0').encode())
    return digest.hexdigest()


def matches_pattern(name, pattern):
    # Workspace-relative Java glob subset used by the authored verification manifest.
    tokens = re.split(r'(\*\*|\*|\?)', pattern)
    regex = ''.join('.*' if part == '**' else '[^/]*' if part == '*' else '[^/]' if part == '?' else re.escape(part) for part in tokens)
    return re.fullmatch(regex, name) is not None


def inspect_live(root, receipt):
    manifest = json.loads((root / 'gradle/active-custom-mods.json').read_text())
    mods = manifest['mods']
    invalid = []
    changed = set()
    if receipt.get('baseline') != 'complete':
        invalid.append('full baseline required: ' + receipt.get('baseline', 'unknown'))
    context = receipt.get('shared_context', {})
    if receipt.get('baseline') == 'complete' and not context:
        invalid.append('full baseline required: live input context unavailable')
    patterns = manifest.get('verificationInputs', [])
    names = set(git_text(root, 'ls-files', '-z', '--cached', '--others', '--exclude-standard').split('\0')) - {''}
    names = {name for name in names if any(matches_pattern(name, pattern) for pattern in patterns)
             and name != 'index.toml' and not (name.startswith('mods/') and name.endswith('.jar')) and not name.startswith('.worklane/')}
    expected_names = set(context) - {'input_patterns', 'manifest', 'java', 'toolchain', 'sdk', '_patterns', '_java_home'}
    if context and (context.get('_patterns') != '\0'.join(patterns) or names != expected_names or
            any(file_identity(root / name) != context.get(name) for name in names) or
            file_identity(root / 'gradle/active-custom-mods.json') != context.get('manifest') or
            file_identity(root / 'docs/toolchains.md') != context.get('sdk') or
            file_identity(Path(context.get('_java_home', '/nonexistent')) / 'release') != context.get('toolchain')):
        invalid.append('full baseline required: shared verification inputs changed')
    for mod in mods:
        name = mod['repository']
        repo = root.parent / 'mod_source' / name
        prior = receipt.get('mods', {}).get(name)
        if prior is None or prior.get('source') != source_identity(repo) or prior.get('tasks') != mod['tasks'] or \
                file_identity(root / 'mods' / mod['artifact']) != prior.get('sha256') or \
                prior.get('support_hashes', {}) != {path: file_identity(repo / path) for path in mod.get('supportArtifacts', [])} or \
                prior.get('dependencies', {}) != {provider: receipt.get('mods', {}).get(provider, {}).get('sha256', 'missing') for provider in mod.get('dependsOn', [])}:
            changed.add(name)
    while True:
        closure = changed | {mod['repository'] for mod in mods if any(provider in changed for provider in mod.get('dependsOn', []))}
        if closure == changed:
            break
        changed = closure
    invalid.extend(sorted(changed))
    return dict(valid_reuse=not invalid and not receipt.get('active_run'), invalidations=invalid,
                affected_providers=sorted(changed), authority='observational_only; preparation rechecks under session lock')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--root', default=str(Path(__file__).resolve().parent.parent))
    sub = parser.add_subparsers(dest='action', required=True)
    start = sub.add_parser('begin')
    start.add_argument('--owner-session', required=True)
    start.add_argument('--replace', help='explicit replacement of an idle owned receipt')
    sub.add_parser('status').add_argument('session')
    admit = sub.add_parser('admit')
    admit.add_argument('session')
    admit.add_argument('--helper-session', required=True)
    revoke = sub.add_parser('revoke')
    revoke.add_argument('session')
    revoke.add_argument('--helper-session', required=True)
    end = sub.add_parser('end')
    end.add_argument('session')
    end.add_argument('--confirm-terminated-run', help='explicit owner confirmation after complete owned-process cleanup')
    args = parser.parse_args()
    owner = caller_owner()
    if args.action == 'begin':
        if args.owner_session != owner:
            raise ValueError('owner-session must match the calling conversation')
        if args.replace:
            with locked(args.root):
                previous = load(args.root, args.replace, owner)
                if previous.get('active_run'):
                    raise ValueError('cannot replace an active verification coordinator')
                session_path(args.root, args.replace).unlink()
        print(json.dumps(begin(args.root, owner)))
    else:
        with locked(args.root):
            value = load(args.root, args.session, owner, allow_helpers=args.action == 'status')
            if args.action in ('admit', 'revoke'):
                if value.get('active_run'):
                    raise ValueError('cannot change helper admission during verification')
                helper = args.helper_session
                if not helper or len(helper) > 512 or helper == owner or any(c.isspace() for c in helper):
                    raise ValueError('invalid helper conversation identity')
                helpers = set(value.get('helpers', []))
                if args.action == 'admit':
                    helpers.add(helper)
                else:
                    helpers.discard(helper)
                if len(helpers) > 16:
                    raise ValueError('helper admission exceeds bounded count')
                value['helpers'] = sorted(helpers)
                atomic_write(session_path(args.root, args.session), value)
            elif args.action == 'status':
                value = dict(value, live_inspection=inspect_live(Path(args.root), value))
            elif args.action == 'end':
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

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

SCHEMA = 'bc.testing_session.v2'


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
                 baseline='pending', shared_fingerprint=None, mods={}, checkpoints={}, helpers=[], shared_context={},
                 state='open', reuse_history=True, invalidations=['baseline not started'])
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


def fingerprint(entries):
    digest = hashlib.sha256()
    for name, value in sorted(entries, key=lambda entry: entry[0]):
        digest.update((name + '\0' + value + '\0').encode())
    return digest.hexdigest()


def no_symlink(root, path):
    path = Path(path)
    try:
        parts = path.relative_to(root).parts
    except ValueError:
        return False
    current = root
    if root.is_symlink():
        return False
    for part in parts:
        if part == '..':
            return False
        current = current / part
        if current.is_symlink():
            return False
    return True


def live_entries(root, patterns, shared=False):
    if any(pattern.startswith('/') or '..' in pattern.split('/') for pattern in patterns):
        raise ValueError('unsafe verification input pattern')
    names = set(git_text(root, 'ls-files', '-z', '--cached', '--others', '--exclude-standard').split('\0')) - {''}
    return [(name, file_identity(root / name)) for name in names
            if any(matches_pattern(name, pattern) for pattern in patterns) and not (shared and (
                name in ('gradle/active-custom-mods.json', 'index.toml') or
                (name.startswith('mods/') and name.endswith('.jar')) or name.startswith('.worklane/')))]


def current_shared_entries(root, patterns, context):
    java = Path(context.get('_java_home', '/nonexistent'))
    gradle = Path(context.get('_gradle_home', '/nonexistent')) / 'lib'
    gradle_hash = fingerprint([(str(path.relative_to(gradle)), file_identity(path))
                               for path in gradle.rglob('*') if path.is_file()]) if gradle.is_dir() else 'unresolved'
    return live_entries(root, patterns, shared=True) + [
        ('input_patterns', '\0'.join(patterns)), ('java', context.get('java', 'unresolved')),
        ('toolchain', file_identity(java / 'release')), ('java_binary', file_identity(java / 'bin/java')),
        ('kotlin', file_identity(Path(context.get('_kotlin_path', '/nonexistent')))), ('gradle', gradle_hash)]


def contract_identity(root, mod, shared):
    patterns = mod.get('verificationInputs', [])
    return fingerprint(shared + live_entries(root, patterns) + [
        ('build_identity', build_identity(root, mod, shared)),
        ('scoped_patterns', '\0'.join(patterns)), ('repository', mod['repository']),
        ('artifact', mod['artifact']), ('mod_id', mod['modId']), ('tasks', '\0'.join(mod['tasks'])),
        ('dependencies', '\0'.join(mod.get('dependsOn', []))), ('support', '\0'.join(mod.get('supportArtifacts', [])))])


def build_identity(root, mod, shared):
    patterns = mod.get('buildInputs')
    if patterns is None:
        patterns = mod.get('verificationInputs', [])
    return fingerprint(shared + live_entries(root, patterns) + [
        ('scoped_patterns', '\0'.join(patterns)),
        ('source', source_identity(root.parent / 'mod_source' / mod['repository'])),
        ('repository', mod['repository']), ('artifact', mod['artifact']), ('mod_id', mod['modId']),
        ('dependencies', '\0'.join(mod.get('dependsOn', []))), ('support', '\0'.join(mod.get('supportArtifacts', [])))])


def checkpoint_build_valid(root, mod, checkpoint, contract, build, dependencies):
    return bool(checkpoint.get('build_id') and checkpoint.get('build_fingerprint') == build and
                checkpoint_valid(root, mod, dict(checkpoint, tasks=mod['tasks'], shared_fingerprint=contract), contract, dependencies))


def checkpoint_path(root, mod, checkpoint):
    origin = checkpoint.get('verification_id', '')
    artifact = mod['artifact']
    if not re.fullmatch(r'[A-Za-z0-9_-]{1,80}', origin) or not re.fullmatch(r'[A-Za-z0-9._-]+\.jar', artifact):
        raise ValueError('unsafe checkpoint origin or artifact')
    return root / '.worklane/testing-sessions/artifacts' / origin / artifact


def checkpoint_valid(root, mod, checkpoint, contract, dependencies):
    try:
        candidate = checkpoint_path(root, mod, checkpoint)
        supports = checkpoint.get('support_hashes', {})
        if (not checkpoint.get('owner') or checkpoint.get('shared_fingerprint') != contract or
                checkpoint.get('source') != source_identity(root.parent / 'mod_source' / mod['repository']) or
                checkpoint.get('tasks') != mod['tasks'] or checkpoint.get('dependencies', {}) != dependencies or
                checkpoint.get('staged_path') != str(candidate.relative_to(root)) or
                not no_symlink(root, candidate) or not candidate.is_file() or
                file_identity(candidate) != checkpoint.get('sha256') or set(supports) != set(mod.get('supportArtifacts', []))):
            return False
        for relative, sha in supports.items():
            if Path(relative).is_absolute() or '..' in Path(relative).parts:
                return False
            support = candidate.with_name(candidate.name + '.support') / relative
            if not no_symlink(root, support) or not support.is_file() or file_identity(support) != sha:
                return False
        return True
    except (ValueError, OSError):
        return False


def inspect_live(root, receipt):
    root = Path(root)
    manifest = json.loads((root / 'gradle/active-custom-mods.json').read_text())
    mods = manifest['mods']
    context = receipt.get('shared_context', {})
    archive = root / '.worklane/testing-sessions/completed'
    if not context and no_symlink(root, archive) and archive.is_dir():
        for candidate in sorted(archive.iterdir()):
            if (re.fullmatch(r'[A-Za-z0-9_-]{1,80}\.json', candidate.name) and candidate.is_file() and
                    no_symlink(root, candidate) and candidate.stat().st_size <= 1024 * 1024):
                try:
                    prior = json.loads(candidate.read_text())
                    if prior.get('schema') == SCHEMA and not prior.get('active_run'):
                        context = prior.get('shared_context', {}) or context
                except (ValueError, OSError):
                    pass
    shared = current_shared_entries(root, manifest.get('verificationInputs', []), context)
    contracts = {mod['repository']: contract_identity(root, mod, shared) for mod in mods}
    builds = {mod['repository']: build_identity(root, mod, shared) for mod in mods}
    checkpoints = {}
    build_candidates = {}
    archive = root / '.worklane/testing-sessions/completed'
    if receipt.get('reuse_history', True) and no_symlink(root, archive) and archive.is_dir():
        for path in sorted(archive.iterdir()):
            if (not re.fullmatch(r'[A-Za-z0-9_-]{1,80}\.json', path.name) or not no_symlink(root, path) or
                    not path.is_file() or path.stat().st_size > 1024 * 1024):
                continue
            try:
                historical = json.loads(path.read_text())
            except (ValueError, OSError):
                continue
            if historical.get('schema') != SCHEMA or historical.get('active_run'):
                continue
            for mod in mods:
                checkpoint = historical.get('checkpoints', {}).get(mod['repository'])
                if checkpoint:
                    if checkpoint_valid(root, mod, checkpoint, contracts[mod['repository']], checkpoint.get('dependencies', {})):
                        checkpoints[mod['repository']] = checkpoint
                    if checkpoint_build_valid(root, mod, checkpoint, contracts[mod['repository']], builds[mod['repository']], checkpoint.get('dependencies', {})):
                        build_candidates[mod['repository']] = checkpoint
    checkpoints.update(receipt.get('checkpoints', {}))
    build_candidates.update(receipt.get('checkpoints', {}))
    build_eligible = {}
    for mod in mods:
        name = mod['repository']
        candidate = build_candidates.get(name)
        dependencies = {provider: build_candidates.get(provider, receipt.get('mods', {}).get(provider, {})).get('sha256', 'missing')
                        for provider in mod.get('dependsOn', [])}
        if candidate and checkpoint_build_valid(root, mod, candidate, contracts[name], builds[name], dependencies):
            build_eligible[name] = candidate['build_id']
    changed = set()
    origins = {}
    for mod in mods:
        name = mod['repository']
        checkpoint = checkpoints.get(name)
        dependencies = {provider: checkpoints.get(provider, receipt.get('mods', {}).get(provider, {})).get('sha256', 'missing')
                        for provider in mod.get('dependsOn', [])}
        if checkpoint is None or not checkpoint_valid(root, mod, checkpoint, contracts[name], dependencies):
            changed.add(name)
        else:
            origins[name] = checkpoint['verification_id']
    while True:
        closure = changed | {mod['repository'] for mod in mods if any(provider in changed for provider in mod.get('dependsOn', []))}
        if closure == changed:
            break
        changed = closure
    invalid = sorted(changed)
    current_java = os.environ.get('JAVA_HOME')
    if current_java and Path(current_java).resolve() != Path(context.get('_java_home', '/nonexistent')).resolve():
        invalid.append('resolved Java identity differs; preparation must resolve current toolchain')
        changed = {mod['repository'] for mod in mods}
        build_eligible = {}
    if receipt.get('schema') != SCHEMA:
        invalid.append('incompatible receipt schema; retained history is not eligible')
        changed = {mod['repository'] for mod in mods}
        build_eligible = {}
    return dict(valid_reuse=not invalid and not receipt.get('active_run') and receipt.get('state', 'open') == 'open',
                session_preparation_allowed=not receipt.get('active_run') and receipt.get('state', 'open') == 'open', invalidations=invalid,
                affected_providers=sorted(changed), build_reuse_candidates=build_eligible,
                build_reuse_authority='conditional on exact current-run dependency bytes; required verification still runs', verification_origins={name: origin for name, origin in origins.items() if name not in changed},
                runtime_acceptance='not evaluated; no historical runtime result is promoted',
                authority='observational_only; preparation rechecks under session lock')


def confirm_terminated_run(value, confirmed):
    # Explicit owner assertion covers complete owned-tree cleanup. PID absence alone
    # does not establish that descendants or other owned resources were cleaned.
    if confirmed != value.get('active_run') or not re.fullmatch(r'\d{8}T\d{6}Z-\d+', confirmed or ''):
        raise ValueError('session is in use; uncertain owner liveness requires reconciliation')
    pid = int(confirmed.rsplit('-', 1)[1])
    if not 1 <= pid <= 2**31 - 1:
        raise ValueError('invalid confirmed run PID')
    try:
        os.kill(pid, 0)
    except ProcessLookupError:
        return
    raise ValueError('confirmed run coordinator still exists; stop and clean its owned tree first')


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
    recover = sub.add_parser('recover')
    recover.add_argument('session')
    recover.add_argument('--confirm-terminated-run', help='explicit owner confirmation after complete owned-process cleanup')
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
                previous['state'] = 'ended'
                atomic_write(session_path(args.root, args.replace), previous)
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
                    confirm_terminated_run(value, args.confirm_terminated_run)
                value = dict(value, state='ended', active_run=None)
                atomic_write(session_path(args.root, args.session), value)
            elif args.action == 'recover':
                if not value.get('active_run') or value.get('baseline') == 'complete':
                    raise ValueError('recovery requires an interrupted active verification run')
                confirm_terminated_run(value, args.confirm_terminated_run)
                value = dict(value, baseline='failed', active_run=None)
                atomic_write(session_path(args.root, args.session), value)
            print(json.dumps(value))


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError, json.JSONDecodeError) as failure:
        print(json.dumps(dict(schema=SCHEMA, state='invalid', error=str(failure))))
        raise SystemExit(2)

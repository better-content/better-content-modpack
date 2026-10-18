#!/usr/bin/env python3
"""Retain generated work by default; prune only explicit reclaimable data while quiet/stable/messy."""
from __future__ import annotations
import argparse
import contextlib
import fcntl
import hashlib
import json
import math
import os
from pathlib import Path
import re
import stat
import subprocess
import sys
import time
import uuid

SCHEMA = 'bc.workspace_maintenance.v3'
LEGACY_SCHEMA = 'bc.workspace_maintenance.v2'
GIB = 1024 ** 3
DEFAULT_THRESHOLDS = {'warning_used': 80, 'critical_used': 90,
                      'warning_free': 50 * GIB, 'critical_free': 20 * GIB,
                      'messy_bytes': 20 * GIB}
ID = re.compile(r'^[a-zA-Z0-9][a-zA-Z0-9_.-]{0,119}$')
REPO_OUTPUTS = {'build', '.gradle', '.kotlin', 'generated', 'dist', 'logs',
                'crash-reports', 'screenshots', 'saves', 'world', 'server-instance',
                'server-template', '__pycache__'}
CODEX_OUTPUTS = {'sessions', 'generated_images', 'cache', '.tmp', 'tmp',
                 'shell_snapshots', 'history.jsonl', 'session_index.jsonl',
                 'models_cache.json', 'thread-writer-locks',
                 'tui-thread-reference-capabilities'}
GRADLE_OUTPUTS = {'caches', 'wrapper', 'daemon', 'native', '.tmp', 'workers',
                  'notifications', 'kotlin-profile', 'undefined-build'}
# Installed JDKs, packages, plugins, skills, auth/config and toolchains are inputs.
PROTECTED = ['.codex/auth.json', '.codex/config.toml', '.codex/AGENTS.md',
             '.codex/hooks.json', '.codex/packages', '.codex/plugins', '.codex/skills',
             '.pi/agent/auth.json', '.pi/agent/settings.json', '.pi/agent/extensions',
             '.pi/agent/sessions',  # User conversation history, including inactive chats.
             '.sdkman', '.local/bin', '.local/lib', '.gradle/jdks',
             '.cache/ms-playwright', '.cache/selenium', '.tmp/.ICE-unix', '.tmp/.X11-unix', '.tmp/.font-unix', '.tmp/.XIM-unix',
             'better-content-modpack/mods', 'workspace_artifacts/README.md']


def exists(path):
    return os.path.lexists(path)


def beneath(path, root):
    return path == root or root in path.parents


def signature(path):
    s = path.lstat()
    return [s.st_dev, s.st_ino, stat.S_IFMT(s.st_mode)]


def allocated(path):
    if not exists(path):
        return 0
    # du does not follow symbolic links; bytes are allocated rather than apparent.
    r = subprocess.run(['du', '-sk', '--', str(path)], capture_output=True, text=True, timeout=30)
    if r.returncode:
        raise RuntimeError('cannot measure target: ' + str(path))
    return int(r.stdout.split()[0]) * 1024


def git_inputs(repo):
    def git(*args):
        r = subprocess.run(['git', '-C', str(repo), *args], capture_output=True, timeout=30)
        if r.returncode:
            raise RuntimeError('cannot inspect Git inputs: ' + str(repo))
        return [repo / os.fsdecode(p) for p in r.stdout.split(b'\0') if p]
    # Unknown/untracked data outside explicit output roots is never classified.
    return git('ls-files', '-z') + git('ls-files', '--others', '--exclude-standard', '-z')


def latest_build_outputs(workspace):
    """Retain the latest complete numeric/lettered ZIP pair, independently of test verdicts."""
    dist = workspace / 'better-content-modpack/dist'
    if not dist.is_dir() or dist.is_symlink():
        return []
    complete = []
    for build in dist.iterdir():
        number = re.fullmatch(r'build-([0-9]+)([a-z]?)', build.name)
        if not number or build.is_symlink() or not build.is_dir():
            continue
        pair = [build / side / 'better-content.zip' for side in ('client', 'server')]
        if all(not p.parent.is_symlink() and not p.is_symlink() and p.is_file() for p in pair):
            complete.append(((int(number[1]), number[2]), pair))
    return max(complete, key=lambda entry: entry[0])[1] if complete else []


def discover(workspace):
    pack = workspace / 'better-content-modpack'
    repos = [pack] if (pack / '.git').exists() else []
    source = workspace / 'mod_source'
    if source.is_dir():
        repos += sorted(p for p in source.iterdir() if not p.is_symlink() and (p / '.git').exists())
    targets = {}
    def add(p, category):
        if exists(p):
            targets[p] = category
    for repo in repos:
        for p in repo.iterdir():
            if p.name in REPO_OUTPUTS or p.name == 'run' or p.name.startswith('run-'):
                if p.is_dir() or p.is_symlink():
                    add(p, 'repository output/fixture')
            elif p.suffix in {'.log', '.hprof'}:
                add(p, 'runtime log/dump')
    # Authored nested Gradle modules are build owners too. Do not search generated
    # trees or traverse symlinks; existing input/protected-path rules still apply.
    for repo in repos:
        for base, dirs, files in os.walk(repo, followlinks=False):
            dirs[:] = [name for name in dirs if name not in REPO_OUTPUTS and name != '.git'
                       and name != 'run' and not name.startswith('run-') and not (Path(base) / name).is_symlink()]
            if Path(base) != repo and ('build.gradle' in files or 'build.gradle.kts' in files):
                for name in ('build', '.gradle', '.kotlin'):
                    add(Path(base) / name, 'repository output/fixture')
    add(pack / '.worklane/testing-sessions', 'verification records and immutable artifacts')
    artifacts = workspace / 'workspace_artifacts'
    if artifacts.is_dir():
        for p in artifacts.iterdir():
            if p.name == 'README.md':
                continue
            if p.is_dir() and not p.is_symlink():
                for child in p.iterdir():
                    add(child, 'disposable review/delivery/evidence')
            else:
                add(p, 'disposable review/delivery/evidence')
    for name in ('.tmp', '.cache'):
        base = workspace / name
        if base.is_dir():
            for p in base.iterdir():
                add(p, 'shared rebuildable dependency cache' if p == workspace / '.cache/bc' else 'temporary data/cache')
    for name in GRADLE_OUTPUTS:
        add(workspace / '.gradle' / name, 'shared rebuildable Gradle cache')
    for name in CODEX_OUTPUTS:
        add(workspace / '.codex' / name, 'agent generated state')
    codex = workspace / '.codex'
    if codex.is_dir():
        for p in codex.iterdir():
            if '.sqlite' in p.name:
                add(p, 'agent generated database')
            # Credential backups are credentials, not generated cleanup targets.
    pi = workspace / '.pi/agent'
    for name in ('web-search-cache', 'models-store.json'):
        add(pi / name, 'agent generated cache')
    minecraft = workspace / '.minecraft'
    if minecraft.is_dir():
        for name in ('saves', 'logs', 'crash-reports', 'screenshots'):
            add(minecraft / name, 'development world/runtime data')
    for name in ('forge-1.20.1-47.4.22-installer.jar.log', 'info:-'):
        add(workspace / name, 'abandoned installer/image diagnostic output')
    for name in ('bc-debug-refresh', 'bc-plan-dispatch', 'journal-debug'):
        add(workspace / '.local/state' / name, 'old task orchestration state')
    # Preserve tracked launcher libraries; remove only untracked download files.
    libraries = pack / 'libraries'
    if libraries.is_dir() and pack in repos:
        tracked = set(git_inputs(pack))
        for base, dirs, files in os.walk(libraries, followlinks=False):
            for name in files:
                p = Path(base) / name
                if p not in tracked:
                    add(p, 'rebuildable downloaded dependency')
    add(workspace / 'go/pkg/mod', 'shared rebuildable dependency cache')
    npm = workspace / '.npm'
    for name in ('_cacache', '_logs', '_npx'):
        add(npm / name, 'rebuildable npm cache')
    # Collapse nested targets without ever traversing a symlink.
    result = {}
    for p in sorted(targets, key=lambda p: (len(p.parts), str(p))):
        if not any(parent in result for parent in p.parents):
            result[p] = targets[p]
    return result, repos


def process_references(workspace):
    """Collect live cwd/open files/maps/absolute argv without emitting secrets."""
    # The cleanup interpreter is dependency-free; callers may still consume compiled
    # Kotlin script caches, sessions or output FDs and must not be exempted.
    ignored = {os.getpid()}
    refs = []
    for proc in Path('/proc').iterdir():
        if not proc.name.isdigit() or int(proc.name) in ignored:
            continue
        pid = int(proc.name)
        try:
            args = (proc / 'cmdline').read_bytes().split(b'\0')
            for arg in args:
                text = os.fsdecode(arg)
                if 'worklane-git-diff' in text and ' ' not in text:
                    refs.append((pid, workspace / '.cache/worklane'))
                # Only standalone absolute argv paths, not arbitrary shell scripts.
                if text.startswith(str(workspace) + '/') and '\n' not in text and ' ' not in text:
                    refs.append((pid, Path(text)))
                if text.startswith('-cp'):
                    continue
                for part in text.split(':'):
                    if part.startswith(str(workspace / '.gradle') + '/') and ' ' not in part:
                        refs.append((pid, Path(part)))
            for name in ('cwd', 'exe'):
                p = Path(os.readlink(proc / name).removesuffix(' (deleted)'))
                if beneath(p, workspace):
                    refs.append((pid, p))
            for fd in (proc / 'fd').iterdir():
                try:
                    p = Path(os.readlink(fd).removesuffix(' (deleted)'))
                    if p.is_absolute() and beneath(p, workspace):
                        refs.append((pid, p))
                except OSError:
                    pass
            for line in (proc / 'maps').read_text().splitlines():
                fields = line.split(None, 5)
                if len(fields) == 6 and fields[5].startswith(str(workspace) + '/'):
                    refs.append((pid, Path(fields[5].removesuffix(' (deleted)'))))
        except (FileNotFoundError, ProcessLookupError):
            continue
        except PermissionError as error:
            # Fail closed if a same-user process is not inspectable.
            # systemd's credential-isolated PAM helper has root-owned proc FDs and
            # cannot consume user task artifacts. Do not turn it into a workspace lease.
            if (proc / 'comm').read_text().strip() == '(sd-pam)':
                continue
            if proc.stat().st_uid == os.getuid():
                raise RuntimeError('cannot inspect process ' + str(pid)) from error
    # AF_UNIX FDs appear as socket:[inode], not as filesystem paths in /proc/PID/fd.
    for line in Path('/proc/net/unix').read_text().splitlines()[1:]:
        fields = line.split()
        if len(fields) >= 8 and fields[7].startswith(str(workspace) + '/'):
            refs.append(('unix-socket:' + fields[6], Path(fields[7])))
    # X servers close their PID lock FD after initialization but still need the lock.
    for lock in (workspace / '.tmp').glob('.X*-lock'):
        try:
            pid = int(lock.read_text().strip())
            if Path(f'/proc/{pid}/comm').read_text().strip() in {'Xvfb', 'Xorg', 'Xwayland'}:
                refs.append((pid, lock))
        except (OSError, ValueError):
            pass
    # Pi does not continuously hold its session FD open. Herdr publishes identities.
    session = os.environ.get('HERDR_SESSION')
    if session:
        r = subprocess.run(['herdr', '--session', session, 'agent', 'list'], capture_output=True, text=True, timeout=15)
        if r.returncode:
            raise RuntimeError('cannot inspect Herdr agent sessions')
        for agent in json.loads(r.stdout)['result']['agents']:
            identity = agent.get('agent_session') or {}
            if identity.get('kind') == 'path' and identity.get('value'):
                refs.append(('Herdr:' + agent['pane_id'], Path(identity['value'])))
                if agent.get('agent') == 'pi':
                    refs.append(('Herdr:' + agent['pane_id'], workspace / '.pi/agent/models-store.json'))
                    refs.append(('Herdr:' + agent['pane_id'], workspace / '.pi/agent/web-search-cache'))
    return refs


def atomic_json(workspace, path, value):
    safe_path(workspace, path)
    if path.is_symlink():
        raise ValueError('unsafe state file')
    temp = path.with_name(path.name + '.' + uuid.uuid4().hex + '.new')
    fd = os.open(temp, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
    try:
        with os.fdopen(fd, 'w') as stream:
            json.dump(value, stream)
            stream.write('\n')
            stream.flush()
            os.fsync(stream.fileno())
        temp.replace(path)
    finally:
        if exists(temp):
            temp.unlink()


def load_tasks(workspace):
    p = safe_path(workspace, workspace / '.worklane/disposable-tasks.json')
    if p.is_symlink():
        raise ValueError('unsafe task registry')
    if not p.exists():
        return {}
    data = json.loads(p.read_text())
    if data.get('schema') not in {SCHEMA, LEGACY_SCHEMA} or not isinstance(data.get('tasks'), dict):
        raise RuntimeError('invalid active-task registry')
    for task in data['tasks'].values():
        pin = task.get('pin_until')
        if pin is not None and (not isinstance(pin, (int, float)) or not math.isfinite(pin)):
            raise ValueError('invalid non-finite task pin; reconcile without deleting data')
    return data['tasks']


def save_tasks(workspace, tasks):
    p = safe_path(workspace, workspace / '.worklane/disposable-tasks.json')
    if p.is_symlink():
        raise ValueError('unsafe task registry')
    if not tasks:
        p.unlink(missing_ok=True)
        return
    p.parent.mkdir(parents=True, exist_ok=True)
    atomic_json(workspace, p, {'schema': SCHEMA, 'tasks': tasks})


def safe_path(workspace, value):
    p = Path(value)
    if not p.is_absolute():
        p = workspace / p
    if '..' in p.parts or p == workspace or not beneath(p, workspace):
        raise ValueError('target escapes workspace or names workspace root')
    parent = p.parent
    while parent != workspace:
        if parent.is_symlink():
            raise ValueError('symbolic-link ancestor: ' + str(parent))
        parent = parent.parent
    return p


def scan_boundary(path, workspace):
    """Preflight mounts before deleting even the first child."""
    if path.is_symlink():
        return
    device = workspace.stat().st_dev
    # Same-device bind mounts are not reliably detected by os.path.ismount.
    for line in Path('/proc/self/mountinfo').read_text().splitlines():
        encoded = line.split()[4]
        mount = Path(re.sub(r'\\([0-7]{3})', lambda match: chr(int(match[1], 8)), encoded))
        if mount != workspace and beneath(mount, path):
            raise ValueError('unexpected mount boundary: ' + str(mount))
    for base, dirs, files in os.walk(path, followlinks=False):
        for p in [Path(base)] + [Path(base) / n for n in dirs + files]:
            s = p.lstat()
            if not stat.S_ISLNK(s.st_mode) and (s.st_dev != device or os.path.ismount(p)):
                raise ValueError('unexpected mount boundary: ' + str(p))


def permission_blocker(path):
    uid = os.getuid()
    parent, child = path.parent.stat(), path.lstat()
    if parent.st_mode & stat.S_ISVTX and parent.st_uid != uid and child.st_uid != uid:
        return 'foreign-owned target in a sticky directory: ' + str(path)
    if path.is_dir() and not path.is_symlink():
        for base, dirs, files in os.walk(path, followlinks=False):
            directory = Path(base)
            info = directory.stat()
            if info.st_uid != uid and not os.access(directory, os.W_OK):
                return 'foreign owner controls directory: ' + str(directory)
            if info.st_mode & stat.S_ISVTX and info.st_uid != uid:
                for name in dirs + files:
                    if (directory / name).lstat().st_uid != uid:
                        return 'foreign-owned entry in sticky directory: ' + str(directory / name)
    return None


def shared_build_output(path, category):
    return category in {'shared rebuildable Gradle cache', 'shared rebuildable dependency cache'} or (
        category == 'repository output/fixture' and path.name in {'build', '.gradle', '.kotlin'})


def plan(workspace, refs=None, tasks=None):
    targets, repos = discover(workspace)
    refs = process_references(workspace) if refs is None else refs
    tasks = load_tasks(workspace) if tasks is None else tasks
    retention = load_retention(workspace)
    retained = protected_outputs(workspace, retention)
    dist = workspace / 'better-content-modpack/dist'
    boundaries = [p for _, p in refs] + retained
    boundaries += [safe_path(workspace, p) for p in retention['records']]
    leased_roots = []
    for task in tasks.values():
        until = task.get('pin_until')
        active = until > time.time() if until is not None else task.get('state', 'active') == 'active'
        if active:
            leased_roots += [safe_path(workspace, p) for p in task.get('paths', [])]
    boundaries += leased_roots
    # A live frame directory or exact recipe input does not protect sibling ZIPs/worlds.
    expanded = {}
    pending = list(targets.items())
    while pending:
        path, category = pending.pop()
        descendants = any(p != path and beneath(p, path) for p in boundaries)
        fully_leased = path in retained or path in [p for _, p in refs] or any(beneath(path, p) for p in leased_roots)
        splittable = True  # Explicit child retention/classification must constrain every output category.
        if splittable and descendants and not fully_leased \
                and path.is_dir() and not path.is_symlink():
            pending.extend((p, category) for p in path.iterdir())
        else:
            expanded[path] = category
    targets = dict(sorted(expanded.items(), key=lambda item: str(item[0])))
    inputs = [p for repo in repos for p in git_inputs(repo)]
    operating = [workspace / value for value in PROTECTED if exists(workspace / value)]
    operating += retained
    inputs += operating
    protected_by_target = {}
    for p in inputs:
        for boundary in (p, *p.parents):
            if boundary in targets:
                protected_by_target.setdefault(boundary, []).append(str(p))
    decisions = []
    for path, category in targets.items():
        safe_path(workspace, path)
        if not exists(path):
            continue  # Live producers may remove temporary paths during a read-only inventory.
        reasons = []
        protected = protected_by_target.get(path, []) + [str(p) for p in operating if beneath(path, p)]
        owners = [str(pid) for pid, p in refs if beneath(p, path)]
        # A mapped Gradle distribution/cache implies a shared build consumer.
        if category == 'shared rebuildable Gradle cache':
            owners += [str(pid) for pid, p in refs if beneath(p, workspace / '.gradle')]
        # SQLite WAL/SHM belong to the database, not independent disposable files.
        if '.sqlite' in path.name:
            base = path.name.split('.sqlite')[0] + '.sqlite'
            owners += [str(pid) for pid, p in refs if p.parent == path.parent and p.name.startswith(base)]
        for task_id, task in tasks.items():
            until = task.get('pin_until')
            active = (until > time.time()) if until is not None else task.get('state', 'active') == 'active'
            for value in task.get('paths', []):
                p = safe_path(workspace, value)
                if active and (beneath(p, path) or beneath(path, p)):
                    owners.append('task:' + task_id)
            if active and shared_build_output(path, category) and task.get('uses_build_cache', True):
                owners.append('task:' + task_id)
        record = retention_for(path, retention)
        classification = record['kind'] if record else (
            'reusable' if 'cache' in category or 'dependency' in category or category.startswith('verification') else 'unknown')
        # Unresolved diagnoses and completed registrations remain retention records, not leases.
        retained_tasks = [task_id for task_id, task in tasks.items()
                          if task.get('retention', 'retained') != 'released'
                          and any(beneath(path, safe_path(workspace, p)) or
                                  beneath(safe_path(workspace, p), path)
                                  for p in task.get('paths', []))]
        if retained_tasks:
            classification = 'retained'
            reasons.append('retained task material: ' + ', '.join(retained_tasks))
        action = ('protect_input' if protected else 'defer_active' if owners else
                  'delete' if classification == 'reclaimable' else 'retain')
        try:
            blocker = permission_blocker(path) if action == 'delete' else None
        except FileNotFoundError:
            continue
        if record:
            reasons.append(record['reason'])
        elif not protected and not owners:
            reasons.append('retained by default; classification is not deletion authorization')
        if blocker:
            reasons.append(blocker)
        if protected:
            if any(beneath(p, path) for p in retained):
                reasons.append('latest candidate or last known-good/support material; retention is not validation')
            else:
                reasons.append('authored, tracked or installed operating inputs inside output tree')
        if owners:
            reasons.append('active consumer')
        try:
            size = allocated(path)
        except RuntimeError:
            if not exists(path):
                continue
            if not owners:
                raise
            size = 0
            reasons.append('active changing payload: size unavailable')
        try:
            identity = signature(path)
        except FileNotFoundError:
            continue
        decisions.append({'path': str(path), 'category': category, 'action': action,
                          'classification': 'protected' if protected or owners else classification,
                          'owners': sorted(set(owners)), 'protected_inputs': protected,
                          'reasons': reasons, 'bytes': size, 'identity': identity, 'blocked_permission': blocker})
    for relative in PROTECTED:
        p = workspace / relative
        if exists(p) and not any(d['path'] == str(p) for d in decisions):
            decisions.append({'path': str(p), 'category': 'operating/source input',
                              'action': 'protect_input', 'owners': [], 'reasons': ['required input'],
                              'protected_inputs': [], 'bytes': 0, 'identity': signature(p)})
    return decisions


def delete_checked(path, workspace, expected, *, checkpoint=None):
    safe_path(workspace, path)
    history = workspace / '.pi/agent/sessions'
    if beneath(path, history) or beneath(history, path):
        raise ValueError('protected Pi conversation history: ' + str(path))
    if not exists(path):
        return
    if signature(path) != expected:
        raise ValueError('target substituted: ' + str(path))
    scan_boundary(path, workspace)
    # fd-relative recursion plus O_NOFOLLOW: a concurrent symlink swap cannot escape.
    def mount_id(fd):
        return next(line for line in Path(f'/proc/self/fdinfo/{fd}').read_text().splitlines()
                    if line.startswith('mnt_id:'))
    anchor_mount = None
    visited = 0
    def remove(parent_fd, name, identity=None):
        nonlocal visited
        visited += 1
        if checkpoint is not None and visited % 64 == 1:
            checkpoint()  # A large directory is not one uninterruptible cleanup batch.
        try:
            s = os.stat(name, dir_fd=parent_fd, follow_symlinks=False)
        except FileNotFoundError:
            return
        if identity is not None and [s.st_dev, s.st_ino, stat.S_IFMT(s.st_mode)] != identity:
            raise ValueError('target substituted during deletion')
        if stat.S_ISDIR(s.st_mode):
            fd = os.open(name, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW, dir_fd=parent_fd)
            try:
                opened = os.fstat(fd)
                if (opened.st_dev, opened.st_ino) != (s.st_dev, s.st_ino) or \
                        opened.st_dev != workspace.stat().st_dev or mount_id(fd) != anchor_mount:
                    raise ValueError('directory substituted or mount boundary')
                if not opened.st_mode & stat.S_IWUSR:
                    os.fchmod(fd, (opened.st_mode & 0o777) | 0o700)
                for child in os.listdir(fd):
                    remove(fd, child)
            finally:
                os.close(fd)
            after = os.stat(name, dir_fd=parent_fd, follow_symlinks=False)
            if (after.st_dev, after.st_ino) != (s.st_dev, s.st_ino):
                raise ValueError('directory substituted before removal')
            os.rmdir(name, dir_fd=parent_fd)
        else:
            os.unlink(name, dir_fd=parent_fd)
    # Anchor every parent component rather than following a substituted ancestor.
    fd = os.open(workspace, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)
    anchor_mount = mount_id(fd)
    try:
        for part in path.parent.relative_to(workspace).parts:
            next_fd = os.open(part, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW, dir_fd=fd)
            if mount_id(next_fd) != anchor_mount:
                os.close(next_fd)
                raise ValueError('mount boundary in target ancestor')
            os.close(fd)
            fd = next_fd
        remove(fd, path.name, expected)
    finally:
        os.close(fd)


def revalidate_batch(workspace, path, category):
    """Check noncooperating consumers/protection changes inside long directory removals."""
    refs = process_references(workspace)
    if any(beneath(p, path) for _, p in refs):
        raise RuntimeError('consumer appeared during deletion batch')
    if category == 'shared rebuildable Gradle cache' and any(beneath(p, workspace / '.gradle') for _, p in refs):
        raise RuntimeError('shared cache consumer appeared during deletion batch')
    if '.sqlite' in path.name:
        base = path.name.split('.sqlite')[0] + '.sqlite'
        if any(p.parent == path.parent and p.name.startswith(base) for _, p in refs):
            raise RuntimeError('database consumer appeared during deletion batch')
    data = load_retention(workspace)
    if any(beneath(p, path) or beneath(path, p) for p in protected_outputs(workspace, data)):
        raise RuntimeError('retention changed during deletion batch')
    record = retention_for(path, data)
    if not record or record['kind'] != 'reclaimable' or any(
            beneath(Path(name), path) and row['kind'] != 'reclaimable'
            for name, row in data['records'].items()):
        raise RuntimeError('classification changed during deletion batch')
    for task in load_tasks(workspace).values():
        pin = task.get('pin_until')
        active = pin > time.time() if pin is not None else task.get('state', 'active') == 'active'
        retained = task.get('retention', 'retained') != 'released'
        overlap = any(beneath(safe_path(workspace, p), path) or beneath(path, safe_path(workspace, p))
                      for p in task.get('paths', []))
        if (overlap and (active or retained)) or (active and task.get('uses_build_cache', True)
                                                and shared_build_output(path, category)):
            raise RuntimeError('task protection changed during deletion batch')
    repo = next((p for p in path.parents if (p / '.git').exists()), None)
    if repo and any(beneath(p, path) for p in git_inputs(repo)):
        raise RuntimeError('authored input appeared during deletion batch')


class DisposalBlocked(RuntimeError):
    def __init__(self, removed, blocked, transaction):
        super().__init__('permission-controlled disposable targets remain')
        self.removed, self.blocked, self.transaction = removed, blocked, transaction


def apply(workspace, decisions, transaction=None, *, admission=None):
    # Direct callers have the same gate as the CLI; a plan alone never authorizes deletion.
    if admission is None:
        raise RuntimeError('prune requires quiet/stable/messy maintenance admission')
    admission()
    state = workspace / '.worklane/disposal'
    if state.parent.is_symlink() or state.is_symlink():
        raise ValueError('unsafe disposal state directory')
    state.mkdir(parents=True, exist_ok=True)
    if transaction and not re.fullmatch(r'[a-f0-9]{32}', transaction):
        raise ValueError('invalid transaction ID')
    record = state / ((transaction or uuid.uuid4().hex) + '.json')
    if record.is_symlink():
        raise ValueError('unsafe transaction record')
    if transaction:
        data = json.loads(record.read_text())
        if data.get('schema') != SCHEMA or data.get('workspace') != str(workspace):
            raise ValueError('invalid disposal transaction')
        old = {d['path']: d for d in data['pending']}
        current = {d['path']: d for d in decisions}
        pending = []
        for path, d in old.items():
            if exists(Path(path)):
                if path not in current or current[path]['action'] != 'delete':
                    raise ValueError('resumed target now active or unclassified: ' + path)
                if signature(Path(path)) != d['identity']:
                    raise ValueError('resumed target substituted: ' + path)
                pending.append(d)
    else:
        pending = [d for d in decisions if d['action'] == 'delete']
    blocked = []
    def checkpoint():
        atomic_json(workspace, record, {'schema': SCHEMA, 'workspace': str(workspace), 'pending': pending + blocked})
    checkpoint()
    removed = []
    while pending:
        admission()  # New work requests yield before the next bounded target.
        d = pending[0]
        # Check live consumers and task leases again immediately before each target.
        fresh_refs = process_references(workspace)
        tasks = load_tasks(workspace)
        path = Path(d['path'])
        retention = load_retention(workspace)
        if any(beneath(p, path) or beneath(path, p) for p in protected_outputs(workspace, retention)):
            raise RuntimeError('target became latest retained build or known-good material; replan: ' + str(path))
        history = workspace / '.pi/agent/sessions'
        if beneath(path, history) or beneath(history, path):
            raise ValueError('protected Pi conversation history: ' + str(path))
        classification = retention_for(path, retention)
        if not classification or classification['kind'] != 'reclaimable':
            raise RuntimeError('target no longer explicitly reclaimable; replan: ' + str(path))
        if any(beneath(path, workspace / p) or beneath(workspace / p, path) for p in PROTECTED):
            raise RuntimeError('target intersects protected operating input')
        consumers = any(beneath(p, path) for _, p in fresh_refs)
        if d['category'] == 'shared rebuildable Gradle cache':
            consumers |= any(beneath(p, workspace / '.gradle') for _, p in fresh_refs)
        if '.sqlite' in path.name:
            base = path.name.split('.sqlite')[0] + '.sqlite'
            consumers |= any(p.parent == path.parent and p.name.startswith(base) for _, p in fresh_refs)
        if consumers:
            raise RuntimeError('target became active; resume after consumer exits: ' + d['path'])
        for task in tasks.values():
            if task.get('retention', 'retained') != 'released' and any(
                    beneath(safe_path(workspace, p), path) or beneath(path, safe_path(workspace, p))
                    for p in task.get('paths', [])):
                raise RuntimeError('target has retained task material')
            until = task.get('pin_until')
            active = until > time.time() if until is not None else task.get('state', 'active') == 'active'
            if active and (any(beneath(safe_path(workspace, p), path) or beneath(path, safe_path(workspace, p))
                               for p in task.get('paths', [])) or
                           (shared_build_output(path, d['category']) and task.get('uses_build_cache', True))):
                raise RuntimeError('target leased during deletion')
        # Concurrent source authoring changes an output tree into an input boundary.
        repo = next((p for p in path.parents if (p / '.git').exists()), None)
        if repo and any(beneath(p, path) for p in git_inputs(repo)):
            raise RuntimeError('target now contains authored input: ' + str(path))
        def batch_guard():
            admission()
            revalidate_batch(workspace, path, d['category'])
        try:
            delete_checked(Path(d['path']), workspace, d['identity'], checkpoint=batch_guard)
        except PermissionError as error:
            # Never chmod/chown foreign owners or mislabel their outputs as inputs.
            blocked.append({**d, 'blocked_permission': str(error)})
        else:
            removed.append(d['path'])
        pending.pop(0)
        if len(removed) % 10 == 0:
            checkpoint()
    if blocked:
        checkpoint()
        raise DisposalBlocked(removed, blocked, record.stem)
    record.unlink()
    return removed


def load_retention(workspace):
    path = safe_path(workspace, workspace / '.worklane/retention.json')
    if path.is_symlink():
        raise ValueError('unsafe retention registry')
    if not path.exists():
        return {'schema': SCHEMA, 'records': {}, 'stability': {'state': 'unknown'}}
    data = json.loads(path.read_text())
    if data.get('schema') != SCHEMA or not isinstance(data.get('records'), dict):
        raise ValueError('invalid retention registry')
    for name, record in data['records'].items():
        safe_path(workspace, name)
        if record.get('kind') not in {'retained', 'reusable', 'reclaimable', 'unknown'} or not record.get('reason'):
            raise ValueError('invalid retention classification')
    return data


def save_retention(workspace, data):
    path = safe_path(workspace, workspace / '.worklane/retention.json')
    path.parent.mkdir(exist_ok=True)
    atomic_json(workspace, path, data)


def retention_for(path, data):
    matches = [(len(Path(name).parts), record) for name, record in data['records'].items()
               if beneath(path, Path(name))]
    return max(matches, key=lambda item: item[0])[1] if matches else None


def protected_outputs(workspace, data):
    # Preserve the whole latest candidate, not ZIPs stripped of retry fixtures/support.
    result = list({p.parents[1] for p in latest_build_outputs(workspace)})
    stable = data.get('last_known_good', {})
    result += [safe_path(workspace, p) for p in stable.get('artifacts', {})]
    result += [safe_path(workspace, p) for p in stable.get('evidence', {})]
    result += [safe_path(workspace, p) for p, r in data['records'].items()
               if r['kind'] in {'retained', 'reusable'}]
    return result


def digest_file(workspace, path):
    path = safe_path(workspace, path)
    if path.is_symlink() or not path.is_file():
        raise ValueError('identity requires a regular non-symlink file: ' + str(path))
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(block)
    return digest.hexdigest()


def production_identity(workspace):
    pack = workspace / 'better-content-modpack'
    roots = ['pack.toml', 'index.toml', 'options.txt', 'config', 'defaultconfigs', 'kubejs',
             'mods', 'resourcepacks', 'shaderpacks', 'global_packs']
    files = []
    for root in roots:
        path = pack / root
        if path.is_symlink():
            raise ValueError('production input symlink: ' + str(path))
        if path.is_dir():
            files.extend(p for p in path.rglob('*') if p.is_file() or p.is_symlink())
        elif path.is_file():
            files.append(path)
    if not (pack / 'pack.toml').is_file():
        raise ValueError('cannot establish current production identity without pack.toml')
    digest = hashlib.sha256()
    for path in sorted(files):
        digest.update(str(path.relative_to(pack)).encode() + b'\0')
        digest.update(digest_file(workspace, path).encode() + b'\n')
    return digest.hexdigest()


def stable_now(workspace, data):
    stable = data.get('stability', {})
    if stable.get('state') != 'good' or not stable.get('basis') or not stable.get('evidence') or not stable.get('artifacts'):
        return False
    try:
        return (stable.get('production_identity') == production_identity(workspace) and
                all(digest_file(workspace, Path(p)) == expected
                    for group in ('artifacts', 'evidence') for p, expected in stable[group].items()))
    except (OSError, ValueError):
        return False


def working_agents():
    session = os.environ.get('HERDR_SESSION')
    if not session:
        raise RuntimeError('quiet state unknown: no Herdr session')
    result = subprocess.run(['herdr', '--session', session, 'agent', 'list'],
                            capture_output=True, text=True, timeout=15)
    if result.returncode:
        raise RuntimeError('quiet state unknown: Herdr unavailable')
    return [a['pane_id'] for a in json.loads(result.stdout)['result']['agents']
            if a.get('agent_status') not in {'idle', 'done'}]


def thresholds(data):
    values = {**DEFAULT_THRESHOLDS, **data.get('thresholds', {})}
    if any(not isinstance(v, (int, float)) or not 0 < v < float('inf') for v in values.values()):
        raise ValueError('invalid storage thresholds')
    if not values['warning_used'] < values['critical_used'] <= 100 or values['critical_free'] >= values['warning_free']:
        raise ValueError('inconsistent storage thresholds')
    return values


def storage_status(workspace, data, measurement=None):
    limits = thresholds(data)
    fs = os.statvfs(workspace)
    total, available = fs.f_blocks * fs.f_frsize, fs.f_bavail * fs.f_frsize
    used = (fs.f_blocks - fs.f_bfree) * fs.f_frsize
    percent = 100 * used / total if total else 100
    warnings = []
    if percent >= limits['critical_used'] or available < limits['critical_free']:
        warnings.append('critical filesystem pressure; does not authorize cleanup')
    elif percent >= limits['warning_used'] or available < limits['warning_free']:
        warnings.append('filesystem pressure; inspect retained/reclaimable inventory')
    measurement = measurement or data.get('measurement')
    if not measurement or time.time() - measurement.get('at', 0) > 3600:
        warnings.append('inventory absent/stale; run maintenance measure when appropriate')
    if measurement and measurement.get('reclaimable_bytes', 0) >= limits['messy_bytes']:
        warnings.append('reclaimable backlog; pruning still requires quiet and verified stable state')
    return {'schema': SCHEMA, 'capacity_scope': 'filesystem, not a confirmed lane quota',
            'total_bytes': total, 'used_bytes': used, 'available_bytes': available,
            'used_percent': percent, 'thresholds': limits, 'warnings': warnings,
            'measurement': measurement, 'recorded_stability': data.get('stability', {}).get('state', 'unknown'),
            'stability_revalidated': False}


def measured_inventory(decisions):
    return {'at': time.time(), 'scope': 'classified output inventory, not whole workspace usage',
            'allocated_bytes': sum(d['bytes'] for d in decisions),
            'reclaimable_bytes': sum(d['bytes'] for d in decisions
                                     if d['action'] == 'delete' and not d.get('blocked_permission')),
            'categories': {kind: sum(d['bytes'] for d in decisions if d.get('classification') == kind)
                           for kind in ('protected', 'retained', 'reusable', 'reclaimable', 'unknown')},
            'largest': [{'path': d['path'], 'bytes': d['bytes'], 'action': d['action']}
                        for d in sorted(decisions, key=lambda d: d['bytes'], reverse=True)[:10]]}


@contextlib.contextmanager
def maintenance_reservation(coordinator, owner):
    coordinator.reserve(owner)
    try:
        with coordinator.maintenance(owner):
            yield
    finally:
        coordinator.unreserve(owner)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--workspace', type=Path, default=Path(__file__).resolve().parents[2])
    sub = parser.add_subparsers(dest='command', required=True)
    sub.add_parser('audit')
    sub.add_parser('measure')
    status = sub.add_parser('status')
    status.add_argument('--cached', action='store_true', help='status never scans payloads')
    classify = sub.add_parser('classify')
    classify.add_argument('--path', required=True)
    classify.add_argument('--kind', choices=['retained', 'reusable', 'reclaimable', 'unknown'], required=True)
    classify.add_argument('--reason', required=True)
    stability = sub.add_parser('stability')
    stability.add_argument('--state', choices=['good', 'broken', 'unknown'], required=True)
    stability.add_argument('--basis', required=True)
    stability.add_argument('--artifact', action='append', default=[])
    stability.add_argument('--evidence', action='append', default=[])
    configure = sub.add_parser('configure')
    for key in DEFAULT_THRESHOLDS:
        configure.add_argument('--' + key.replace('_', '-'), type=float)
    prune = sub.add_parser('prune')
    prune.add_argument('--owner', required=True, help='explicit maintenance identity, not an arbitrary excluded pane')
    group = prune.add_mutually_exclusive_group(required=True)
    group.add_argument('--apply', action='store_true')
    group.add_argument('--resume')
    for command in ('begin', 'update', 'finish', 'release-retention'):
        task_parser = sub.add_parser(command)
        task_parser.add_argument('--task', required=True)
        task_parser.add_argument('--owner', default=os.environ.get('BC_OWNER_SESSION') or os.environ.get('PI_SESSION_ID'))
        task_parser.add_argument('--path', action='append', default=[])
        task_parser.add_argument('--pin-until', type=float)
        task_parser.add_argument('--no-build-cache', action='store_true')
        task_parser.add_argument('--reconcile-legacy', action='store_true')
        task_parser.add_argument('--reason')
        task_parser.add_argument('--apply', action='store_true', help='deprecated compatibility flag; finish never deletes')
    args = parser.parse_args(argv)
    workspace = args.workspace.absolute()
    if workspace.is_symlink() or not workspace.is_dir():
        raise ValueError('workspace must be a real directory')
    workspace = workspace.resolve()
    # Read-only inspection never creates state or takes deletion authority.
    retention = load_retention(workspace)
    if args.command == 'status':
        print(json.dumps(storage_status(workspace, retention), indent=2))
        return 0
    if args.command == 'audit':
        decisions = plan(workspace)
        measurement = measured_inventory(decisions)
        print(json.dumps({'schema': SCHEMA, 'workspace': str(workspace), 'decisions': decisions,
                          **measurement, 'storage': storage_status(workspace, retention, measurement)}, indent=2))
        return 0
    lock_root = workspace / '.worklane'
    lock_root.mkdir(exist_ok=True)
    if lock_root.is_symlink():
        raise ValueError('unsafe state directory')
    lock_path = lock_root / 'disposal.lock'
    fd = os.open(lock_path, os.O_RDWR | os.O_CREAT | os.O_NOFOLLOW, 0o600)
    try:
        try:
            fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            if args.command in {'begin', 'update'}:
                from workspace_coordination import Coordinator
                Coordinator(workspace / '.worklane/coordination').request_work()
            raise
        tasks = load_tasks(workspace)
        retention = load_retention(workspace)
        if args.command in {'begin', 'update', 'finish', 'release-retention'}:
            if not ID.fullmatch(args.task) or not args.owner:
                raise ValueError('valid task ID and stable --owner identity required')
            if args.command == 'begin':
                if args.task in tasks:
                    raise ValueError('task already registered; update active tasks or choose a new task ID')
                tasks[args.task] = {'state': 'active', 'paths': [], 'owner': args.owner,
                                    'created_at': time.time(), 'retention': 'retained'}
            elif args.task not in tasks:
                raise ValueError('task is not registered')
            task = tasks[args.task]
            if task.get('owner') != args.owner:
                if task.get('owner') or not args.reconcile_legacy or not args.reason:
                    raise ValueError('owner mismatch or legacy owner unknown; explicit reconciliation required')
                task['owner'] = args.owner
                task['reconciled'] = {'at': time.time(), 'reason': args.reason}
            if args.command in {'begin', 'update'}:
                if task.get('state') != 'active' or task.get('retention', 'retained') == 'released':
                    raise ValueError('cannot update finished/released activity; register a new task ID')
                if args.pin_until is not None and (not math.isfinite(args.pin_until) or args.pin_until <= time.time()):
                    raise ValueError('pin expiration must be finite and in the future')
                task['paths'] = sorted(set(task.get('paths', []) + [str(safe_path(workspace, p)) for p in args.path]))
                if args.command == 'begin' or args.pin_until is not None:
                    task['pin_until'] = args.pin_until
                if args.command == 'begin' or args.no_build_cache:
                    task['uses_build_cache'] = not args.no_build_cache and task.get('pin_until') is None
            elif args.command == 'finish':
                task['state'] = 'finished'
                task['finished_at'] = time.time()
            else:
                if task.get('state') != 'finished' or not args.reason:
                    raise ValueError('retention release requires finished task and explicit reason')
                task['retention'] = 'released'
                task['release_reason'] = args.reason
            task['updated_at'] = time.time()
            if args.command in {'begin', 'update'}:
                from workspace_coordination import Coordinator
                with Coordinator(workspace / '.worklane/coordination').transaction(admission=True):
                    save_tasks(workspace, tasks)
            else:
                save_tasks(workspace, tasks)
            print(json.dumps({'schema': SCHEMA, 'task': args.task, 'state': task['state'], 'removed_count': 0}))
            return 0
        if args.command == 'classify':
            path = safe_path(workspace, args.path)
            targets, _ = discover(workspace)
            if not exists(path) or not any(beneath(path, p) for p in targets):
                raise ValueError('classification requires an existing known output boundary')
            if not args.reason.strip():
                raise ValueError('classification reason required')
            retention['records'][str(path)] = {'kind': args.kind, 'reason': args.reason, 'at': time.time()}
        elif args.command == 'stability':
            stable = {'state': args.state, 'basis': args.basis, 'at': time.time()}
            if args.state == 'good':
                if not args.artifact or not args.evidence or not args.basis.strip():
                    raise ValueError('good state requires exact artifacts, evidence and acceptance basis')
                stable['production_identity'] = production_identity(workspace)
                for group, paths in [('artifacts', args.artifact), ('evidence', args.evidence)]:
                    stable[group] = {str(safe_path(workspace, p)): digest_file(workspace, Path(p)) for p in paths}
                retention['last_known_good'] = stable
            retention['stability'] = stable
        elif args.command == 'configure':
            retention['thresholds'] = {**retention.get('thresholds', {}),
                                      **{key: getattr(args, key) for key in DEFAULT_THRESHOLDS
                                         if getattr(args, key) is not None}}
            thresholds(retention)
        elif args.command == 'measure':
            previous = retention.get('measurement', {})
            measurement = measured_inventory(plan(workspace))
            measurement['growth_bytes'] = measurement['allocated_bytes'] - previous.get('allocated_bytes', measurement['allocated_bytes'])
            retention['measurement'] = measurement
        elif args.command == 'prune':
            # Quiet observation happens before reservation; the coordinator then closes admission.
            if working_agents():
                raise RuntimeError('workspace is not quiet: agents are working')
            from workspace_coordination import Coordinator
            coordinator = Coordinator(workspace / '.worklane/coordination')
            with maintenance_reservation(coordinator, args.owner):
                decisions = plan(workspace)
                measurement = measured_inventory(decisions)
                if not stable_now(workspace, retention):
                    raise RuntimeError('current modpack stability is unknown, broken, or changed')
                if measurement['reclaimable_bytes'] < thresholds(retention)['messy_bytes']:
                    raise RuntimeError('workspace is not messy enough to prune')
                def admission():
                    if coordinator.maintenance_should_yield() or working_agents():
                        raise RuntimeError('new work requested; maintenance yielding before next target')
                    if not stable_now(workspace, load_retention(workspace)):
                        raise RuntimeError('production/artifact/evidence stability changed; maintenance yielding')
                blocked, transaction = [], None
                try:
                    removed = apply(workspace, decisions, args.resume, admission=admission)
                except DisposalBlocked as error:
                    removed, blocked, transaction = error.removed, error.blocked, error.transaction
                print(json.dumps({'schema': SCHEMA, 'removed_count': len(removed),
                                  'removed_bytes': sum(d['bytes'] for d in decisions if d['path'] in set(removed)),
                                  'blocked_permission': blocked, 'resume_transaction': transaction}, indent=2))
                return 3 if blocked else 0
        save_retention(workspace, retention)
        print(json.dumps(storage_status(workspace, retention), indent=2))
        return 0
    finally:
        os.close(fd)


if __name__ == '__main__':
    try:
        sys.exit(main())
    except (OSError, ValueError, RuntimeError, json.JSONDecodeError) as error:
        print('maintenance failed: ' + str(error), file=sys.stderr)
        sys.exit(1)

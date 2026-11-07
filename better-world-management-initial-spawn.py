#!/usr/bin/env python3
"""Closed, generation-zero dedicated-world staging. Never touches an existing playable save.

Invoked only by better-world-management-server.sh under its exclusive supervisor lock.
The old server.properties bytes are restored even on an ordinary failure. A process-kill
leaves an active marker: recover the property backup and inspect the retained stages.
"""
from __future__ import annotations

import argparse
import base64
import json
import ctypes
import gzip
import math
import os
from pathlib import Path
import re
import secrets
import shutil
import struct
import subprocess
import sys
import time
import uuid
import zipfile

BUDGET = 600.0
RECEIPT = Path('data/better_world_management/initial-spawn-attempt-v2.tsv')
OUTCOME = Path('data/better_world_management/initial-spawn-outcome-v2.tsv')
STATUS = Path('data/better_world_management/initial-spawn-v1.tsv')
LOGIN = Path('data/better_world_management/initial-spawn-login-v1.tsv')
MAX_NBT = 16 * 1024 * 1024


def log(message: str) -> None:
    print(f'initial spawn supervisor: {message}', file=sys.stderr, flush=True)


def fail(message: str) -> None:
    raise RuntimeError(message)


def sync_directory(path: Path) -> None:
    descriptor = os.open(path, os.O_RDONLY | os.O_DIRECTORY)
    try:
        os.fsync(descriptor)
    finally:
        os.close(descriptor)


def rename_noreplace(source: Path, target: Path) -> None:
    """Linux renameat2: same-filesystem atomic promotion that cannot replace a world."""
    libc = ctypes.CDLL(None, use_errno=True)
    function = getattr(libc, 'renameat2', None)
    if function is None:
        fail('atomic no-replace rename is unavailable; candidate was not published')
    function.argtypes = (ctypes.c_int, ctypes.c_char_p, ctypes.c_int, ctypes.c_char_p, ctypes.c_uint)
    function.restype = ctypes.c_int
    if function(-100, os.fsencode(source), -100, os.fsencode(target), 1) != 0:
        code = ctypes.get_errno()
        raise OSError(code, os.strerror(code), str(target))


def atomic_bytes(path: Path, content: bytes) -> None:
    part = path.with_name(path.name + '.partial')
    descriptor = os.open(part, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    try:
        with os.fdopen(descriptor, 'wb') as output:
            output.write(content)
            output.flush()
            os.fsync(output.fileno())
        os.replace(part, path)
        sync_directory(path.parent)
    except BaseException:
        part.unlink(missing_ok=True)
        raise


def properties(raw: bytes) -> dict[str, str]:
    # Java server.properties accepts ordinary ISO-8859-1 property bytes; preserve raw bytes on restore.
    result: dict[str, str] = {}
    for line in raw.decode('latin-1').splitlines():
        if not line or line.lstrip().startswith(('#', '!')):
            continue
        # Java Properties also accepts ':' and whitespace separators. Never mistake an
        # alternate level-seed assignment for a blank random seed or silently rewrite it.
        if re.match(r'^\s*level(?:\\)?-seed(?:\s|:|=|$)', line) and not line.startswith('level-seed='):
            fail('ambiguous level-seed property syntax; normalize server.properties before staging')
        if '=' not in line:
            continue
        key, value = line.split('=', 1)
        if key in result:
            fail(f'duplicate server property: {key}')
        result[key] = value
    return result


def seed_mode(raw: str | None) -> str:
    return 'unknown' if raw is None else 'random' if not raw.strip() else 'explicit'


def staged_properties(original: bytes, stage: str, seed: str | None) -> bytes:
    values = properties(original)
    values.update({'level-name': stage, 'server-ip': '127.0.0.1',
                   'server-port': '0', 'enable-rcon': 'false', 'enable-query': 'false',
                   'enable-status': 'false'})
    if seed is not None:
        values['level-seed'] = seed
    # During a closed stage preserving comments is unnecessary. The exact original bytes are restored.
    return ''.join(f'{key}={value}\n' for key, value in values.items()).encode('latin-1')


def require_companion_admission_gate(root: Path) -> None:
    jars = list((root / 'mods').glob('better-world-management-*.jar'))
    if len(jars) != 1 or jars[0].is_symlink() or not jars[0].is_file():
        fail('expected exactly one regular World Management mod before closed staging')
    try:
        with zipfile.ZipFile(jars[0]) as compiled:
            names = set(compiled.namelist())
            needed = {
                'com/bettercontent/betterworldmanagement/InitialSpawnCreation.class',
                'com/bettercontent/betterworldmanagement/mixin/InitialSpawnLoginGateMixin.class',
                'com/bettercontent/betterworldmanagement/mixin/CreateWorldScreenMixin.class',
            }
            if not needed.issubset(names):
                fail('installed World Management JAR lacks the pre-entry admission gate; update it before creating a fresh world')
            mixins = json.loads(compiled.read('better_world_management.mixins.json'))
            if 'InitialSpawnLoginGateMixin' not in mixins.get('mixins', []) or not mixins.get('required', False):
                fail('installed World Management JAR does not require its pre-entry admission gate')
    except (zipfile.BadZipFile, KeyError, json.JSONDecodeError) as error:
        raise RuntimeError('installed World Management JAR cannot establish the pre-entry gate') from error


def new_seed(used: set[int]) -> int:
    for _ in range(100):
        value = int.from_bytes(secrets.token_bytes(8), 'big', signed=True)
        if value not in used:
            used.add(value)
            return value
    fail('could not obtain a distinct random seed')


class Nbt:
    def __init__(self, data: bytes):
        self.data = memoryview(data)
        self.index = 0

    def take(self, count: int) -> bytes:
        if count < 0 or self.index + count > len(self.data):
            fail('level.dat contains an invalid NBT length')
        result = bytes(self.data[self.index:self.index + count])
        self.index += count
        return result

    def number(self, fmt: str):
        return struct.unpack('>' + fmt, self.take(struct.calcsize(fmt)))[0]

    def text(self):
        size = self.number('H')
        return self.take(size).decode('utf-8')

    def payload(self, tag: int, depth: int = 0):
        if depth > 64:
            fail('level.dat NBT depth exceeded')
        primitive = {1: 'b', 2: 'h', 3: 'i', 4: 'q', 5: 'f', 6: 'd'}
        if tag in primitive:
            return self.number(primitive[tag])
        if tag == 8:
            return self.text()
        if tag in (7, 11, 12):
            length = self.number('i')
            size = {7: 1, 11: 4, 12: 8}[tag]
            self.take(length * size)
            return None
        if tag == 9:
            subtype = self.number('B')
            length = self.number('i')
            if length < 0 or length > MAX_NBT:
                fail('level.dat NBT list length exceeded')
            for _ in range(length):
                self.payload(subtype, depth + 1)
            return None
        if tag == 10:
            result = {}
            while True:
                subtype = self.number('B')
                if subtype == 0:
                    return result
                name = self.text()
                value = self.payload(subtype, depth + 1)
                if name in ('Data', 'SpawnX', 'SpawnY', 'SpawnZ', 'WorldGenSettings', 'seed'):
                    result[name] = value
        fail('level.dat contains an unsupported NBT tag')


def saved_level(path: Path):
    if path.is_symlink() or not path.is_file():
        fail('saved level.dat is missing or unsafe')
    with gzip.open(path, 'rb') as source:
        payload = source.read(MAX_NBT + 1)
    if len(payload) > MAX_NBT:
        fail('saved level.dat exceeds the NBT size bound')
    nbt = Nbt(payload)
    if nbt.number('B') != 10:
        fail('saved level.dat does not begin with a compound')
    nbt.text()
    data = nbt.payload(10).get('Data', {})
    generator = data.get('WorldGenSettings', {})
    if not isinstance(generator, dict):
        fail('saved level.dat lacks world generation settings')
    return data, generator


def ensure_uninhabited(world: Path) -> None:
    if world.is_symlink() or not world.is_dir():
        fail('candidate world is missing or a symlink')
    for candidate in (world / 'data', world / 'data/better_world_management',
                      world / LOGIN, world / RECEIPT, world / STATUS, world / OUTCOME):
        if candidate.is_symlink():
            fail('candidate contains an unsafe evidence symlink')
    if (world / LOGIN).exists():
        fail('candidate has a login marker; refusing to retry or publish')
    for name in ('playerdata', 'advancements', 'stats'):
        directory = world / name
        if directory.is_symlink():
            fail('candidate player directory is unsafe')
        if directory.is_dir() and any(directory.iterdir()):
            fail('candidate contains player data; refusing to retry or publish')


def receipt_info(world: Path, identity: str, target: str, mode: str, attempt: int,
                 *, legacy_v2: bool = False) -> str:
    # Keep the established filename for both schemas. A new candidate MUST have
    # the parametric V3 receipt; only an explicitly historical caller may read V2.
    file = world / RECEIPT
    if file.is_symlink() or not file.is_file():
        fail('candidate did not write a regular attempt receipt')
    lines = file.read_text(encoding='utf-8').splitlines()
    if not lines:
        fail('candidate receipt is missing its version')
    v2 = lines[0] == 'BC_INITIAL_SPAWN_ATTEMPT_V2'
    if v2 and not legacy_v2:
        fail('new candidate requires a V3 attempt receipt')
    if (v2 and len(lines) != 9) or (not v2 and
            (lines[0] != 'BC_INITIAL_SPAWN_ATTEMPT_V3' or len(lines) != 12)):
        fail('candidate receipt version or line count differs from the staged attempt')
    if lines[1:4] != [f'attempt\t{identity}', f'target\t{target}', f'number\t{attempt}']:
        fail('candidate receipt identity differs from the staged attempt')
    if not lines[4].startswith('seed\t') or not lines[5].startswith('status\t') \
            or lines[6] != f'provenance\t{mode}' or not lines[7].startswith('spawn\t') \
            or not lines[8].startswith('biome\t'):
        fail('candidate receipt is malformed')
    if mode not in ('random', 'explicit', 'unknown'):
        fail('candidate receipt has invalid seed provenance')
    coordinates = lines[7].split('\t')
    if len(coordinates) != 4 or coordinates[0] != 'spawn' \
            or any(not re.fullmatch(r'-?[0-9]+', value) for value in coordinates[1:]):
        fail('candidate receipt coordinates are malformed')
    if not re.fullmatch(r'-?[0-9]+', lines[4][5:]):
        fail('candidate receipt seed is malformed')
    seed = int(lines[4][5:])
    x, y, z = (int(value) for value in coordinates[1:])
    if seed < -(2 ** 63) or seed >= 2 ** 63:
        fail('candidate receipt seed is out of range')
    biome = lines[8][6:]
    if not re.fullmatch(r'[a-z0-9_.-]+:[a-z0-9_./-]+', biome):
        fail('candidate receipt biome is malformed')
    status = lines[5][7:]
    if v2:
        if status not in ('resolved', 'fallback') or status == 'fallback' and mode != 'explicit':
            fail('historical receipt requested an unsafe fallback')
    else:
        if status != 'resolved':
            fail('new candidate requires resolved V3 status')
        temperatures = []
        for field, line in zip(('base_temp_mc', 'min_temp_mc', 'max_temp_mc'), lines[9:]):
            parts = line.split('\t')
            if len(parts) != 2 or parts[0] != field or not re.fullmatch(
                    r'-?[0-9]+(?:\.[0-9]+)?(?:[eE][+-]?[0-9]+)?', parts[1]):
                fail(f'candidate receipt {field} is malformed')
            value = float(parts[1])
            if not math.isfinite(value):
                fail(f'candidate receipt {field} is not finite')
            temperatures.append(value)
        base, minimum, maximum = temperatures
        if not minimum <= base <= maximum:
            fail('candidate receipt temperature is outside its inclusive range')
    data, generator = saved_level(world / 'level.dat')
    if (data.get('SpawnX'), data.get('SpawnY'), data.get('SpawnZ')) != (x, y, z) \
            or generator.get('seed') != seed:
        fail('saved level.dat spawn or seed differs from candidate receipt')
    if (world / STATUS).read_text(encoding='utf-8').strip() != 'pending':
        fail('candidate reported publication before the supervisor verified the save')
    return status


def stopped(process: subprocess.Popen, timeout: float = 60.0) -> int:
    if process.poll() is None:
        try:
            process.stdin.write('stop\n')
            process.stdin.flush()
        except (BrokenPipeError, OSError):
            pass
    try:
        return process.wait(timeout=timeout)
    except subprocess.TimeoutExpired:
        log('staged server did not stop cleanly; terminating without publication')
        process.terminate()
        try:
            return process.wait(timeout=15)
        except subprocess.TimeoutExpired:
            process.kill()
            return process.wait(timeout=15)


def run_stage(root: Path, stage: Path, mode: str, target: str, identity: str,
              attempt: int, remaining_ms: int, universe: Path, forge_args: list[str],
              deadline: float | None) -> tuple[str, float]:
    env = os.environ.copy()
    env.update(BC_INITIAL_SPAWN_TARGET=target, BC_INITIAL_SPAWN_ATTEMPT=identity,
               BC_INITIAL_SPAWN_NUMBER=str(attempt), BC_INITIAL_SPAWN_PROVENANCE=mode,
               BC_INITIAL_SPAWN_REMAINING_MS=str(remaining_ms))
    command = [str(root / 'run-forge.sh'), *forge_args, '--universe', str(universe)]
    log(f'phase=launch attempt={attempt} elapsed={0 if deadline is None else int(BUDGET - deadline + time.monotonic())}s (loopback only)')
    process = subprocess.Popen(command, cwd=root, env=env, stdin=subprocess.PIPE,
                               stdout=sys.stdout, stderr=subprocess.STDOUT, text=True)
    status_file = stage / STATUS
    start_file = stage / 'data/better_world_management/initial-spawn-start-v2.tsv'
    receipt = stage / RECEIPT
    outcome = stage / OUTCOME
    try:
        began = time.monotonic()
        last = began - 10
        failed_since = None
        while process.poll() is None:
            if deadline is None and start_file.is_file():
                deadline = time.monotonic() + BUDGET
                log(f'phase=screen attempt={attempt} elapsed=0s budget={int(BUDGET)}s')
            now = time.monotonic()
            if receipt.is_file() or outcome.is_file():
                break
            if status_file.is_file() and status_file.read_text(encoding='utf-8').strip() == 'failed':
                failed_since = now if failed_since is None else failed_since
                if now - failed_since >= 2.0:
                    break  # Missing outcome is a technical error, not an automatic retry.
            if deadline is not None and now >= deadline:
                log(f'phase=deadline attempt={attempt} elapsed={int(BUDGET)}s; refusing unsafe publication')
                break
            if deadline is None and now - began > 900:
                fail('staged server did not reach its first seed probe in 15 minutes')
            if now - last >= 10:
                log(f'phase=waiting attempt={attempt} elapsed={0 if deadline is None else int(BUDGET - deadline + now)}s')
                last = now
            time.sleep(0.2)
        code = stopped(process)
        if code != 0:
            fail(f'staged server exited unsuccessfully ({code}); candidate retained')
        if deadline is not None and time.monotonic() >= deadline:
            fail('ten-minute candidate preparation/verification budget expired before clean shutdown')
        if receipt.is_file():
            return 'verified', deadline or time.monotonic() + BUDGET
        if outcome.is_file():
            lines = outcome.read_text(encoding='utf-8').splitlines()
            if len(lines) == 3 and lines[0] == 'BC_INITIAL_SPAWN_OUTCOME_V2' \
                    and lines[1] == 'kind\tretry' and lines[2].startswith('reason\t'):
                return 'retry', deadline or time.monotonic() + BUDGET
        fail('initial spawn search encountered a technical failure or its ten-minute deadline')
    finally:
        if process.poll() is None:
            stopped(process)


def run(root: Path, world: str, forge_args: list[str]) -> None:
    os.umask(0o077)
    if not re.fullmatch(r'[A-Za-z0-9._-]{1,128}', world) or world in ('.', '..'):
        fail('invalid final world name')
    if any(arg not in ('--nogui', 'nogui') for arg in forge_args):
        fail('fresh-world staging only permits --nogui; other flags could override its private save or loopback listener')
    final = root / world
    if final.exists() or final.is_symlink():
        fail('final world already exists; never replace an established save')
    state = root / '.better_world_management' / 'initial-spawn'
    if state.is_symlink():
        fail('unsafe initial-spawn transaction directory')
    state.mkdir(mode=0o700, parents=True, exist_ok=True)
    active = state / 'active-v2.tsv'
    if active.exists() or active.is_symlink():
        fail('a prior initial-spawn transaction is interrupted; inspect retained stages and restore server.properties before retrying')
    original_file = root / 'server.properties'
    if original_file.is_symlink():
        fail('server.properties is a symlink')
    original = original_file.read_bytes()
    values = properties(original)
    if values.get('level-name') != world:
        fail('server.properties world name changed before staging')
    seed_input = values.get('level-seed')
    mode = seed_mode(seed_input)
    if mode == 'unknown':
        log('seed provenance unavailable; keeping the existing seed, and no unsafe fallback is permitted')
    require_companion_admission_gate(root)
    run_id = uuid.uuid4().hex
    transaction = state / run_id
    transaction.mkdir(mode=0o700)
    backup = transaction / 'server.properties.before'
    atomic_bytes(backup, original)
    atomic_bytes(active, f'BC_INITIAL_SPAWN_ACTIVE_V2\nrun\t{run_id}\ntarget\t{world}\n'.encode())
    used: set[int] = set()
    deadline = None
    published = False
    try:
        for number in range(1, 10_000):
            if deadline is not None and time.monotonic() >= deadline:
                fail('10-minute initial spawn preparation deadline reached; failed candidates retained')
            stage_name = 'bc-initial-' + uuid.uuid4().hex
            universe = transaction / f'universe-{number}'
            universe.mkdir(mode=0o700)
            stage = universe / stage_name
            identity = f'{run_id}-{number}'
            attempt_seed = str(new_seed(used)) if mode == 'random' else seed_input
            encoded_input = base64.b64encode((attempt_seed or '').encode('latin-1')).decode('ascii') or '-'
            atomic_bytes(transaction / f'attempt-{number}-supervisor-v2.tsv', (
                'BC_INITIAL_SPAWN_SUPERVISOR_V2\n'
                f'attempt\t{identity}\ntarget\t{world}\nnumber\t{number}\n'
                f'provenance\t{mode}\nseed_input_base64\t{encoded_input}\n'
                f'stage\t{stage_name}\n').encode('ascii'))
            atomic_bytes(original_file, staged_properties(original, stage_name, attempt_seed))
            outcome, deadline = run_stage(root, stage, mode, world, identity, number,
                                          600_000 if deadline is None else max(1, int(1000 * (deadline - time.monotonic()))),
                                          universe, forge_args, deadline)
            ensure_uninhabited(stage)
            if outcome == 'retry':
                if mode != 'random' or time.monotonic() >= deadline:
                    fail('random initial-spawn attempts exhausted the ten-minute budget')
                rejected = transaction / f'rejected-{number}'
                os.replace(stage, rejected)
                sync_directory(transaction)
                log(f'phase=retry attempt={number} elapsed={int(BUDGET - deadline + time.monotonic())}s reason=no safe native site')
                continue
            status = receipt_info(stage, identity, world, mode, number)
            for durable in (stage / RECEIPT, stage / 'level.dat'):
                descriptor = os.open(durable, os.O_RDONLY | os.O_NOFOLLOW)
                try:
                    os.fsync(descriptor)
                finally:
                    os.close(descriptor)
            if final.exists() or final.is_symlink():
                fail('desired world appeared during staging; refusing to replace it')
            atomic_bytes(stage / STATUS, (status + '\n').encode())
            sync_directory(stage)
            rename_noreplace(stage, final)
            sync_directory(root)
            published = True
            log(f'phase=published attempt={number} elapsed={int(BUDGET - deadline + time.monotonic())}s; reopening verified save')
            break
    finally:
        # This is the only property restoration on ordinary exits. SIGKILL retains active-v2.tsv
        # and the protected backup; the supervisor refuses a new launch until manual recovery.
        atomic_bytes(original_file, original)
        if original_file.read_bytes() != original:
            fail('server.properties could not be restored byte for byte')
        if published:
            active.unlink()
            sync_directory(state)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, required=True)
    parser.add_argument('--world', required=True)
    parser.add_argument('forge_args', nargs=argparse.REMAINDER)
    args = parser.parse_args()
    forge_args = args.forge_args[1:] if args.forge_args[:1] == ['--'] else args.forge_args
    try:
        run(args.root.resolve(strict=True), args.world, forge_args)
        return 0
    except (RuntimeError, OSError, ValueError, KeyboardInterrupt, EOFError) as error:
        # Do not put protected stage paths or raw seed settings in the public console line.
        summary = 'filesystem error; inspect the protected transaction' if isinstance(error, OSError) else str(error)
        log(f'FAILED: {summary}. Inspect .better_world_management/initial-spawn/; no staged candidate was admitted.')
        return 1


if __name__ == '__main__':
    sys.exit(main())

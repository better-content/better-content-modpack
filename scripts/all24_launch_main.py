#!/usr/bin/env python3
"""One-shot, nonshipping Build22 all-24 source, target, or saved-state restart phase.

Run only with explicit SHA anchors. This controller never promotes a release marker.
Each phase has its own retained result; a failed phase cannot be resumed.
"""
import argparse
import calendar
import copy
from datetime import datetime, timezone
import fcntl
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import shlex
import signal
import socket
import subprocess
import sys
import time
import tomllib
import uuid
import zipfile

sys.dont_write_bytecode = True
ROOT = Path(__file__).absolute().parents[1]
RUNTIMES = ROOT / 'generated/world-axiom-runtimes'
OUT = 'all24-launch-v1'
SCHEMA = 'world_axioms_real_succession_launch_v1'
BOOTSTRAP_CODE_SHA = 'efd82147bc16945b8e6a14b71d6556299d94f2ff26427fc1791ac9205634fd6d'
PRODUCTION_SHA = 'b5e160ddc3654d24223f30fa187221abd009d278b175a857bfd93e030adf025f'
FIXTURE_SHA = '9f832f7c27622140a60c7a98c28250a85bdbc7a8670fd6c3fbce83750e11c8a7'
FIXTURE = ROOT.parent / 'mod_source/better-world-management/build/libs/better-world-management-0.1.0-native-fixtures.jar'
JAVA = Path('/home/dev/.sdkman/candidates/java/17.0.17-tem/bin/java')
CLASS = 'com.bettercontent.betterworldmanagement.worldshaping.AxiomSuccessionNativeTests'
TESTS = {'source': 'sourcestageall24andrequestrealreset',
         'target': 'successorall24nativestateclaimsandretry',
         'postclaim-restart': 'postclaimrestartpreservesreceiptsandentities'}
OPERATOR = '7ea34167-fd82-4aa1-aecc-1898dc998a24'
OWNER_KEY = 'WORLD_AXIOM_NATIVE_OWNER'


def need(ok, why):
    if not ok:
        raise ValueError(why)


def safe(path):
    path = Path(path).absolute()
    need('..' not in path.parts and not any(p.is_symlink() for p in (path, *path.parents)), 'Unsafe path: ' + str(path))
    return path


def sha(path):
    with safe(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def strict(raw):
    def pairs(rows):
        data = {}
        for k, v in rows:
            need(k not in data, 'Duplicate JSON key: ' + k)
            data[k] = v
        return data
    return json.loads(raw, object_pairs_hook=pairs,
                      parse_constant=lambda value: need(False, 'Nonfinite JSON: ' + value))


def read(path):
    path = safe(path)
    need(path.is_file() and path.stat().st_nlink == 1 and path.stat().st_size <= 32 * 1024**2,
         'Missing/oversized/hardlinked JSON: ' + str(path))
    return strict(path.read_bytes())


def write(path, data):
    path = safe(path)
    raw = (json.dumps(data, sort_keys=True, indent=2, allow_nan=False) + '\n').encode()
    with path.open('xb') as stream:
        stream.write(raw); stream.flush(); os.fsync(stream.fileno())
    fd = os.open(path.parent, os.O_RDONLY | os.O_DIRECTORY)
    try: os.fsync(fd)
    finally: os.close(fd)


def snapshot(paths):
    return {str(safe(p)): sha(p) for p in paths}


def files(root):
    result = {}
    for p in root.rglob('*'):
        p = safe(p)
        need(p.stat().st_uid == os.getuid(), 'Foreign-owned runtime entry: ' + str(p))
        need(p.is_dir() or (p.is_file() and p.stat().st_nlink == 1), 'Nonregular runtime entry: ' + str(p))
        if p.is_file(): result[p.relative_to(root).as_posix()] = p
    return result


def contract(path, magic):
    path = safe(path)
    need(path.is_file() and path.stat().st_size <= 65536, 'Missing native contract: ' + str(path))
    raw = path.read_text()
    need(raw.endswith('\n') and '\r' not in raw, 'Noncanonical native contract')
    lines = raw.splitlines()
    need(lines.pop(0) == magic, 'Wrong native contract magic')
    values = {}
    for line in lines:
        parts = line.split('\t')
        need(len(parts) == 2 and parts[0] not in values, 'Malformed native contract')
        values[parts[0]] = parts[1]
    return values


TOKEN_START = {}
TOKEN_SEEN_PIDS = {}
TOKEN_EXTERNAL_ZOMBIES = {}
CLOCK_TICKS = os.sysconf('SC_CLK_TCK')


def mark_token(token, started_at=None, mint_tick=None):
    """Bind an exact /proc uptime tick; never derive it from UTC wall time."""
    if not isinstance(token, str) or not token:
        raise ValueError('Owner token required')
    now_tick = int(float(Path('/proc/uptime').read_text().split()[0]) * CLOCK_TICKS)
    if started_at is None:
        if mint_tick is not None: raise ValueError('New owner cannot borrow prior mint tick')
        ticks = now_tick
    else:
        parsed = datetime.fromisoformat(started_at)
        if parsed.tzinfo is None:
            raise ValueError('Earlier owner timestamp must have a timezone')
        if type(mint_tick) is not int or not (0 < mint_tick <= now_tick):
            raise ValueError('Exact prior owner monotonic mint tick required')
        ticks = mint_tick
    if token in TOKEN_START and TOKEN_START[token] != ticks:
        raise ValueError('Owner token mint time cannot be rebound')
    TOKEN_START[token] = ticks
    TOKEN_SEEN_PIDS.setdefault(token, {})
    TOKEN_EXTERNAL_ZOMBIES.setdefault(token, {})


def process_start_ticks(proc):
    text = (proc / 'stat').read_text()
    fields = text[text.rfind(')') + 1:].split()
    if len(fields) <= 19:
        raise RuntimeError('UNKNOWN: malformed proc start identity: ' + proc.name)
    return int(fields[19])


def protected_process_identity(proc, status, token):
    """Bounded read-only provenance for a refused census; never expose environ."""
    evidence = {'pid': int(proc.name), 'mint_tick': TOKEN_START[token]}
    for line in status.splitlines():
        if line.startswith(('Name:', 'State:', 'PPid:', 'Uid:')):
            key, value = line.split(':', 1)
            evidence[key.lower()] = value.strip()[:160]
    for label, path in (('process', proc), ('parent', Path('/proc') / evidence.get('ppid', '0'))):
        row = {}
        for key, operation in (
            ('start_tick', lambda: process_start_ticks(path)),
            ('command0', lambda: (path / 'cmdline').read_bytes().split(b'\0', 1)[0].decode(errors='replace')[:256]),
            ('executable', lambda: os.readlink(path / 'exe')[:256]),
            ('cgroup', lambda: (path / 'cgroup').read_text(errors='replace')[:256]),
        ):
            try:
                row[key] = operation()
            except OSError as error:
                row[key + '_unavailable'] = type(error).__name__
        evidence[label] = row
    return evidence


def external_zombie_parent_proof(proc, status, token, start):
    """Only prove a never-owned zombie came from an older untagged live parent.

    A process that was ever observed with this owner tag is NEVER exempted.
    Any unreadable/replaced parent, missing status, or live inaccessible child
    remains UNKNOWN. This is ancestry evidence, not a PID/name/cgroup allowlist.
    """
    pid = int(proc.name)
    if pid in TOKEN_SEEN_PIDS[token]:
        return None
    fields = dict((key, value.strip()) for line in status.splitlines()
                  if ':' in line for key, value in [line.split(':', 1)])
    if not fields.get('State', '').startswith('Z ') or not fields.get('PPid', '').isdigit():
        return None
    parent = Path('/proc') / fields['PPid']
    try:
        parent_start = process_start_ticks(parent)
        if parent_start >= TOKEN_START[token]:
            return None
        parent_status = (parent / 'status').read_text()
        pfields = dict((key, value.strip()) for line in parent_status.splitlines()
                       if ':' in line for key, value in [line.split(':', 1)])
        if pfields.get('Uid', '').split()[:1] != [str(os.getuid())] \
                or pfields.get('State', '').startswith('Z '):
            return None
        parent_env = (parent / 'environ').read_bytes()
        if any(part.startswith(OWNER_KEY.encode() + b'=') for part in parent_env.split(b'\0')):
            return None
        # Verify both PID identities and the child's zombie/parent/UID facts
        # again AFTER reading the parent's environment, before exclusion.
        if process_start_ticks(parent) != parent_start or process_start_ticks(proc) != start:
            return None
        child_status = (proc / 'status').read_text()
        if not all(line in child_status.splitlines() for line in status.splitlines()
                   if line.startswith(('PPid:', 'Uid:', 'State:'))):
            return None
    except (OSError, ValueError, RuntimeError):
        return None
    return {'pid': pid, 'start_tick': start, 'parent_pid': int(fields['PPid']),
            'parent_start_tick': parent_start, 'parent_environ_sha256': hashlib.sha256(parent_env).hexdigest(),
            'proof': 'zombie_never_seen_owned_pre_token_live_same_uid_parent_without_owner_key'}


def owned(token):
    if token not in TOKEN_START:
        raise RuntimeError('UNKNOWN: owner token mint time not recorded')
    marker = (OWNER_KEY + '=' + token).encode() + b'\0'
    result = {}
    for proc in Path('/proc').iterdir():
        if not proc.name.isdecimal():
            continue
        try:
            status = (proc / 'status').read_text()
        except (FileNotFoundError, ProcessLookupError):
            continue
        except OSError as error:
            if proc.exists():
                raise RuntimeError('UNKNOWN: stable proc status not readable: ' + proc.name) from error
            continue
        uids = [line for line in status.splitlines() if line.startswith('Uid:')]
        if len(uids) != 1 or len(uids[0].split()) < 2:
            raise RuntimeError('UNKNOWN: malformed process owner status: ' + proc.name)
        if int(uids[0].split()[1]) != os.getuid():
            continue
        try:
            environ = (proc / 'environ').read_bytes()
        except (FileNotFoundError, ProcessLookupError):
            continue
        except OSError as error:
            if not proc.exists():
                continue
            try:
                first_start = process_start_ticks(proc)
            except (FileNotFoundError, ProcessLookupError):
                continue  # Exited before an identity could be anchored.
            if first_start < TOKEN_START[token]:
                continue  # Cannot inherit a future random token at exec.
            state = [line for line in status.splitlines() if line.startswith('State:')]
            if len(state) == 1 and state[0].split()[1] == 'Z' \
                    and TOKEN_SEEN_PIDS[token].get(int(proc.name)) == first_start:
                result[int(proc.name)] = ['OWNED_ZOMBIE_AWAIT_REAP']
                continue  # Still counted as owned, never a clean census.
            # A just-forked process may still be between /proc/status creation
            # and exec/environment publication. Retry only the SAME anchored
            # PID/start for at most 100ms; stable unreadability remains UNKNOWN.
            environ = None
            still_same = True
            for delay in (.005, .015, .03, .05):
                time.sleep(delay)
                try:
                    current_start = process_start_ticks(proc)
                except (FileNotFoundError, ProcessLookupError):
                    still_same = False
                    break
                if current_start != first_start:
                    still_same = False
                    break
                try:
                    environ = (proc / 'environ').read_bytes()
                    break
                except (FileNotFoundError, ProcessLookupError):
                    still_same = False
                    break
                except OSError:
                    pass
            if not still_same:
                continue  # Exited or PID reused; a different identity is not exempted.
            if environ is not None:
                pass  # Continue through ordinary marker/command checks below.
            else:
                proof = external_zombie_parent_proof(proc, status, token, first_start)
                if proof is not None:
                    TOKEN_EXTERNAL_ZOMBIES[token][int(proc.name)] = proof
                    continue
                identity = protected_process_identity(proc, status, token)
                raise RuntimeError('UNKNOWN: post-token same-UID proc environment not readable: '
                                   + json.dumps(identity, sort_keys=True)) from error
        seen_start = TOKEN_SEEN_PIDS[token].get(int(proc.name))
        if marker not in environ:
            if seen_start is None:
                continue
            try:
                same_process = seen_start == process_start_ticks(proc)
            except (FileNotFoundError, ProcessLookupError):
                continue
            if same_process:
                state = [line for line in status.splitlines() if line.startswith('State:')]
                if len(state) == 1 and state[0].split()[1] == 'Z':
                    result[int(proc.name)] = ['OWNED_ZOMBIE_AWAIT_REAP']
                    continue
                raise RuntimeError('UNKNOWN: previously owned process lost its marker: ' + proc.name)
            continue  # Reused PID does not inherit the earlier process identity.
        try:
            TOKEN_SEEN_PIDS[token][int(proc.name)] = process_start_ticks(proc)
            command = (proc / 'cmdline').read_bytes().decode(errors='replace').split('\0')[:-1]
        except (FileNotFoundError, ProcessLookupError):
            continue
        except OSError as error:
            if proc.exists():
                raise RuntimeError('UNKNOWN: owned proc command not readable: ' + proc.name) from error
            continue
        if not command and proc.exists():
            # An exited, unreaped owned process has no useful command. Do not
            # claim a clean census until its parent reaps it or disappears.
            result[int(proc.name)] = ['UNKNOWN: empty owned command']
        else:
            result[int(proc.name)] = command
    return result


def cleanup(token, leader=None):
    """Signal only previously and freshly owner-identified PIDFDs; fail UNKNOWN."""
    for signum, seconds in ((signal.SIGTERM, 4), (signal.SIGKILL, 2)):
        if leader is not None:
            leader.poll()  # Reap our direct child before interpreting empty zombie environ.
        for pid in owned(token):
            try:
                fd = os.pidfd_open(pid, 0)
            except ProcessLookupError:
                continue
            except OSError as error:
                raise RuntimeError('UNKNOWN: pidfd unavailable for owned process: ' + str(pid)) from error
            try:
                if pid in owned(token):
                    try:
                        signal.pidfd_send_signal(fd, signum, None, 0)
                    except ProcessLookupError:
                        pass
            finally:
                os.close(fd)
        until = time.monotonic() + seconds
        while time.monotonic() < until:
            if leader is not None:
                leader.poll()
            current = owned(token)
            if not current:
                return {}
            time.sleep(.1)
    if leader is not None:
        leader.poll()
    return owned(token)


def cleanup_known_tagged_after_unknown(token):
    """Best-effort exact-owner kill after an UNKNOWN census, never a clean result.

    Unlike cleanup(), this intentionally ignores unreadable *other* processes
    only while gathering verifiably tagged or previously seen PID/start pairs.
    The caller retains UNKNOWN and FAIL even if all known children disappear.
    """
    known = dict(TOKEN_SEEN_PIDS[token])
    for proc in Path('/proc').iterdir():
        if not proc.name.isdigit():
            continue
        try:
            env = (proc / 'environ').read_bytes().split(b'\0')
            if OWNER_KEY.encode() + b'=' + token.encode() in env:
                known[int(proc.name)] = process_start_ticks(proc)
        except (FileNotFoundError, ProcessLookupError, PermissionError):
            continue  # UNKNOWN is retained by the original census failure.
    signaled = []
    for signum in (signal.SIGTERM, signal.SIGKILL):
        for pid, start in known.items():
            proc = Path('/proc') / str(pid)
            try:
                if process_start_ticks(proc) != start:
                    continue
                fd = os.pidfd_open(pid, 0)
                try:
                    if process_start_ticks(proc) == start:
                        signal.pidfd_send_signal(fd, signum, None, 0)
                        signaled.append({'pid': pid, 'start_tick': start, 'signal': signum.name})
                finally:
                    os.close(fd)
            except (FileNotFoundError, ProcessLookupError):
                continue
        deadline = time.monotonic() + (4 if signum == signal.SIGTERM else 2)
        while time.monotonic() < deadline:
            if all(not (Path('/proc') / str(pid)).exists() for pid in known):
                break
            time.sleep(.1)
    return {'exact_owner_identities': [{'pid': pid, 'start_tick': start}
                                       for pid, start in sorted(known.items())],
            'pidfd_signals': signaled, 'clean_census_claimed': False}


def forge_command(command):
    return isinstance(command, list) and any('@user_jvm_args.txt' in part for part in command)



def native_pass(result, exact):
    need(result.get('schema') == 'world_axioms_native_fixtures_v1' and result.get('status') == 'PASS'
         and result.get('actual_dedicated') is True and result.get('world_saved') is True
         and result.get('forge_gametest_enabled') is False and result.get('save_mode') == 'normal_shutdown'
         and result.get('classes') == [CLASS] and not result.get('framework_error') and not result.get('save_error'),
         'No actual dedicated normal-save native PASS')
    rows = result.get('cases')
    need(isinstance(rows, list) and len(rows) == 1 and rows[0].get('name') == exact
         and rows[0].get('required') is True and rows[0].get('completed') is True
         and rows[0].get('passed') is True and rows[0].get('failed') is False and not rows[0].get('error')
         and result.get('expected') == result.get('completed') == result.get('required') == 1
         and result.get('failed') == 0 and type(result.get('process_id')) is int,
         'Wrong, incomplete, or failed native case')
    datetime.fromisoformat(result['process_started_at'])
    datetime.fromisoformat(result['last_case_completed_at'])


def instant_ns(value):
    need(isinstance(value, str), 'Native instant must be text')
    match = re.fullmatch(r'(\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d)(?:\.(\d{1,9}))?(?:Z|\+00:00)', value)
    need(match is not None, 'Native instant must be exact UTC')
    second = datetime.strptime(match[1], '%Y-%m-%dT%H:%M:%S')
    return calendar.timegm(second.timetuple()) * 10**9 + int((match[2] or '').ljust(9, '0'))


def phase_results(runtime, prior, phase):
    paths = set((runtime / 'axiom-native-results').glob('*/results.json')) - set(prior)
    need(paths, 'No fresh native result')
    results = {}
    for path in paths:
        data = read(path)
        native_pass(data, TESTS[phase])
        need(path.parent.name == str(uuid.UUID(data['campaign'])), 'Native campaign path mismatch')
        results[str(path)] = {'sha256': sha(path), 'evidence': data}
    need(len(results) == 1 or phase == 'source', 'Expected exactly one fresh native result')
    return results


def bundle(production, fixture, out):
    """Copy production bytes, append one exact nonshipping fixture module, retain original."""
    need(sha(production) == PRODUCTION_SHA and sha(fixture) == FIXTURE_SHA, 'Production/fixture SHA changed')
    original = out / 'original-production.jar'
    with original.open('xb') as f:
        f.write(production.read_bytes()); f.flush(); os.fsync(f.fileno())
    need(sha(original) == PRODUCTION_SHA, 'Frozen production copy changed')
    partial = out / 'native-bundle.partial.jar'
    with zipfile.ZipFile(original) as main, zipfile.ZipFile(fixture) as tests:
        for z in (main, tests):
            names = z.namelist()
            need(len(names) == len(set(names)) and not any(re.fullmatch(r'META-INF/[^/]+\.(?:SF|RSA|DSA)', n, re.I) for n in names),
                 'Duplicate or signed JAR entry')
        a, b = set(main.namelist()), set(tests.namelist())
        collision = {n for n in a & b if not (main.getinfo(n).is_dir() and tests.getinfo(n).is_dir())}
        allowed = {'META-INF/MANIFEST.MF', 'META-INF/mods.toml', 'pack.mcmeta'}
        refmap = 'better_world_management.refmap.json'
        if refmap in collision:
            need(main.read(refmap) == tests.read(refmap), 'Refmap collision differs')
            allowed.add(refmap)
        need(collision <= allowed, 'Fixture overrides production class/resource')
        raw_main, raw_test = main.read('META-INF/mods.toml'), tests.read('META-INF/mods.toml')
        m, t = tomllib.loads(raw_main.decode()), tomllib.loads(raw_test.decode())
        namespace = 'better_world_management_feasibility'
        need(set(t) == {'modLoader', 'loaderVersion', 'license', 'mods', 'dependencies'}
             and all(t[k] == m[k] for k in ('modLoader', 'loaderVersion', 'license'))
             and [x['modId'] for x in m['mods']] == ['better_world_management']
             and [x['modId'] for x in t['mods']] == [namespace]
             and set(t['dependencies']) == {namespace}, 'Unexpected fixture namespace or metadata')
        match = re.search(rb'(?m)^\[\[mods\]\]\s*$', raw_test)
        need(match is not None, 'Fixture module table missing')
        appended = raw_main + b'\n# Nonshipping native fixture append; production metadata above unchanged.\n' + raw_test[match.start():]
        need(tomllib.loads(appended.decode()) == m | {'mods': m['mods'] + t['mods'],
             'dependencies': m.get('dependencies', {}) | t['dependencies']}, 'Metadata append changed semantics')
        need(CLASS.replace('.', '/') + '.class' in b, 'Exact all24 fixture class missing')
        with zipfile.ZipFile(partial, 'x') as dest:
            for info in main.infolist(): dest.writestr(copy.copy(info), appended if info.filename == 'META-INF/mods.toml' else main.read(info))
            for info in tests.infolist():
                if info.filename not in a: dest.writestr(copy.copy(info), tests.read(info))
        with zipfile.ZipFile(partial) as result:
            need(set(result.namelist()) == a | b, 'Bundle inventory changed')
            need(all(result.read(n) == main.read(n) for n in a - {'META-INF/mods.toml'}), 'Production entry changed')
            need(all(result.read(n) == tests.read(n) for n in b - a), 'Fixture entry changed')
            need(result.read('META-INF/mods.toml') == appended, 'Metadata append changed')
        receipt = {'schema': 'world_axioms_nonshipping_native_bundle_v1', 'production_runtime_path': str(production),
                   'original_production_sha256': PRODUCTION_SHA, 'fixture_sha256': FIXTURE_SHA,
                   'bundle_sha256': sha(partial), 'original_copy': str(original),
                   'production_entry_sha256_before': {n: hashlib.sha256(main.read(n)).hexdigest() for n in a},
                   'production_metadata_append_only': True, 'all_other_production_entries_byte_identical': True,
                   'fixture_module': namespace, 'permitted_resource_collisions': sorted(collision),
                   'shipping_artifact_modified': False}
    need(sha(production) == PRODUCTION_SHA, 'Runtime production JAR changed before bundle')
    os.replace(partial, production)
    write(out / 'native-bundle.json', receipt)
    need(sha(production) == receipt['bundle_sha256'], 'Bundle installation failed')
    return receipt


def policy(runtime, out):
    path = runtime / 'kubejs/config/crafting_policy.json'
    original = read(path)
    ns = 'better_world_management_feasibility'
    need(ns not in original.get('namespaces', {}) and original.get('enforcement', {}).get('unknown_loaded_namespaces') == 'blocking',
         'Fixture namespace already classified or unknown namespaces not blocked')
    (out / 'original-native-crafting-policy.json').write_bytes(path.read_bytes())
    updated = strict(path.read_bytes())
    classification = {'primary_role': 'infrastructure', 'support_state': 'not_applicable'}
    updated['namespaces'][ns] = classification
    with path.open('w') as f:
        json.dump(updated, f, sort_keys=True, indent=2); f.write('\n'); f.flush(); os.fsync(f.fileno())
    check = read(path)
    need(check['namespaces'].pop(ns) == classification and check == original, 'Native policy changed other values')
    receipt = {'schema': 'world_axioms_nonshipping_native_policy_v1', 'policy_path': str(path),
               'policy_sha256': sha(path), 'original_copy': str(out / 'original-native-crafting-policy.json'),
               'original_sha256': sha(out / 'original-native-crafting-policy.json'),
               'fixture_namespace': ns, 'classification': classification,
               'unknown_loaded_namespaces': 'blocking', 'existing_policy_values_unchanged': True}
    write(out / 'native-policy.json', receipt)
    return receipt

def config_module():
    """The same-pair config verifier is nonshipping but frozen per phase."""
    path = ROOT / 'scripts/all24_config_attestation_main.py'
    spec = importlib.util.spec_from_file_location('all23_build26_config_attestation', path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def bootstrap_module():
    path = ROOT / 'scripts/all24_bootstrap_main.py'
    need(sha(path) == BOOTSTRAP_CODE_SHA, 'Bootstrap helper changed')
    spec = importlib.util.spec_from_file_location('all24_main_bootstrap_pinned', path)
    module = importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
    need(module.LINEAGE == 'lineage-all23-main24', 'Unexpected public bootstrap lineage')
    return module


def runtime_identity(runtime, material_sha, bootstrap_sha, fresh_nonce):
    need(runtime.parent == RUNTIMES and re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._-]{0,127}', runtime.name),
         'Runtime must be a direct main-pack nonce')
    need(fresh_nonce == runtime.name and re.fullmatch('[0-9a-f]{64}', material_sha or '')
         and re.fullmatch('[0-9a-f]{64}', bootstrap_sha or ''),
         'Explicit fresh nonce and material/bootstrap SHA anchors required')


def validate_source(runtime, material_sha=None, bootstrap_sha=None, fresh_nonce=None):
    runtime_identity(runtime, material_sha, bootstrap_sha, fresh_nonce)
    need(runtime.is_dir() and runtime.stat().st_uid == os.getuid(), 'Unowned runtime')
    need(sha(runtime / 'runtime-inputs-v1.json') == material_sha, 'Materialization SHA changed')
    need(sha(runtime / 'all24-source-bootstrap-v1/result.json') == bootstrap_sha, 'Bootstrap SHA changed')
    module = bootstrap_module()
    module.validate(runtime, material_sha, bootstrap_sha)
    receipt = read(runtime / 'runtime-inputs-v1.json')
    need(receipt.get('pinned_release', {}).get('source_build_sha256', {}).get('better-world-management') == PRODUCTION_SHA,
         'Production JAR source/build pin changed')
    production = runtime / 'mods/better-world-management-0.1.0.jar'
    need(sha(production) == PRODUCTION_SHA and sha(FIXTURE) == FIXTURE_SHA, 'Production/fixture changed')
    with zipfile.ZipFile(FIXTURE) as z:
        need(CLASS.replace('.', '/') + '.class' in z.namelist(), 'Exact main fixture class absent')
    need(not (runtime / OUT).exists(), 'Launcher was already attempted')
    return receipt


def validate_pins(runtime, material_sha, bootstrap_sha, fresh_nonce):
    runtime_identity(runtime, material_sha, bootstrap_sha, fresh_nonce)
    bootstrap = bootstrap_module()
    need(sha(runtime / 'runtime-inputs-v1.json') == material_sha
         and sha(runtime / 'all24-source-bootstrap-v1/result.json') == bootstrap_sha
         and sha(FIXTURE) == FIXTURE_SHA, 'Exact runtime/bootstrap/fixture pins changed')
    material = read(runtime / 'runtime-inputs-v1.json')
    need(material.get('schema') == 'world_axioms_main_build26_materialization_v1'
         and material.get('status') == 'materialized_unaccepted'
         and material.get('destination') == str(runtime)
         and material.get('accepted_zip_expected_sha256') ==
             'c18aebc94e5b5d14860c5ef72725f65003d6f313c37295f7833216ec40d0c6fa'
         and material.get('client_zip_expected_sha256') ==
             'e6bad4c0e1598dfd917e8494d3640a18b11d8268e7dcf26687c7743596145af9',
         'Materialization is not exact main Build26')
    need(material.get('pinned_release') == bootstrap.parent.materializer().admit(),
         'Live ready/Smoke/49-mod/source-build admission changed')
    boot_receipt = read(runtime / 'all24-source-bootstrap-v1/result.json')
    need(boot_receipt.get('schema') == 'world_axiom_all24_source_bootstrap_v1'
         and boot_receipt.get('status') == 'BOOTSTRAPPED_SYNTHETIC_PARENT_NOT_NATIVE_ACCEPTANCE'
         and boot_receipt.get('runtime') == str(runtime)
         and boot_receipt.get('materialization_sha256') == material_sha
         and boot_receipt.get('bootstrap_helper_sha256') == BOOTSTRAP_CODE_SHA
         and boot_receipt.get('actual_resets_performed') == 0
         and boot_receipt.get('native_acceptance') is False,
         'Bootstrap origin/identity changed')
    need(sha(ROOT / 'dist/build-26/server/better-content.zip') == material['accepted_zip_expected_sha256']
         and sha(ROOT / 'dist/build-26/client/better-content.zip') == material['client_zip_expected_sha256'],
         'Exact accepted Build26 ZIP pair drifted')
    return material


def lineage(runtime, generation):
    row = contract(runtime / '.better_world_management/lineage-v5.tsv', 'BC_PRESTIGE_LINEAGE_V5')
    need(row.get('generation') == row.get('total_prestiges') == str(generation)
         and row.get('lineage') == bootstrap_module().LINEAGE,
         'Wrong native lineage')
    boot_receipt = read(runtime / 'all24-source-bootstrap-v1/result.json')
    need(boot_receipt.get('generation') == boot_receipt.get('total_prestiges') == 23
         and boot_receipt.get('runtime') == str(runtime), 'Native lineage lost bootstrap origin')
    return row['lineage']


def fixture(runtime, line):
    value = read(runtime / 'world-axiom-succession-fixture-v1.json')
    need(value.get('schema') == 'world_axiom_real_succession_fixture_v1'
         and value.get('initialization') == 'SYNTHETIC_PARENT23_NOT_23_REAL_RESETS'
         and value.get('source_phase') == 'committed' and value.get('base_generation') == 23
         and value.get('lineage') == line and value.get('operator') == OPERATOR
         and re.fullmatch('[a-f0-9]{64}', value.get('build_sha256', '')),
         'Missing committed real source fixture')
    return value


def health_preferences(health, reset, successor, f):
    choices = ('minecraft:plains', 'minecraft:forest', 'minecraft:meadow')
    need([reset.get('biome_' + str(n)) for n in (1, 2, 3)] == list(choices)
         and [successor.get('biome_' + str(n)) for n in (1, 2, 3)] == list(choices)
         and [health.get('requested_biome_' + str(n)) for n in (1, 2, 3)] == list(choices),
         'Health biome preferences differ from frozen source request')
    resolved = health.get('resolved_biome')
    need(resolved in choices and health.get('actual_biome') == resolved,
         'Health did not settle in an actually requested biome')
    if f.get('natural_selected_biome') is not None:
        need(f['natural_selected_biome'] == resolved
             and type(f.get('natural_selected_spawn')) is int,
             'Native selected-biome arrival proof differs from health result')


def promotion(runtime, line):
    need(lineage(runtime, 24) == line, 'Real successor lineage mismatch')
    f = fixture(runtime, line)
    need(f.get('source_process') and f.get('source_seed') is not None,
         'Missing real source process/world identity')
    if f.get('target_seed') is not None:
        need(str(f['source_seed']) != str(f['target_seed']), 'Target reused source world seed')
    tx = f.get('transaction')
    need(isinstance(tx, str) and re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._-]{0,127}', tx), 'Unsafe transaction')
    state = runtime / '.better_world_management'
    tr = state / 'transactions' / tx
    phase = contract(tr / 'phase-v2.tsv', 'BC_PRESTIGE_TRANSACTION_PHASE_V2')
    need(phase == {'transaction': tx, 'phase': 'lineage-committed'}, 'Supervisor did not commit lineage')
    reset = contract(tr / 'reset-request-v5.tsv', 'BC_PRESTIGE_RESET_V5')
    successor = contract(tr / 'successor-request-v5.tsv', 'BC_PRESTIGE_SUCCESSOR_V5')
    need(reset.get('state') == 'committed' and reset.get('lineage') == line
         and reset.get('transaction') == tx and reset.get('base_generation') == '23'
         and reset.get('world') == 'world' and reset.get('old_seed') == str(f['source_seed'])
         and reset.get('seed_mode') == 'random', 'Frozen source reset request mismatch')
    need(successor.get('lineage') == line and successor.get('transaction') == tx
         and successor.get('attempt') == '1' and successor.get('base_generation') == '23'
         and successor.get('target_generation') == '24', 'Frozen first successor request mismatch')
    health = contract(tr / 'health-result-v5.tsv', 'BC_PRESTIGE_HEALTH_V5')
    expected_health = {'lineage': line, 'base_generation': '23', 'target_generation': '24',
                       'transaction': tx, 'attempt': '1', 'world': 'world', 'status': 'healthy',
                       'fresh_players': 'true', 'level_dat': 'true',
                       }
    need(len(health) == 16 and all(health.get(k) == v for k, v in expected_health.items())
         and health.get('successor_seed') == health.get('actual_seed')
         and health.get('actual_seed') != str(f['source_seed'])
         and health.get('successor_seed') == successor.get('successor_seed'),
         'No first-attempt healthy real successor')
    health_preferences(health, reset, successor, f)
    perk = contract(tr / 'perk-health-v3.tsv', 'BC_PRESTIGE_PERK_HEALTH_V3')
    need(len(perk) == 9 and all(perk.get(k) == v for k, v in expected_health.items()
                                if k in {'lineage', 'base_generation', 'target_generation',
                                         'transaction', 'attempt', 'resolved_biome'}),
         'No matching native perk health')
    axiom = read(tr / 'world-axiom-health-v1.json')
    need(all(axiom.get(k) == v for k, v in {'schema': 'world_axiom_health_v1', 'status': 'healthy',
         'lineage': line, 'transaction': tx, 'generation': 24, 'build_sha256': f['build_sha256']}.items()),
         'No native all23 policy health')
    build = read(runtime / 'world/data/better_world_management/world-axioms-v1.json')
    need(build.get('lineage') == line and build.get('transaction') == tx
         and build.get('base_generation') == 23 and build.get('sha256') == f['build_sha256']
         and isinstance(build.get('selected'), list) and len(build['selected']) == len(set(build['selected'])) == 23,
         'Promoted active all23 build mismatch' )
    build_bytes = (runtime / 'world/data/better_world_management/world-axioms-v1.json').read_bytes()
    need(all(p.read_bytes() == build_bytes for p in (state / 'world-axioms-v1.json', tr / 'world-axiom-reset-v1.json')),
         'Frozen source/target snapshot bytes mismatch')
    projection = read(runtime / 'kubejs/config/world_axioms_runtime.json')
    need(projection.get('transaction') == tx and projection.get('generation') == 24
         and projection.get('build_sha256') == f['build_sha256'] and projection.get('selected') == build['selected'],
         'Real successor projection mismatch')
    archives = list((state / 'archives').glob('*-' + tx + '.zip'))
    need(len(archives) == 1, 'No unique actual source archive')
    archive = safe(archives[0]); digest = sha(archive)
    need(Path(str(archive) + '.sha256').read_text() == digest + '  ' + archive.name + '\n', 'Archive sidecar mismatch')
    with zipfile.ZipFile(archive) as z:
        need({'world/level.dat', 'better-world-management-archive-manifest-v1.tsv'} <= set(z.namelist()),
             'Source archive omits native world/manifest')
    control = state / 'control'
    for p in ('*reset*', '*successor-request*', '*staged*', '*shutdown-request*'):
        need(not list(control.glob(p)), 'Production supervisor left pending control: ' + p)
    return {'lineage': line, 'transaction': tx, 'fixture': f, 'archive': str(archive),
            'archive_sha256': digest, 'build_sha256': f['build_sha256'], 'health': health}


def target_disk(runtime, proof):
    f = proof['fixture']
    need(f.get('target_phase') == 'claims_and_disk_proof_pass'
         and f.get('target_process') and f['target_process'] != f['source_process'], 'No distinct target claims process')
    need(f.get('natural_selected_biome') == proof['health']['resolved_biome']
         and type(f.get('natural_selected_spawn')) is int,
         'Native selected-biome arrival proof absent or differs from health')
    journal_path = runtime / 'world/data/better_world_management/world-axiom-journal-v1.json'
    journal = read(journal_path)
    need(journal.get('transaction') == proof['transaction'] and journal.get('build_sha256') == proof['build_sha256'],
         'Claim journal transaction changed')
    for name in ('seed_covenant', 'patient_hive', 'ark', 'familiars_name'):
        row = journal.get('receipts', {}).get(name, {})
        need(row.get('state') == 'released' and row.get('actor') == OPERATOR, 'Missing actual released claim: ' + name)
    player = runtime / 'world/playerdata' / (OPERATOR + '.dat')
    level = runtime / 'world/level.dat'
    need(player.read_bytes()[:2] == b'\x1f\x8b' and level.read_bytes()[:2] == b'\x1f\x8b', 'Native player/world NBT absent')
    entities = list((runtime / 'world/entities').glob('*.mca'))
    need(entities and all(p.stat().st_size >= 8192 for p in entities), 'Native entity region absent')
    return {'journal_sha256': sha(journal_path), 'native_nbt_sha256': snapshot((player, level)),
            'entity_regions_sha256': snapshot(entities), 'native_fixture_process': f['target_process']}


def checkpoint(runtime, phase, digest, material_sha=None, bootstrap_sha=None):
    prior_phase = 'source' if phase == 'target' else 'target'
    path = runtime / OUT / prior_phase / 'result.json'
    need(isinstance(digest, str) and re.fullmatch('[0-9a-f]{64}', digest) and sha(path) == digest,
         'Explicit accepted predecessor result SHA required')
    data = read(path)
    need(data.get('schema') == SCHEMA and data.get('phase') == prior_phase and data.get('status') == 'PASS'
         and data.get('phase_accepted') is True and data.get('runtime') == str(runtime)
         and data.get('exit_code') == 0 and data.get('owned_processes_remaining') == {}
         and data.get('immutable_inputs_unchanged') is True and data.get('normal_console_stop_sent') is True
         and data.get('config_output', {}).get('status') == 'CONFIG_ONLY_MATCH'
         and data.get('config_output', {}).get('candidate_tree_sha256')
         and sha(path.parent / 'config-attestation-v1.json') == data.get('config_output_sha256')
         and data.get('materialization_sha256') == material_sha
         and data.get('bootstrap_sha256') == bootstrap_sha and data.get('fixture_sha256') == FIXTURE_SHA,
         'Predecessor is not an accepted stopped exact-input phase')
    token = data.get('owner_uuid')
    need(isinstance(data.get('started_at'), str), 'Predecessor owner mint timestamp absent')
    started = read(path.parent / 'started.json')
    need(started.get('owner_uuid') == token and started.get('owner_mint_tick') == data.get('owner_mint_tick')
         and started.get('started_at') == data.get('started_at'), 'Prior owner mint provenance changed')
    if isinstance(token, str): mark_token(token, data['started_at'], data['owner_mint_tick'])
    need(isinstance(token, str) and str(uuid.UUID(token)) == token and not owned(token), 'Predecessor owner still live')
    need(data.get('immutable_post') and snapshot(data['immutable_post']) == data['immutable_post'],
         'Predecessor immutable inputs drifted')
    need(data.get('saved_state_sha256') and snapshot(data['saved_state_sha256']) == data['saved_state_sha256'],
         'Predecessor saved state drifted')
    need(data.get('native_results') and all(sha(p) == row['sha256'] for p, row in data['native_results'].items()),
         'Predecessor native evidence drifted')
    need(set(files(runtime)) == set(data['closed_runtime_files']) | {str(path.relative_to(runtime))},
         'Unexpected file in closed predecessor runtime')
    line = data['promotion']['lineage']
    proof = promotion(runtime, line)
    need(proof == data['promotion'], 'Predecessor promotion drifted')
    if phase == 'postclaim-restart':
        need(target_disk(runtime, proof) == data['target_native_disk_proof'], 'Target native disk proof drifted')
        source = checkpoint_source(runtime, data)
        need(source['phase_accepted'] is True, 'Source predecessor drifted')
        need(not (runtime / 'postclaim-restart-input-v1.json').exists()
             and not (runtime / 'postclaim-restart-observation-v1.json').exists(), 'Restart already attempted')
    return data, proof


def checkpoint_source(runtime, target):
    path = runtime / OUT / 'source/result.json'
    need(sha(path) == target.get('source_result_sha256'), 'Target source SHA changed')
    data = read(path)
    need(data.get('phase') == 'source' and data.get('status') == 'PASS' and data.get('phase_accepted') is True,
         'Target source no longer accepted')
    return data


def save_proof(runtime, result, phase, proof):
    evidence = result.get('save_evidence', {})
    need(isinstance(evidence, dict) and result.get('case_status') == 'PASS'
         and isinstance(evidence.get('region_evidence'), dict) and evidence['region_evidence']
         and evidence.get('region_files') == len(evidence['region_evidence']), 'No enumerated normal save evidence')
    world = runtime / 'world'
    rows = {'level.dat': evidence.get('level_dat')}
    rows.update({'region/' + k: v for k, v in evidence['region_evidence'].items()})
    last = instant_ns(result['last_case_completed_at'])
    verified = instant_ns(evidence.get('verified_at'))
    need(last < verified and evidence.get('level_dat_sha256') == rows['level.dat'].get('sha256'),
         'Native save evidence chronology/digest mismatch')
    need(all(isinstance(row, dict) and type(row.get('bytes')) is int and 0 < row['bytes'] <= 512 * 1024**2
             and re.fullmatch('[0-9a-f]{64}', row.get('sha256', ''))
             and last < instant_ns(row.get('mtime')) <= verified for row in rows.values()),
         'Native save entry bounds or chronology invalid')
    need(sum(row['bytes'] for row in rows.values()) <= 4 * 1024**3, 'Native save evidence too large')
    if phase == 'source':
        archive = Path(proof['archive'])
        with zipfile.ZipFile(archive) as z:
            for name, row in rows.items():
                data = z.read('world/' + name)
                need(len(data) == row['bytes'] and hashlib.sha256(data).hexdigest() == row['sha256'],
                     'Source archive native save mismatch: ' + name)
    else:
        for name, row in rows.items():
            p = safe(world / name)
            need(p.stat().st_size == row['bytes'] and p.stat().st_mtime_ns == instant_ns(row['mtime'])
                 and sha(p) == row['sha256'],
                 'Working native save mismatch: ' + name)
    return {'kind': 'archive' if phase == 'source' else 'working', 'enumerated_files': rows}


def verify_restart(runtime, target, proof, report, manifest_sha):
    observation = read(runtime / 'postclaim-restart-observation-v1.json')
    need(observation.get('schema') == 'world_axiom_postclaim_restart_observation_v1'
         and observation.get('manifest_sha256') == manifest_sha
         and observation.get('transaction') == proof['transaction']
         and observation.get('journal_sha256') == target['target_native_disk_proof']['journal_sha256']
         and observation.get('prior_target_process') == proof['fixture']['target_process']
         and observation.get('case_observation') == 'PASS_AWAITING_NORMAL_SHUTDOWN_AND_POSTEXIT_SAVE_VERIFICATION',
         'Java RestartObservation did not bind accepted target')
    need(proof['fixture'] == target['promotion']['fixture'], 'Restart mutated target fixture')
    only = next(iter(report['native_results'].values()))['evidence']
    started = str(only['process_id']) + '@' + only['process_started_at']
    need(observation.get('process') == started
         and started not in {proof['fixture']['source_process'], proof['fixture']['target_process']},
         'Restart was not a distinct third JVM')
    need(sha(runtime / 'world/data/better_world_management/world-axiom-journal-v1.json') == observation['journal_sha256'],
         'Restart altered released claim journal')
    return {'observation_sha256': sha(runtime / 'postclaim-restart-observation-v1.json'),
            'native_process': started}

def admission(args, runtime):
    need(args.allow_real_succession and args.synthetic_parent23, 'Explicit real succession and synthetic parent23 flags required')
    need(type(args.seconds) is int and 120 <= args.seconds <= 3600, 'Phase budget must be 120..3600 seconds')
    need(args.fixtures_sha256 == FIXTURE_SHA, 'Explicit exact fixture SHA required')
    runtime_identity(runtime, args.materialization_sha256, args.bootstrap_sha256, args.fresh_runtime_nonce)
    if args.phase == 'source':
        need(not args.source_result_sha256 and not args.target_result_sha256, 'Source cannot use predecessor evidence')
        material = validate_source(runtime, args.materialization_sha256, args.bootstrap_sha256,
                                   args.fresh_runtime_nonce)
        return material, None, None
    material = validate_pins(runtime, args.materialization_sha256, args.bootstrap_sha256,
                             args.fresh_runtime_nonce)
    need(not (runtime / OUT / args.phase).exists(), 'Phase already attempted')
    if args.phase == 'target':
        need(args.source_result_sha256 and not args.target_result_sha256, 'Target needs only source result SHA')
        predecessor, proof = checkpoint(runtime, args.phase, args.source_result_sha256,
                                        args.materialization_sha256, args.bootstrap_sha256)
    else:
        need(args.target_result_sha256 and not args.source_result_sha256, 'Restart needs only target result SHA')
        predecessor, proof = checkpoint(runtime, args.phase, args.target_result_sha256,
                                        args.materialization_sha256, args.bootstrap_sha256)
    return material, predecessor, proof


def verify_wrapper(runtime):
    entry = (runtime / 'run.sh').read_text()
    wrapper = (runtime / 'better-world-management-server.sh').read_text()
    forge = (runtime / 'run-forge.sh').read_text()
    need('better-world-management-server.sh' in entry and 'run-forge.sh' in wrapper
         and 'cat <&0 >&8' in wrapper and 'wait "$SUCCESSOR_PID"' in wrapper,
         'Unreviewed production supervisor/stdin path')
    matches = re.findall(r'@(libraries/[^\s"\']+/unix_args\.txt)', forge)
    need(len(matches) == 1 and '@user_jvm_args.txt' in forge, 'Unreviewed Forge entry')
    launch = shlex.split((runtime / matches[0]).read_text())
    need('--launchTarget' in launch and launch[launch.index('--launchTarget') + 1] == 'forgeserver',
         'Not normal production dedicated target')


def overlay(runtime, out, phase, target_sha=None):
    jvm_path = runtime / 'user_jvm_args.txt'
    (out / 'prior-user_jvm_args.txt').write_bytes(jvm_path.read_bytes())
    prior = shlex.split(jvm_path.read_text(), comments=True)
    need(all(x.startswith('-') and 'gameTestServer' not in x for x in prior), 'Unsafe inherited JVM argument')
    kept = [x for x in prior if not x.startswith(('-Xms', '-Xmx', '-XX:ActiveProcessorCount=',
            '-Djava.io.tmpdir=', '-Dworld_axioms.', '-Dbetter_world_management.fixture.'))]
    jvm = kept + ['-Xms512m', '-Xmx6G', '-XX:ActiveProcessorCount=4', '-Djava.io.tmpdir=/home/dev/.tmp',
                  '-Dworld_axioms.nativeFixtures=true', '-Dworld_axioms.campaign=succession',
                  '-Dworld_axioms.autoFixtures=true', '-Dworld_axioms.test=' + TESTS[phase],
                  '-Dworld_axioms.stopAfterFixtures=' + ('false' if phase == 'source' else 'true'),
                  '-Dbetter_world_management.fixture.allow_real_succession=true',
                  '-Dbetter_world_management.fixture.synthetic_parent_generation=23']
    manifest_sha = None
    if phase == 'postclaim-restart':
        path = runtime / 'postclaim-restart-input-v1.json'
        target_path = runtime / OUT / 'target/result.json'
        need(sha(target_path) == target_sha, 'Target changed before restart manifest')
        write(path, {'schema': 'world_axiom_postclaim_restart_input_v1',
                     'target_result_path': str(target_path), 'target_result_sha256': target_sha})
        manifest_sha = sha(path)
        jvm.append('-Dworld_axioms.restartManifestSha256=' + manifest_sha)
    with jvm_path.open('w') as stream:
        stream.write('\n'.join(shlex.quote(x) for x in jvm) + '\n'); stream.flush(); os.fsync(stream.fileno())
    properties = runtime / 'server.properties'
    (out / 'prior-server.properties').write_bytes(properties.read_bytes())
    with socket.socket() as sock:
        sock.bind(('127.0.0.1', 0)); port = sock.getsockname()[1]
    intent = {'server-ip': '127.0.0.1', 'server-port': str(port), 'online-mode': 'false',
              'level-name': 'world', 'view-distance': '3', 'simulation-distance': '3',
              'enable-rcon': 'false', 'enable-query': 'false'}
    lines = [line for line in properties.read_text().splitlines()
             if not re.match(r'^\s*(?:' + '|'.join(intent) + r')\s*[=:]', line)]
    with properties.open('w') as stream:
        stream.write('\n'.join(lines + [k + '=' + v for k, v in intent.items()]) + '\n')
        stream.flush(); os.fsync(stream.fileno())
    return jvm, intent, manifest_sha


def immutable_paths(runtime, material, phase, out):
    # Forge rewrites configs: a separate exact-pair inventory and finite
    # same-pair Smoke-output attestation guards ALL config paths, not a skip.
    # Bundle, policy, JVM args and server.properties are explicit overlays.
    excluded = {'mods/better-world-management-0.1.0.jar', 'kubejs/config/crafting_policy.json',
                'user_jvm_args.txt', 'server.properties'}
    paths = [runtime / name for name in material['final_files'] if name not in excluded
             and not name.startswith('config/')]
    paths += [runtime / 'runtime-inputs-v1.json', runtime / 'all24-source-bootstrap-v1/result.json',
              FIXTURE, ROOT / 'scripts/all24_bootstrap_main.py', ROOT / 'scripts/all24_materialize_main.py',
              ROOT / 'scripts/all24_config_attestation_main.py',
              ROOT / 'dist/build-26/server/better-content.zip', ROOT / 'dist/build-26/client/better-content.zip',
              Path(__file__), runtime / OUT / 'source/original-production.jar',
              runtime / OUT / 'source/native-bundle.json', runtime / OUT / 'source/native-policy.json']
    paths += [p for p in (runtime / 'all24-source-bootstrap-v1').rglob('*') if p.is_file()]
    if phase != 'source':
        paths += [runtime / OUT / 'source/result.json']
    if phase == 'postclaim-restart':
        paths += [runtime / OUT / 'target/result.json', runtime / 'postclaim-restart-input-v1.json',
                  runtime / 'world-axiom-succession-fixture-v1.json']
    return sorted(set(safe(p) for p in paths))


def run_phase(args, runtime, material, predecessor):
    out = runtime / OUT / args.phase
    if args.phase == 'source': (runtime / OUT).mkdir(mode=0o700)
    out.mkdir(mode=0o700)  # exclusive one-shot phase admission
    token = str(uuid.uuid4())
    mark_token(token)
    report = {'schema': SCHEMA, 'phase': args.phase, 'runtime': str(runtime), 'owner_uuid': token,
              'status': 'FAILED', 'phase_accepted': False, 'native_acceptance': False,
              'owner_mint_tick': TOKEN_START[token],
              'materialization_sha256': args.materialization_sha256,
              'bootstrap_sha256': args.bootstrap_sha256,
              'fixture_sha256': FIXTURE_SHA, 'production_sha256': PRODUCTION_SHA,
              'source_result_sha256': args.source_result_sha256 or (predecessor.get('source_result_sha256') if predecessor else None),
              'target_result_sha256': args.target_result_sha256,
              'started_at': datetime.now(timezone.utc).isoformat(), 'operational_budget_seconds': args.seconds,
              'normal_console_stop_sent': False, 'observed_owned_commands': {},
              'immutable_scope': 'nonconfig production inputs; every config separately attested against exact Build26 ZIP and same-pair Smoke',
              'native_functional_cpu_allocation': 4,
              'scope': 'One phase only; nonshipping Build26 fixture, no release promotion'}
    process = None
    immutable = {}
    config_input = None
    config_refs = None
    proof = None
    started = time.monotonic()
    prior = set((runtime / 'axiom-native-results').glob('*/results.json'))
    prior_hashes = snapshot(prior)
    old_handlers = {}
    def interrupt(sig, _frame): raise InterruptedError('Bounded phase signal: ' + str(sig))
    for sig in (signal.SIGINT, signal.SIGTERM, signal.SIGALRM):
        old_handlers[sig] = signal.signal(sig, interrupt)
    signal.alarm(args.seconds - 45)
    try:
        need(JAVA.is_file(), 'Pinned Java 17 absent')
        with zipfile.ZipFile(FIXTURE) as z:
            need(CLASS.replace('.', '/') + '.class' in z.namelist(), 'Exact fixture class absent')
        if args.phase == 'source':
            report['native_bundle'] = bundle(runtime / 'mods/better-world-management-0.1.0.jar', FIXTURE, out)
            report['native_policy'] = policy(runtime, out)
            line = lineage(runtime, 23)
        else:
            source = read(runtime / OUT / 'source/result.json')
            report['native_bundle'] = source['native_bundle']
            report['native_policy'] = source['native_policy']
            need(sha(runtime / 'mods/better-world-management-0.1.0.jar') == report['native_bundle']['bundle_sha256'],
                 'Source native bundle changed')
            need(sha(runtime / 'kubejs/config/crafting_policy.json') == report['native_policy']['policy_sha256'],
                 'Source fixture namespace policy changed')
            line = predecessor['promotion']['lineage']
        verify_wrapper(runtime)
        report['jvm_arguments'], report['server_properties'], manifest_sha = overlay(runtime, out, args.phase,
                                                                                   args.target_result_sha256)
        if manifest_sha: report['restart_manifest_sha256'] = manifest_sha
        immutable = snapshot(immutable_paths(runtime, material, args.phase, out))
        write(out / 'immutable-pre.json', immutable)
        need(immutable[str(ROOT / 'dist/build-26/server/better-content.zip')] ==
             material['accepted_zip_expected_sha256'], 'Exact accepted ZIP drifted')
        config_gate = config_module()
        config_refs = config_gate.anchors()  # Pins BOTH ZIPs, ready/Smoke, and source witness.
        config_input = config_gate.tree(runtime / 'config')
        if args.phase == 'source':
            need(config_input == config_refs[0], 'Fresh source config is not exact packaged Build26 input')
        else:
            expected_config = predecessor['config_output']['candidate_tree_sha256']
            need(config_gate.inventory_sha(config_input) == expected_config,
                 'Predecessor config output changed before this distinct phase')
        report['config_input_tree_sha256'] = config_gate.inventory_sha(config_input)
        report['config_reference_smoke_tree_sha256'] = config_gate.SMOKE_TREE_SHA
        write(out / 'started.json', report)
        env = {k: v for k, v in os.environ.items()
               if k not in {'JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS', 'JAVA_OPTIONS',
                            'CLASSPATH', 'BC_WLM_SUPERVISED'} and not k.startswith('PRESTIGE_')}
        env.update({OWNER_KEY: token, 'BC_JAVA': str(JAVA), 'JAVA_HOME': str(JAVA.parent.parent),
                    'TMPDIR': '/home/dev/.tmp', 'JDK_JAVA_OPTIONS': '-Xmx6G -XX:ActiveProcessorCount=4',
                    'PYTHONDONTWRITEBYTECODE': '1'})
        Path('/home/dev/.tmp').mkdir(exist_ok=True)
        log_path = out / 'stdout-stderr.log'
        with log_path.open('xb') as log:
            process = subprocess.Popen(['bash', './run.sh', 'nogui'], cwd=runtime, env=env,
                                       stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT,
                                       start_new_session=True)
            log_offset = 0
            while process.poll() is None:
                for pid, command in owned(token).items():
                    # A later reaped JVM is not evidence that its earlier live
                    # command vanished; retain the first observed live identity.
                    if pid not in report['observed_owned_commands'] or (
                            report['observed_owned_commands'][pid] == ['OWNED_ZOMBIE_AWAIT_REAP']
                            and command != ['OWNED_ZOMBIE_AWAIT_REAP']):
                        report['observed_owned_commands'][pid] = command
                with log_path.open('rb') as reader:
                    reader.seek(log_offset); chunk = reader.read(); log_offset = reader.tell()
                text = chunk.decode(errors='replace')
                if re.search(r'phase=attempt-[2-8]-|phase=rolled-back|did not pass health verification; retrying|'
                             r'Failed to start the minecraft server|Exception in server tick loop|MixinApplyError', text):
                    raise ValueError('Production retry/rollback/startup fatal observed')
                if args.phase == 'source' and not report['normal_console_stop_sent']:
                    fixture_path = runtime / 'world-axiom-succession-fixture-v1.json'
                    if fixture_path.exists() and (runtime / '.better_world_management/lineage-v5.tsv').exists():
                        native_paths = set((runtime / 'axiom-native-results').glob('*/results.json')) - prior
                        source_process = read(fixture_path).get('source_process', '')
                        source_pid = source_process.split('@')[0]
                        source_reports = [p for p in native_paths if read(p).get('process_id') == int(source_pid)] if source_pid.isdigit() else []
                        native_fixture = read(fixture_path)
                        if source_reports and native_fixture.get('source_successor_guard') == 'PASS_NO_RESTAGE' and contract(runtime / '.better_world_management/lineage-v5.tsv',
                                                        'BC_PRESTIGE_LINEAGE_V5').get('generation') == '24':
                            proof = promotion(runtime, line)
                            active = contract(runtime / '.better_world_management/control/active-successor-process-v2.tsv',
                                              'BC_PRESTIGE_ACTIVE_SUCCESSOR_V2')
                            target_pid = active.get('pid')
                            now = owned(token)
                            guard = proof['fixture'].get('source_successor_guard_process', '')
                            need(proof['fixture'].get('source_successor_guard') == 'PASS_NO_RESTAGE'
                                 and guard.split('@')[0] == target_pid,
                                 'Successor did not run the native no-restage guard')
                            need(active.get('transaction') == proof['transaction'] and active.get('attempt') == '1'
                                 and source_pid != target_pid and int(source_pid) in report['observed_owned_commands']
                                 and target_pid is not None and int(target_pid) in now
                                 and forge_command(now[int(target_pid)])
                                 and forge_command(report['observed_owned_commands'][int(source_pid)]),
                                 'No actual UUID-owned healthy production successor')
                            report['actual_successor_process'] = active
                            report['promotion'] = proof
                            report['normal_console_stop_log_offset'] = log_path.stat().st_size
                            process.stdin.write(b'stop\n'); process.stdin.flush()
                            report['normal_console_stop_sent'] = True
                time.sleep(.2)
            report['exit_code'] = process.wait(timeout=2)
            process.stdin.close()
        need(report['exit_code'] == 0, 'Production supervisor exited nonzero')
        text = log_path.read_text(errors='replace')
        tail = (log_path.read_bytes()[report.get('normal_console_stop_log_offset', 0):].decode(errors='replace')
                if args.phase == 'source' else text)
        need('Stopping server' in tail and ('Saving worlds' in tail or 'Saving players' in tail),
             'No normal native server stop/save')
        for pid, command in owned(token).items():
            if pid not in report['observed_owned_commands'] or (
                    report['observed_owned_commands'][pid] == ['OWNED_ZOMBIE_AWAIT_REAP']
                    and command != ['OWNED_ZOMBIE_AWAIT_REAP']):
                report['observed_owned_commands'][pid] = command
        all_results = phase_results(runtime, prior, args.phase)
        need(snapshot(prior) == prior_hashes, 'Prior native result changed during phase')
        source_pid = None
        if args.phase == 'source':
            source_pid = int(report['promotion']['fixture']['source_process'].split('@')[0])
            accepted = {p: r for p, r in all_results.items() if r['evidence']['process_id'] == source_pid}
            ignored = {p: r for p, r in all_results.items() if p not in accepted}
            need(len(accepted) == 1 and all(r['evidence']['process_id'] == int(report['actual_successor_process']['pid']) for r in ignored.values()),
                 'Wrong original source/late successor native reports')
            report['ignored_successor_reentry_sha256'] = {p: r['sha256'] for p, r in ignored.items()}
            report['native_results'] = accepted
            need(report['normal_console_stop_sent'], 'No real supervisor successor guard/normal stop')
        else:
            report['native_results'] = all_results
            report['normal_console_stop_sent'] = True  # fixture automatic normal console stop
        proof = promotion(runtime, line)
        report['promotion'] = proof
        for path, row in report['native_results'].items():
            data = row['evidence']
            phase_start = datetime.fromisoformat(report['started_at'])
            native_start = datetime.fromisoformat(data['started_at'])
            native_finish = datetime.fromisoformat(data['last_case_completed_at'])
            need(phase_start <= native_start <= native_finish, 'Native evidence predates this fresh phase')
            actual_process = str(data['process_id']) + '@' + data['process_started_at']
            expected_process = (proof['fixture']['source_process'] if args.phase == 'source' else
                                proof['fixture'].get('target_process') if args.phase == 'target' else None)
            need(expected_process is None or actual_process == expected_process,
                 'Native result does not bind actual source/target JVM identity')
            need(data['process_id'] in report['observed_owned_commands']
                 and forge_command(report['observed_owned_commands'][data['process_id']]),
                 'Native report PID was not observed as owned production Forge')
            report.setdefault('verified_native_save_evidence', {})[path] = save_proof(runtime, data, args.phase, proof)
        if args.phase == 'target': report['target_native_disk_proof'] = target_disk(runtime, proof)
        if args.phase == 'postclaim-restart':
            report['restart_observation'] = verify_restart(runtime, predecessor, proof, report, manifest_sha)
        need((runtime / 'world/level.dat').is_file() and list((runtime / 'world/region').glob('*.mca')),
             'Normal native world save absent')
        report['status'] = 'PASS'
    except BaseException as error:
        report['error'] = type(error).__name__ + ': ' + str(error)
        report['status'] = 'FAILED'
    finally:
        signal.alarm(40)
        for sig in (signal.SIGINT, signal.SIGTERM): signal.signal(sig, signal.SIG_IGN)
        try:
            report['owned_processes_remaining'] = cleanup(token, process)
        except BaseException as error:
            report['owned_processes_remaining'] = {'UNKNOWN': type(error).__name__ + ': ' + str(error)}
            report['status'] = 'FAILED'
            try:
                report['known_owner_cleanup_fallback'] = cleanup_known_tagged_after_unknown(token)
            except BaseException as fallback_error:
                report['known_owner_cleanup_fallback_error'] = type(fallback_error).__name__ + ': ' + str(fallback_error)
        if process is not None:
            try: process.wait(timeout=1)
            except subprocess.TimeoutExpired: report['status'] = 'FAILED'
        try:
            after = snapshot(Path(p) for p in immutable)
            report['immutable_post'] = after
            report['immutable_inputs_unchanged'] = bool(immutable) and after == immutable
            write(out / 'immutable-post.json', after)
        except BaseException as error:
            report['immutable_inputs_unchanged'] = False
            report['post_hash_error'] = repr(error)
        if config_input is not None and config_refs is not None:
            try:
                gate = config_module()
                need(sha(ROOT / 'scripts/all24_config_attestation_main.py') ==
                     immutable[str(ROOT / 'scripts/all24_config_attestation_main.py')],
                     'Config verifier changed during phase')
                current_refs = gate.anchors()  # Revalidate the external Smoke/witness trees after the JVM.
                need(all(gate.inventory_sha(a) == gate.inventory_sha(b)
                         for a, b in zip(current_refs, config_refs)),
                     'External config reference changed during phase')
                observed = gate.tree(runtime / 'config')
                need(isinstance(proof, dict) and isinstance(proof.get('fixture'), dict)
                     and proof['fixture'].get('source_process'),
                     'Native source/successor promotion must bind biome registry admission')
                config_result = gate.verify(observed, *current_refs,
                                            registered_biomes=proof['fixture'].get('registered_biomes'))
                need(config_result['native_biome_registry_sha256'] is not None,
                     'Native biome registry witness missing')
                need(gate.inventory_sha(gate.tree(runtime / 'config')) ==
                     config_result['candidate_tree_sha256'], 'Config changed during post-attestation')
                report['config_output'] = {k: v for k, v in config_result.items()
                                           if k != 'classifications'}
                report['config_output']['classification_counts'] = {
                    cls: list(config_result['classifications'].values()).count(cls)
                    for cls in sorted(set(config_result['classifications'].values()))}
                report['config_output']['native_acceptance'] = False
                write(out / 'config-attestation-v1.json', config_result)
                report['config_output_sha256'] = sha(out / 'config-attestation-v1.json')
            except BaseException as error:
                report['config_output_error'] = type(error).__name__ + ': ' + str(error)
                report['status'] = 'FAILED'
        report['phase_accepted'] = (report['status'] == 'PASS' and report['immutable_inputs_unchanged']
                                    and report['owned_processes_remaining'] == {}
                                    and report.get('config_output', {}).get('status') == 'CONFIG_ONLY_MATCH'
                                    and bool(report.get('config_output_sha256')))
        if report['phase_accepted'] and args.phase in ('source', 'target'):
            try:
                saved = [p for base in (runtime / 'world', runtime / '.better_world_management')
                         for p in base.rglob('*') if p.is_file()]
                saved += [runtime / 'world-axiom-succession-fixture-v1.json', runtime / 'server.properties']
                report['saved_state_sha256'] = snapshot(saved)
                report['closed_runtime_files'] = sorted(files(runtime))
            except BaseException as error:
                report['phase_accepted'] = False
                report['saved_state_error'] = repr(error)
        if not report['phase_accepted']: report['status'] = 'FAILED'
        report['external_zombie_parent_proofs'] = sorted(TOKEN_EXTERNAL_ZOMBIES[token].values(),
                                                         key=lambda proof: (proof['pid'], proof['start_tick']))
        report['native_acceptance'] = bool(report['phase_accepted'])  # Only this one real native phase, not release readiness.
        signal.alarm(0)
        report['finished_at'] = datetime.now(timezone.utc).isoformat()
        report['elapsed_seconds'] = time.monotonic() - started
        write(out / 'result.json', report)
        for sig, prior_handler in old_handlers.items(): signal.signal(sig, prior_handler)
    return 0 if report['phase_accepted'] else 1


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--runtime', required=True, type=Path)
    parser.add_argument('--phase', required=True, choices=TESTS)
    parser.add_argument('--materialization-sha256', required=True)
    parser.add_argument('--bootstrap-sha256', required=True)
    parser.add_argument('--fresh-runtime-nonce', help='Required for a separately materialized fresh Build22 nonce')
    parser.add_argument('--fixtures-sha256', required=True)
    parser.add_argument('--source-result-sha256')
    parser.add_argument('--target-result-sha256')
    parser.add_argument('--allow-real-succession', action='store_true')
    parser.add_argument('--synthetic-parent23', action='store_true')
    parser.add_argument('--seconds', type=int, default=1800)
    args = parser.parse_args(argv)
    runtime = safe(args.runtime)
    material, predecessor, _ = admission(args, runtime)  # no launcher writes before full admission
    lock_owner = ROOT / 'pack-test-lock.main.kts'
    token = os.environ.get('BC_PACK_TEST_LOCK_TOKEN')
    if token is None:
        # Use the pack's exact kernel-backed fail-fast lock rather than a separate flock.
        command = [str(lock_owner), 'run', 'test', 'all24-' + args.phase, '--',
                   sys.executable, str(Path(__file__).absolute()), *(argv if argv is not None else sys.argv[1:])]
        return subprocess.call(command, cwd=ROOT)
    owner = read(Path(os.environ.get('BC_PACK_TEST_LOCK_OWNER', '')))
    need(owner.get('token') == token and owner.get('selector') == 'all24-' + args.phase
         and owner.get('kind') == 'test', 'No matching fail-fast runner lock owner')
    state_root = Path(os.environ.get('BC_PACK_TEST_STATE_ROOT') or
                      str(Path.home() / '.local/share/worklane/pack-tests'))
    with safe(state_root / 'runner.lock').open('r+b') as runner_lock:
        try:
            fcntl.lockf(runner_lock.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            pass  # pack-test-lock.main.kts owns the actual kernel lock
        else:
            fcntl.lockf(runner_lock.fileno(), fcntl.LOCK_UN)
            raise ValueError('Runner owner metadata exists without the kernel lock')
    lock_path = runtime / '.all24-launch.lock'
    with lock_path.open('a+') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        need(lock.seek(0, 2) == 0, 'Invalid runtime lock payload')
        return run_phase(args, runtime, material, predecessor)


if __name__ == '__main__':
    try: raise SystemExit(main())
    except Exception as error:
        print('Main Build22 all24 launcher refused; retained evidence untouched: ' + repr(error), file=sys.stderr)
        raise SystemExit(1)

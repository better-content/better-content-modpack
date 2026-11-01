#!/usr/bin/env python3
"""Main Build22 public synthetic-parent23 bootstrap for all-24 selection.

This creates neither a frozen build nor a reset request. Only an actual source
Forge commit can bind the source world and request succession. The 23/23 parent
represents zero historical resets, not an accepted all-24 test by itself.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import signal
import subprocess
import sys
import time
import zipfile

_parent_path = Path(__file__).absolute().parent / 'all24_parent_preflight_main.py'
_spec = importlib.util.spec_from_file_location('_all24_main_preflight', _parent_path)
parent = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(parent)

ROOT = parent.ROOT
INIT = 'all24-source-bootstrap-v1'
RECEIPT = INIT + '/result.json'
LINEAGE = 'lineage-all23-main24'
SOURCE = '''import java.nio.file.*;
import com.bettercontent.betterworldmanagement.PrestigeContracts;
import com.bettercontent.betterworldmanagement.PrestigeActivePerksContract;
public final class RecipeSourceParentWriter {
 public static void main(String[] a) throws Exception {
  Path lineage = Path.of(a[1]), active = Path.of(a[2]);
  var expected = new PrestigeContracts.Lineage(a[3], 23, 23);
  if (a[0].equals("create")) {
   if (Files.exists(lineage, LinkOption.NOFOLLOW_LINKS) || Files.exists(active, LinkOption.NOFOLLOW_LINKS))
    throw new IllegalStateException("Refuse overwrite");
   PrestigeContracts.writeLineage(lineage, expected);
   PrestigeActivePerksContract.writeNeutral(active, PrestigeContracts.readLineage(lineage));
  } else if (!a[0].equals("read")) throw new IllegalArgumentException("mode");
  if (!PrestigeContracts.readLineage(lineage).equals(expected)
      || !PrestigeActivePerksContract.readNeutral(active, expected).lineageId().equals(a[3]))
   throw new IllegalStateException("Public synthetic parent lineage/neutral state mismatch");
  System.out.println(a[3] + "\\t23\\t23\\tneutral:-");
 }
}
'''
need, safe, sha, publish, write_json = parent.need, parent.safe, parent.sha, parent.publish, parent.write_json


def inventory(root):
    result = {}
    for path in sorted(root.rglob('*')):
        safe(path)
        need(path.is_dir() or (path.is_file() and path.stat().st_nlink == 1), 'Nonregular/hardlinked runtime entry')
        if path.is_file():
            result[path.relative_to(root).as_posix()] = {'sha256': sha(path), 'bytes': path.stat().st_size}
    return result


def expected_bytes():
    return (f'BC_PRESTIGE_LINEAGE_V5\nlineage\t{LINEAGE}\ntotal_prestiges\t23\ngeneration\t23\n'.encode(),
            f'BC_PRESTIGE_PERKS_V2\nlineage\t{LINEAGE}\ngeneration\t23\nperks\t-\n'.encode())


def run_owned(command, cwd, log_path, deadline):
    """Own the subprocess session; retain output and fail closed on leader/pipe timeout."""
    env = {'HOME': str(Path.home()), 'TMPDIR': str(Path.home() / '.tmp'),
           'LANG': 'C.UTF-8', 'LC_ALL': 'C.UTF-8', 'TZ': 'UTC',
           'PATH': '/home/dev/.local/bin:/usr/local/bin:/usr/bin:/bin', 'PYTHONDONTWRITEBYTECODE': '1'}
    remaining = deadline - time.monotonic()
    need(remaining > 0, 'Public helper deadline exhausted')
    process = subprocess.Popen([str(part) for part in command], cwd=cwd, env=env,
                               stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
    try:
        try:
            output, _ = process.communicate(timeout=remaining)
        except subprocess.TimeoutExpired as error:
            try: os.killpg(process.pid, signal.SIGTERM)
            except ProcessLookupError: pass
            try: output, _ = process.communicate(timeout=3)
            except subprocess.TimeoutExpired:
                try: os.killpg(process.pid, signal.SIGKILL)
                except ProcessLookupError: pass
                try: output, _ = process.communicate(timeout=3)
                except subprocess.TimeoutExpired as final:
                    process.stdout.close()
                    process.wait(timeout=2)
                    output = final.output or error.output or b''
            publish(log_path.with_name(log_path.stem + '-timeout.log'), output or error.output or b'')
            raise
    except BaseException:
        try: os.killpg(process.pid, signal.SIGKILL)
        except ProcessLookupError: pass
        if process.poll() is None: process.wait(timeout=3)
        raise
    try:
        os.killpg(process.pid, 0)
    except ProcessLookupError:
        pass
    else:
        os.killpg(process.pid, signal.SIGKILL)
        raise RuntimeError('Public helper left a process in its owned session')
    publish(log_path, output)
    need(process.returncode == 0, 'Public helper failed, see ' + str(log_path))
    return output.decode('utf-8', errors='strict')


def validate(runtime, materialization_sha256, receipt_sha256):
    runtime = safe(runtime)
    receipt_path = runtime / RECEIPT
    need(sha(receipt_path) == receipt_sha256, 'Explicit source bootstrap receipt hash changed')
    result = json.loads(receipt_path.read_bytes(), object_pairs_hook=parent.pairs)
    need(result.get('schema') == 'world_axiom_all24_source_bootstrap_v1'
         and result.get('status') == 'BOOTSTRAPPED_SYNTHETIC_PARENT_NOT_NATIVE_ACCEPTANCE'
         and result.get('runtime') == str(runtime) and result.get('materialization_sha256') == materialization_sha256
         and result.get('bootstrap_helper_sha256') == sha(Path(__file__))
         and result.get('preflight_helper_sha256') == sha(Path(parent.__file__))
         and result.get('actual_resets_performed') == 0 and result.get('world_created') is False
         and result.get('reset_requested') is False and result.get('native_acceptance') is False,
         'Invalid synthetic source bootstrap receipt')
    before = result['before_files']
    material = json.loads((runtime / 'runtime-inputs-v1.json').read_bytes(), object_pairs_hook=parent.pairs)
    expected = {name: {'sha256': row['sha256'], 'bytes': row['bytes']}
                for name, row in material['final_files'].items()}
    expected['runtime-inputs-v1.json'] = {'sha256': materialization_sha256,
                                         'bytes': (runtime / 'runtime-inputs-v1.json').stat().st_size}
    need(before == expected, 'Materialized main Build22 inputs changed before bootstrap')
    actual = inventory(runtime)
    actual.pop(RECEIPT, None)
    need(actual == result['final_files'] and all(actual.get(name) == row for name, row in before.items()),
         'Unexpected source bootstrap file/hash closure')
    allowed = {'.better_world_management/lineage-v5.tsv', '.better_world_management/perks-v2.tsv'}
    need(set(actual) - set(before) == allowed | {name for name in actual if name.startswith(INIT + '/')},
         'Source bootstrap may add only public parent contracts and own evidence')
    lineage, neutral = expected_bytes()
    need((runtime / '.better_world_management/lineage-v5.tsv').read_bytes() == lineage
         and (runtime / '.better_world_management/perks-v2.tsv').read_bytes() == neutral,
         'Published public contracts differ from source neutral parent23')
    need(not any((runtime / path).exists() for path in ('world', 'logs', '.better_world_management/control',
              'kubejs/config/world_axioms_runtime.json', 'axiom-native-results')),
         'Pre-source world, control, projection and native results must not exist')
    return result


def bootstrap(runtime, materialization_sha256, *, allow_synthetic_source=False):
    need(allow_synthetic_source is True, 'Explicit --allow-synthetic-source required')
    material = parent.validate_runtime(runtime, materialization_sha256)
    runtime = safe(runtime)
    owners = [row for row in material['overrides'] if row.get('repository') == 'better-world-management']
    need(len(owners) == 1, 'Exactly one pinned production World Management JAR')
    jar = safe(runtime / owners[0]['path'])
    need(jar.parent == runtime / 'mods' and sha(jar) == material['final_files'][owners[0]['path']]['sha256'],
         'Wrong pinned main Build22 production JAR')
    with zipfile.ZipFile(jar) as archive:
        need({'com/bettercontent/betterworldmanagement/PrestigeContracts.class',
              'com/bettercontent/betterworldmanagement/PrestigeActivePerksContract.class'} <= set(archive.namelist()),
             'Public lineage/neutral active contract absent from actual production JAR')
    before = inventory(runtime)
    evidence = runtime / INIT
    evidence.mkdir(mode=0o700)
    parent.sync_dir(runtime)
    intent = dict(schema='world_axiom_all24_source_bootstrap_v1', runtime=str(runtime),
                  status='BOOTSTRAPPING_NOT_NATIVE_ACCEPTANCE', created_utc=datetime.now(timezone.utc).isoformat(),
                  initialization='SYNTHETIC_PARENT23_NOT_23_REAL_RESETS', generation=23, total_prestiges=23,
                  actual_resets_performed=0, world_created=False, reset_requested=False, native_acceptance=False,
                  materialization_sha256=materialization_sha256, bootstrap_helper_sha256=sha(Path(__file__)),
                  preflight_helper_sha256=sha(Path(parent.__file__)), production_jar_sha256=sha(jar),
                  before_files=before)
    write_json(evidence / 'intent.json', intent)
    deadline = time.monotonic() + 90
    try:
        classes = evidence / 'classes'
        candidate = evidence / 'candidate'
        classes.mkdir(mode=0o700); candidate.mkdir(mode=0o700)
        source = evidence / 'RecipeSourceParentWriter.java'
        publish(source, SOURCE.encode())
        compile_command = [parent.JAVA_HOME / 'bin/javac', '-J-Xmx1G', '-J-XX:ActiveProcessorCount=2',
                           '-proc:none', '-implicit:none', '-cp', jar, '-d', classes, source]
        run_owned(compile_command, evidence, evidence / 'public-javac.log', deadline)
        prefix = [parent.JAVA_HOME / 'bin/java', '-Xmx256m', '-XX:ActiveProcessorCount=2',
                  '-cp', str(classes) + os.pathsep + str(jar), 'RecipeSourceParentWriter']
        names = ('lineage-v5.tsv', 'perks-v2.tsv')
        staged = [candidate / name for name in names]
        expected = f'{LINEAGE}\t23\t23\tneutral:-\n'
        need(run_owned([*prefix, 'create', *staged, LINEAGE], evidence,
                       evidence / 'public-create.log', deadline) == expected,
             'Public neutral parent writer readback differs')
        values = expected_bytes()
        need(all(path.read_bytes() == value for path, value in zip(staged, values)),
             'Production public writer produced unexpected bytes')
        need({name: row for name, row in inventory(runtime).items() if not name.startswith(INIT + '/')} == before,
             'Materialized files changed during public writer')
        state = runtime / '.better_world_management'
        state.mkdir(mode=0o700)
        for name, value in zip(names, values): publish(state / name, value)
        need(run_owned([*prefix, 'read', state / names[0], state / names[1], LINEAGE], evidence,
                       evidence / 'public-published-readback.log', deadline) == expected,
             'Published lineage/neutral contract readback differs')
        after = inventory(runtime)
        result = {**intent, 'status': 'BOOTSTRAPPED_SYNTHETIC_PARENT_NOT_NATIVE_ACCEPTANCE',
                  'completed_utc': datetime.now(timezone.utc).isoformat(), 'public_readback': True,
                  'final_files': after, 'added_files': sorted(set(after) - set(before))}
        write_json(runtime / RECEIPT, result)
        validate(runtime, materialization_sha256, sha(runtime / RECEIPT))
        return result
    except BaseException as error:
        write_json(evidence / 'failed-retained.json', {**intent, 'status': 'FAILED_RETAINED_DO_NOT_REUSE',
                  'error': type(error).__name__ + ': ' + str(error)})
        raise


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--runtime', type=Path, required=True)
    parser.add_argument('--materialization-sha256', required=True)
    parser.add_argument('--allow-synthetic-source', action='store_true')
    args = parser.parse_args()
    result = bootstrap(args.runtime, args.materialization_sha256,
                       allow_synthetic_source=args.allow_synthetic_source)
    print(json.dumps({'status': result['status'], 'receipt': str(args.runtime / RECEIPT),
                      'receipt_sha256': sha(args.runtime / RECEIPT)}, sort_keys=True))


if __name__ == '__main__':
    main()

#!/usr/bin/env python3
"""Inert stdlib timing/exit/capture contracts; no Gradle, caches or native launches.
Fixtures and timing evidence are deliberately retained.
"""
import fcntl
import json
import os
from pathlib import Path
import signal
import shutil
import subprocess
import sys
import tempfile
import time
import unittest

SCRIPT = Path(__file__).with_name('bounded_facade.py').absolute()


class BoundedTimingTests(unittest.TestCase):
    def setUp(self):
        base = Path.home() / '.tmp'
        base.mkdir(exist_ok=True)
        self.root = Path(tempfile.mkdtemp(prefix='bounded-timing-', dir=base))
        self.log = self.root / 'command.log'
        self.sink = self.root / 'timing.jsonl'
        self.env = dict(os.environ, BC_TIMING_FILE=str(self.sink), BC_TIMING_PROFILE='inert',
                        BC_TIMING_RUN_ID='synthetic-run', BC_TIMING_INPUT_ID='fixture-v1',
                        BC_TIMING_ENVIRONMENT_ID='synthetic', BC_TIMING_CACHE_ID='no-cache',
                        BC_TIMING_REVISION='fixture-revision', UNRELATED_SECRET='must-not-appear')

    def command(self, program, budget=3, *extra):
        return [sys.executable, '-B', str(SCRIPT), '--cwd', str(self.root), '--budget-seconds',
                str(budget), '--log', str(self.log), *extra, '--', sys.executable, '-c', program]

    def run_probe(self, program, budget=3, *extra):
        return subprocess.run(self.command(program, budget, *extra), env=self.env,
                              capture_output=True, timeout=12)

    def records(self):
        return [json.loads(line) for line in self.sink.read_text().splitlines()]

    def assert_pair(self, outcome, exit_code):
        start, terminal = self.records()
        self.assertEqual(('start', 'RUNNING', None), (start['event'], start['status'], start['duration_ns']))
        self.assertEqual(('terminal', outcome, exit_code),
                         (terminal['event'], terminal['status'], terminal['exit_code']))
        self.assertEqual(start['operation_id'], terminal['operation_id'])
        self.assertGreaterEqual(terminal['duration_ns'], 0)
        self.assertEqual('process', terminal['scope'])
        self.assertEqual('nanoseconds', terminal['units'])
        self.assertIsNone(terminal['expected_runtime_ns'])
        self.assertEqual('UNKNOWN_UNTIL_COMPARABLE_SUCCESS_MEASURED', terminal['timeout_policy'])
        return terminal

    def test_logical_selector_is_recorded_but_not_inherited_by_nested_commands(self):
        self.env.update(BC_TIMING_PROCESS_NAME='source:fixture:default', BC_TIMING_INPUT_ID='a'*64)
        result = self.run_probe("import os; assert 'BC_TIMING_PROCESS_NAME' not in os.environ; print('unchanged')")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual('unchanged\n', self.log.read_text())
        terminal = self.assert_pair('SUCCEEDED', 0)
        self.assertEqual('source:fixture:default', terminal['process_name'])
        self.assertEqual(str(self.root), terminal['cwd'])
        self.assertEqual(64, len(terminal['command_sha256']))
        self.assertTrue(all(row['process_name']==terminal['process_name'] for row in self.records()))

    def test_success_is_incremental_and_metadata_is_selected(self):
        child = subprocess.Popen(self.command('import time; print("ready",flush=True); time.sleep(.2)'),
                                 env=self.env, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        try:
            end = time.monotonic() + 3
            while not self.log.exists() and time.monotonic() < end:
                time.sleep(.005)
            self.assertEqual('RUNNING', self.records()[0]['status'])
            child.communicate(timeout=8)
            self.assertEqual(0, child.returncode)
        finally:
            if child.poll() is None:
                child.terminate(); child.communicate(timeout=8)
        terminal = self.assert_pair('SUCCEEDED', 0)
        self.assertTrue(terminal['cleanup_complete'])
        self.assertEqual(3_000_000_000, terminal['configured_timeout_ns'])
        self.assertEqual('inert', terminal['profile'])
        self.assertEqual('fixture-revision', terminal['revision'])
        self.assertNotIn('must-not-appear', self.sink.read_text())
        self.assertNotIn('UNRELATED_SECRET', self.sink.read_text())
        report = json.loads(Path(str(self.sink) + '.report.json').read_text())
        self.assertEqual('COMPLETE', report['status'])
        self.assertEqual('NOT_EVALUATED', report['acceptance'])
        group = report['groups'][0]
        self.assertEqual(1, group['successful_sample_count'])
        self.assertEqual(terminal['duration_ns'], group['expected_runtime_ns'])
        self.assertEqual(2 * terminal['duration_ns'], group['recommended_timeout_ns'])

    def test_nonzero_preserves_status_and_output(self):
        result = self.run_probe('import sys; print("output"); sys.exit(7)')
        self.assertEqual(7, result.returncode, result.stderr)
        self.assertEqual('output\n', self.log.read_text())
        self.assert_pair('FAILED', 7)

    def test_timeout_is_censored_not_success_or_expected_runtime(self):
        result = self.run_probe('import time; time.sleep(10)', .08)
        self.assertEqual(124, result.returncode, result.stderr)
        terminal = self.assert_pair('TIMED_OUT', 124)
        self.assertTrue(terminal['cleanup_complete'])
        self.assertEqual([], terminal['surviving_pids'])
        self.assertEqual(80_000_000, terminal['configured_timeout_ns'])

    def test_signal_cancellation_retains_terminal_and_cleanup(self):
        child = subprocess.Popen(self.command('import time; print("ready",flush=True); time.sleep(10)'),
                                 env=self.env, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        try:
            end = time.monotonic() + 3
            while (not self.log.exists() or not self.log.stat().st_size) and time.monotonic() < end:
                time.sleep(.005)
            child.send_signal(signal.SIGTERM)
            child.communicate(timeout=8)
            self.assertEqual(143, child.returncode)
            self.assertTrue(self.assert_pair('CANCELLED', 143)['cleanup_complete'])
        finally:
            if child.poll() is None:
                child.terminate(); child.communicate(timeout=8)

    def test_failed_launch_has_start_and_failure_terminal(self):
        command = self.command('pass')
        command[-3:] = ['--', '/definitely-missing-timing-executable']
        result = subprocess.run(command, env=self.env, capture_output=True, timeout=8)
        self.assertEqual(2, result.returncode)
        self.assertEqual('FileNotFoundError', self.assert_pair('FAILED', None)['exception_type'])

    def test_capture_bytes_and_quiet_output_are_preserved(self):
        result = self.run_probe('print("captured")', 3, '--capture-limit', '1000')
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(b'captured\n', result.stdout)
        self.assert_pair('SUCCEEDED', 0)

    def test_concurrent_append_is_complete_and_has_independent_pairs(self):
        children = []
        try:
            for index in range(8):
                command = self.command('pass')
                command[command.index('--log') + 1] = str(self.root / ('log-' + str(index)))
                children.append(subprocess.Popen(command, env=self.env, stdout=subprocess.PIPE, stderr=subprocess.PIPE))
            for child in children:
                _, stderr = child.communicate(timeout=10)
                self.assertEqual(0, child.returncode, stderr)
        finally:
            for child in children:
                if child.poll() is None:
                    child.terminate(); child.communicate(timeout=8)
        groups = {}
        for record in self.records():
            groups.setdefault(record['operation_id'], []).append(record['event'])
        self.assertEqual(8, len(groups))
        self.assertTrue(all(events == ['start', 'terminal'] for events in groups.values()))

    def test_posix_lock_interoperability_blocks_append_before_launch(self):
        # Java FileChannel.lock uses POSIX record locks, not the separate Linux flock namespace.
        with self.sink.open('wb') as lock:
            fcntl.lockf(lock.fileno(), fcntl.LOCK_EX)
            child = subprocess.Popen(self.command('print("launched")'), env=self.env,
                                     stdout=subprocess.PIPE, stderr=subprocess.PIPE)
            try:
                time.sleep(.15)
                self.assertIsNone(child.poll())
                self.assertFalse(self.log.exists())
                fcntl.lockf(lock.fileno(), fcntl.LOCK_UN)
                _, stderr = child.communicate(timeout=8)
                self.assertEqual(0, child.returncode, stderr)
            finally:
                fcntl.lockf(lock.fileno(), fcntl.LOCK_UN)
                if child.poll() is None:
                    child.terminate(); child.communicate(timeout=8)
        self.assert_pair('SUCCEEDED', 0)

    def test_timing_failure_cannot_create_success_or_replace_nonzero(self):
        for status in (0, 7):
            self.sink = self.root / ('failure-' + str(status) + '.jsonl')
            self.env['BC_TIMING_FILE'] = str(self.sink)
            program = ('import os,pathlib,sys; p=pathlib.Path(os.environ["BC_TIMING_FILE"]); '
                       'p.rename(str(p)+".retained"); p.mkdir(); sys.exit(' + str(status) + ')')
            result = self.run_probe(program)
            self.assertEqual(2 if status == 0 else 7, result.returncode, result.stderr)
            self.assertTrue(Path(str(self.sink) + '.retained').is_file())
            self.assertIn(b'timing' if status else b'invalid', result.stderr)

    def test_unfinished_observation_is_retained_not_synthesized_success(self):
        # A missing terminal (e.g. uncatchable supervisor kill) is explicitly not a successful sample.
        self.assertEqual(0, self.run_probe('pass').returncode)
        start = self.records()[0]
        self.sink.write_text(json.dumps(start) + '\n')
        result = self.run_probe('pass')
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual('RUNNING', self.records()[0]['status'])
        self.assertEqual(3, len(self.records()))
        report = json.loads(Path(str(self.sink) + '.report.json').read_text())
        self.assertEqual('INCOMPLETE', report['status'])
        self.assertEqual('NOT_EVALUATED', report['acceptance'])

    def test_default_sink_is_propagated_and_available_run_id_is_recorded(self):
        self.env.pop('BC_TIMING_FILE')
        self.env.pop('BC_TIMING_RUN_ID')
        self.env['BC_TEST_RUN_ID'] = 'outer-real-run'
        result = self.run_probe('import os; print(os.environ["BC_TIMING_FILE"])')
        self.assertEqual(0, result.returncode, result.stderr)
        self.sink = Path(str(self.log) + '.timing.jsonl')
        self.assertEqual(str(self.sink), self.log.read_text().strip())
        self.assertEqual('outer-real-run', self.records()[1]['run_id'])
        self.assertTrue(Path(str(self.sink) + '.report.json').is_file())

    def test_copied_supervisor_requires_explicit_reporter_dependency(self):
        scripts = self.root / 'copied-pack' / 'scripts'
        scripts.mkdir(parents=True)
        copied = scripts / SCRIPT.name
        shutil.copyfile(SCRIPT, copied)
        command = self.command('print("original process output")')
        command[command.index(str(SCRIPT))] = str(copied)
        result = subprocess.run(command, env=self.env, capture_output=True, timeout=8)
        self.assertNotEqual(0, result.returncode, result.stderr)
        self.assertIn(b'timing_budget', result.stderr)
        self.assertFalse(self.log.exists())  # required pre-invocation selector cannot be skipped
        # Explicitly include both reviewed dependencies; no fallback skips selection/reporting.
        shutil.copyfile(SCRIPT.with_name('timing_budget.py'), scripts / 'timing_budget.py')
        shutil.copyfile(SCRIPT.with_name('timing_report.py'), scripts / 'timing_report.py')
        result = subprocess.run(command, env=self.env, capture_output=True, timeout=8)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertTrue(Path(str(self.sink) + '.report.json').is_file())


if __name__ == '__main__':
    unittest.main()

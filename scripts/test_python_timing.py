#!/usr/bin/env python3
"""Focused stdlib-only subprocess contracts; fixtures/evidence are retained."""
import json
import os
from pathlib import Path
import signal
import subprocess
import sys
import tempfile
import time
import unittest

HOOK = Path(__file__).resolve().parent / 'timing-python'
FIXTURE = '''import os, sys, unittest
print('argv=' + repr(sys.argv))
print('path=' + __file__)
class Cases(unittest.TestCase):
    def test_case(self):
        print('ORIGINAL STDOUT')
        mode = os.environ.get('FIXTURE_MODE', 'pass')
        if mode == 'fail': self.fail('original failure')
        if mode == 'error': raise ValueError('original error')
        if mode == 'skip': self.skipTest('original skip')
        if mode == 'cancel': raise KeyboardInterrupt('original cancellation')
        if mode == 'wait':
            print('READY', flush=True)
            import time
            time.sleep(20)
    @unittest.expectedFailure
    def test_expected(self): self.fail('expected')
    @unittest.expectedFailure
    def test_unexpected(self):
        if os.environ.get('FIXTURE_MODE') != 'unexpected': self.fail('expected')
if __name__ == '__main__': unittest.main(argv=[sys.argv[0]], verbosity=0)
'''


class PythonTimingTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        root = Path.home() / '.tmp' / 'python-timing-tests'
        root.mkdir(parents=True, exist_ok=True)
        cls.root = Path(tempfile.mkdtemp(prefix='run-', dir=root))
        cls.script = cls.root / 'fixture.py'
        cls.script.write_text(FIXTURE)

    def run_fixture(self, mode='pass', enabled=True, sink=None, extra=None):
        sink = sink or self.root / (self.id().split('.')[-1] + '-' + mode + '.jsonl')
        env = dict(os.environ, PYTHONPATH=str(HOOK), PYTHONDONTWRITEBYTECODE='1',
                   BC_TIMING_PYTHON='true' if enabled else 'false', BC_TIMING_FILE=str(sink),
                   FIXTURE_MODE=mode)
        if extra:
            env.update(extra)
        result = subprocess.run([sys.executable, '-B', str(self.script), 'original arg'],
                                env=env, capture_output=True, timeout=10)
        return result, sink

    def records(self, sink):
        return [json.loads(line) for line in sink.read_text().splitlines()]

    def test_outcomes_and_original_stdout_exit(self):
        for mode, status, code in [('pass', 'SUCCEEDED', 0), ('fail', 'FAILED', 1),
                                   ('error', 'ERROR', 1), ('skip', 'SKIPPED', 0),
                                   ('unexpected', 'SUCCEEDED', 1), ('cancel', 'CANCELLED', None)]:
            with self.subTest(mode=mode):
                original, _ = self.run_fixture(mode, enabled=False)
                observed, sink = self.run_fixture(mode)
                self.assertEqual(original.returncode, observed.returncode)
                if code is not None:
                    self.assertEqual(code, observed.returncode)
                self.assertEqual(original.stdout, observed.stdout)
                records = self.records(sink)
                tests = [r for r in records if r['scope'] == 'python_test']
                target = [r for r in tests if r['test_name'] == 'test_case']
                self.assertEqual(['start', 'terminal'], [r['event'] for r in target])
                self.assertEqual(status, target[-1]['status'])
                self.assertGreaterEqual(target[-1]['duration_ns'], 0)
                self.assertIsNone(target[-1]['configured_timeout_ns'])
                self.assertEqual('python_test', target[-1]['timeout_scope'])
                self.assertEqual('UNKNOWN', target[-1]['input_id'])
                if mode != 'cancel':
                    self.assertIn('EXPECTED_FAILURE', [r['status'] for r in tests])
                if mode == 'unexpected':
                    self.assertIn('UNEXPECTED_SUCCESS', [r['status'] for r in tests])
                self.assertTrue(any(r['scope'] == 'python_suite' for r in records))
                for start in (r for r in records if r['event'] == 'start'):
                    self.assertEqual(1, sum(r['event'] == 'terminal' and
                                           r['operation_id'] == start['operation_id'] for r in records))

    def test_inert_flags_and_missing_sink(self):
        for extra in ({'BC_TIMING_PYTHON': 'false'}, {'BC_TIMING_PYTHON': 'TRUE'}, {'BC_TIMING_FILE': ''}):
            sink = self.root / ('inert-' + str(len(list(self.root.iterdir()))) + '.jsonl')
            result, _ = self.run_fixture(sink=sink, extra=extra)
            self.assertEqual(0, result.returncode)
            self.assertFalse(sink.exists())

    def test_observer_failure_fails_success_without_masking_original(self):
        bad = self.root / 'is-directory'
        bad.mkdir(exist_ok=True)
        success, _ = self.run_fixture(sink=bad)
        self.assertNotEqual(0, success.returncode)
        self.assertIn(b'TimingObserverError', success.stderr)
        for mode in ('fail', 'error', 'cancel'):
            original, _ = self.run_fixture(mode, enabled=False)
            observed, _ = self.run_fixture(mode, sink=bad)
            self.assertEqual(original.returncode, observed.returncode)
            self.assertEqual(original.stdout, observed.stdout)
            self.assertNotIn(b'TimingObserverError', observed.stderr)
            if mode == 'cancel':
                self.assertIn(b'KeyboardInterrupt: original cancellation', observed.stderr)

    def test_metadata_and_separate_enclosing_cap(self):
        extra = {'BC_TIMING_' + key: value for key, value in
                 [('RUN_ID', 'run'), ('REVISION', 'revision'), ('INPUT_ID', 'inputs'),
                  ('PROFILE', 'profile'), ('ENVIRONMENT_ID', 'environment'), ('CACHE_ID', 'cache')]}
        extra['BC_TIMING_TIMEOUT_SECONDS'] = '12.5'
        result, sink = self.run_fixture(extra=extra)
        self.assertEqual(0, result.returncode)
        for record in self.records(sink):
            self.assertIsNone(record['configured_timeout_ns'])
            self.assertIsNone(record['expected_runtime_ns'])
            self.assertEqual(12_500_000_000, record['enclosing_process_timeout_ns'])
            for key in ('run_id', 'revision', 'input_id', 'profile', 'environment_id', 'cache_id'):
                self.assertNotEqual('UNKNOWN', record[key])

    def test_killed_process_retains_durable_start_not_success(self):
        sink = self.root / 'killed.jsonl'
        env = dict(os.environ, PYTHONPATH=str(HOOK), BC_TIMING_PYTHON='true',
                   BC_TIMING_FILE=str(sink), FIXTURE_MODE='wait', PYTHONDONTWRITEBYTECODE='1')
        output = self.root / 'killed.stdout'
        with output.open('wb') as stream:
            process = subprocess.Popen([sys.executable, '-B', str(self.script)], env=env,
                                       stdout=stream, stderr=subprocess.STDOUT)
            try:
                deadline = time.monotonic() + 5
                while b'READY' not in output.read_bytes() and time.monotonic() < deadline:
                    time.sleep(.01)
                self.assertIn(b'READY', output.read_bytes())
                process.send_signal(signal.SIGTERM)
                self.assertEqual(-signal.SIGTERM, process.wait(timeout=5))
            finally:
                if process.poll() is None:
                    process.kill()
                    process.wait(timeout=5)
        records = self.records(sink)
        self.assertTrue(any(r['scope'] == 'python_test' and r['event'] == 'start' for r in records))
        self.assertFalse(any(r['event'] == 'terminal' for r in records))

    def test_callback_arguments_identity_and_original_baseexception(self):
        script = self.root / 'callback-contract.py'
        script.write_text('''import unittest
class Exact(BaseException): pass
original = Exact('identity')
class Case(unittest.TestCase):
    def test_original(self): raise original
class Result(unittest.TestResult):
    def addError(self, test, err):
        assert err[1] is original
        print('EXACT ERROR IDENTITY')
        super().addError(test, err)
    def startTest(self, test):
        print('ORIGINAL START')
        super().startTest(test)
    def stopTest(self, test):
        print('ORIGINAL STOP')
        super().stopTest(test)
case = Case('test_original')
result = Result()
assert case.run(result) is result
assert result.testsRun == 1 and len(result.errors) == 1
class EscapingResult(Result):
    def addError(self, test, err): raise original
try: Case('test_original').run(EscapingResult())
except Exact as caught:
    assert caught is original
    print('EXACT ESCAPING IDENTITY')
else: raise AssertionError('original exception lost')
''')
        outputs = []
        for enabled in ('false', 'true'):
            env = dict(os.environ, PYTHONPATH=str(HOOK), BC_TIMING_PYTHON=enabled,
                       BC_TIMING_FILE=str(self.root / 'callback-contract.jsonl'), PYTHONDONTWRITEBYTECODE='1')
            result = subprocess.run([sys.executable, '-B', str(script)], env=env,
                                    capture_output=True, timeout=10)
            self.assertEqual(0, result.returncode, result.stderr)
            outputs.append(result.stdout)
        self.assertEqual(outputs[0], outputs[1])
        self.assertEqual(2, outputs[1].count(b'ORIGINAL START'))
        self.assertEqual(2, outputs[1].count(b'ORIGINAL STOP'))

    def test_parallel_thread_callbacks_and_class_setup_failure(self):
        script = self.root / 'thread-contract.py'
        script.write_text('''import concurrent.futures, unittest
class Case(unittest.TestCase):
    def test_original(self): pass
class Broken(unittest.TestCase):
    @classmethod
    def setUpClass(cls): raise ValueError('class setup')
    def test_never(self): raise AssertionError('must not run')
def run(index):
    suite = unittest.TestSuite([Case('test_original')])
    return suite.run(unittest.TestResult()).wasSuccessful()
with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
    assert all(pool.map(run, range(20)))
result = unittest.TestSuite([Broken('test_never')]).run(unittest.TestResult())
assert len(result.errors) == 1
''')
        sink = self.root / 'thread-contract.jsonl'
        env = dict(os.environ, PYTHONPATH=str(HOOK), BC_TIMING_PYTHON='true',
                   BC_TIMING_FILE=str(sink), PYTHONDONTWRITEBYTECODE='1')
        result = subprocess.run([sys.executable, '-B', str(script)], env=env, capture_output=True, timeout=10)
        self.assertEqual(0, result.returncode, result.stderr)
        records = self.records(sink)
        tests = [r for r in records if r['scope'] == 'python_test' and r['event'] == 'terminal']
        self.assertEqual(20, len(tests))
        self.assertTrue(all(r['status'] == 'SUCCEEDED' for r in tests))
        suites = [r for r in records if r['scope'] == 'python_suite' and r['event'] == 'terminal']
        self.assertEqual(1, sum(r['status'] == 'FAILED' for r in suites))

    def deadline_fixture(self):
        script = self.root / 'deadline-fixture.py'
        script.write_text('''import os, signal, sys, time, types, unittest
sentinel = types.ModuleType('timing_report')
sys.modules['timing_report'] = sentinel
before_path, before_argv = list(sys.path), list(sys.argv)
mode = os.environ.get('DEADLINE_MODE', 'fast')
if mode == 'hanging_handler':
    def hang(*args):
        while True: time.sleep(.05)
    signal.signal(signal.SIGTERM, hang)
if mode in ('race', 'recovered'): signal.signal(signal.SIGTERM, lambda *args: None)
class Case(unittest.TestCase):
    def test_case(self):
        print('READY', flush=True)
        if mode == 'freeze':
            open(os.environ['MUTATE_BASELINE'], 'w').write('{invalid}\\n')
        time.sleep(.08 if mode == 'fast' else .4 if mode in ('race', 'recovered') else 10)
        if mode == 'race': self.fail('original failure after timeout')
result = unittest.TextTestRunner(verbosity=0).run(unittest.TestSuite([Case('test_case')]))
assert sys.modules['timing_report'] is sentinel
assert sys.path == before_path and sys.argv == before_argv
raise SystemExit(0 if result.wasSuccessful() else 1)
''')
        return script

    def deadline_run(self, script, label, mode='fast', baseline=None, **overrides):
        sink = self.root / (label + '.jsonl')
        env = dict(os.environ, PYTHONPATH=str(HOOK), PYTHONDONTWRITEBYTECODE='1',
                   BC_TIMING_PYTHON='true', BC_TIMING_FILE=str(sink), DEADLINE_MODE=mode,
                   BC_TIMING_REVISION='controlled', BC_TIMING_INPUT_ID='a'*64,
                   BC_TIMING_PROFILE='inert-case', BC_TIMING_ENVIRONMENT_ID='local', BC_TIMING_CACHE_ID='none',
                   BC_TIMING_BASELINES=json.dumps([str(baseline)]) if baseline else '')
        env.update(overrides)
        started = time.monotonic()
        result = subprocess.run([sys.executable, '-B', str(script)], env=env, capture_output=True, timeout=6)
        (self.root / (label + '.stderr')).write_bytes(result.stderr)
        return result, sink, time.monotonic()-started

    def test_real_measured_case_and_suite_deadlines_and_hanging_signal_handler(self):
        script = self.deadline_fixture()
        result, raw, _ = self.deadline_run(script, 'measured')
        self.assertEqual(0, result.returncode, result.stderr)
        baseline = self.records(raw)
        for scope in ('python_test', 'python_suite'):
            selected = [row for row in baseline if row['scope'] == scope]
            duration = next(row['duration_ns'] for row in selected if row['event'] == 'terminal')
            source = self.root / (scope + '-baseline.jsonl')
            source.write_text(''.join(json.dumps(row)+'\n' for row in selected))
            result, sink, elapsed = self.deadline_run(script, scope+'-timeout', 'slow', source)
            self.assertEqual(-signal.SIGTERM, result.returncode, result.stderr)
            terminal = next(row for row in self.records(sink) if row['scope'] == scope and row['event'] == 'terminal')
            self.assertEqual('TIMED_OUT', terminal['status'])
            self.assertEqual(duration*2, terminal['configured_timeout_ns'])
            self.assertEqual(duration, terminal['expected_runtime_ns'])
            self.assertEqual('EXACT_2X_EMPIRICAL_MEDIAN', terminal['timeout_policy'])
            self.assertLess(elapsed, 2)
            health = self.records(Path(str(sink)+'.health.jsonl'))
            self.assertEqual(2_000_000_000, health[0]['cleanup_budget_ns'])
        source = self.root / 'python_test-baseline.jsonl'
        result, sink, elapsed = self.deadline_run(script, 'hanging-handler', 'hanging_handler', source)
        self.assertEqual(124, result.returncode, result.stderr)
        self.assertGreaterEqual(elapsed, 2)
        self.assertLess(elapsed, 4)
        self.assertIn('cleanup_exhausted', [r['event'] for r in self.records(Path(str(sink)+'.health.jsonl'))])
        print('retained real Python deadline evidence:', self.root)

    def test_unknown_invalid_mismatch_and_frozen_catalog(self):
        script = self.deadline_fixture()
        result, raw, _ = self.deadline_run(script, 'freeze-measured')
        self.assertEqual(0, result.returncode, result.stderr)
        for label, source, extra in [('mismatch', raw, {'BC_TIMING_CACHE_ID': 'changed'}),
                                     ('unknown', raw, {'BC_TIMING_INPUT_ID': 'UNKNOWN'})]:
            result, sink, _ = self.deadline_run(script, label, baseline=source, **extra)
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertTrue(all(r['configured_timeout_ns'] is None for r in self.records(sink)))
        invalid = self.root / 'invalid-baseline.jsonl'
        invalid.write_text('{invalid}\n')
        result, sink, _ = self.deadline_run(script, 'invalid', baseline=invalid)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertTrue(all(r['configured_timeout_ns'] is None for r in self.records(sink)))
        result, sink, _ = self.deadline_run(script, 'frozen', 'freeze', raw, MUTATE_BASELINE=str(raw))
        self.assertEqual(-signal.SIGTERM, result.returncode, result.stderr)
        starts = [r for r in self.records(sink) if r['event'] == 'start']
        self.assertTrue(all(r['timeout_policy'] == 'EXACT_2X_EMPIRICAL_MEDIAN' for r in starts))
        self.assertEqual('{invalid}\n', raw.read_text())

    def test_non_success_outcomes_are_not_catalog_baselines(self):
        metadata = {'BC_TIMING_' + key: value for key, value in
                    [('REVISION', 'controlled'), ('INPUT_ID', 'a'*64), ('PROFILE', 'inert'),
                     ('ENVIRONMENT_ID', 'local'), ('CACHE_ID', 'none')]}
        for mode in ('pass', 'fail', 'error', 'skip', 'unexpected', 'cancel'):
            result, raw = self.run_fixture(mode, extra=metadata)
            env = dict(os.environ, BC_TIMING_BASELINES=json.dumps([str(raw)]))
            reduced = subprocess.run([sys.executable, '-B', str(HOOK.parent/'timing_budget.py'), 'catalog'],
                                     env=env, capture_output=True, timeout=5)
            self.assertEqual(0, reduced.returncode, reduced.stderr)
            catalog = json.loads(reduced.stdout)
            self.assertEqual('VALID', catalog['status'])
            for group in catalog['groups']:
                self.assertEqual('python_test', group['identity']['scope'])
                self.assertEqual('test_case', group['identity']['test_name'])
                self.assertIn(mode, ('pass', 'unexpected'))

    def test_timeout_teardown_race_preserves_original_failure_and_health(self):
        script = self.deadline_fixture()
        result, raw, _ = self.deadline_run(script, 'race-measured')
        self.assertEqual(0, result.returncode, result.stderr)
        selected = [r for r in self.records(raw) if r['scope'] == 'python_test']
        source = self.root / 'race-case-baseline.jsonl'
        source.write_text(''.join(json.dumps(row)+'\n' for row in selected))
        result, sink, _ = self.deadline_run(script, 'race', 'race', source)
        self.assertEqual(1, result.returncode)
        self.assertIn(b'original failure after timeout', result.stderr)
        self.assertNotIn(b'TimingObserverError', result.stderr)
        health = self.records(Path(str(sink)+'.health.jsonl'))
        self.assertIn('FAILED', [row.get('callback_status') for row in health])
        case = [r for r in self.records(sink) if r['scope'] == 'python_test']
        self.assertEqual(['start', 'terminal'], [r['event'] for r in case])
        self.assertEqual('TIMED_OUT', case[-1]['status'])
        recovered, recovered_sink, _ = self.deadline_run(script, 'recovered', 'recovered', source)
        self.assertNotEqual(0, recovered.returncode)
        self.assertIn(b'TimingObserverError', recovered.stderr)
        self.assertIn('TIMED_OUT', [r['status'] for r in self.records(recovered_sink)])

    def test_concurrent_process_append(self):
        sink = self.root / 'concurrent.jsonl'
        env = dict(os.environ, PYTHONPATH=str(HOOK), BC_TIMING_PYTHON='true',
                   BC_TIMING_FILE=str(sink), FIXTURE_MODE='pass', PYTHONDONTWRITEBYTECODE='1')
        processes = [subprocess.Popen([sys.executable, '-B', str(self.script)], env=env,
                                     stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL) for _ in range(6)]
        try:
            for process in processes:
                self.assertEqual(0, process.wait(timeout=10))
        finally:
            for process in processes:
                if process.poll() is None:
                    process.kill()
                    process.wait(timeout=5)
        records = self.records(sink)
        self.assertEqual(6 * 10, len(records))  # two suites plus three cases, start+terminal
        self.assertEqual(6, len({r['invocation_id'] for r in records}))


if __name__ == '__main__':
    unittest.main()

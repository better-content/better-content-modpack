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

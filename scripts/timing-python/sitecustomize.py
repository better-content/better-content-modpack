"""Explicit, test-only unittest timing observer loaded through PYTHONPATH.

No test discovery or argv rewriting. Exact comparable raw baselines select frozen
own-scope deadlines; unknown own caps remain null, never enclosing-process budgets.
POSIX lockf interoperates with the JVM timing writer; flock additionally serializes
other Python appenders. Only scalar identities are retained, never TestCase objects.
"""
import os

if os.environ.get('BC_TIMING_PYTHON') == 'true' and os.environ.get('BC_TIMING_FILE'):
    import atexit
    import fcntl
    import functools
    import hashlib
    import importlib.util
    import json
    import signal
    from pathlib import Path
    import threading
    import time
    import unittest
    import uuid
    from datetime import datetime, timezone
    from decimal import Decimal

    class TimingObserverError(RuntimeError):
        pass

    class _Observer:
        def __init__(self):
            self.lock = threading.RLock()
            self.active = {}
            self.failure = None
            self.counts = {}
            self.invocation = str(uuid.uuid4())
            self.catalog = None
            self.budget_api = None
            self.wakeup = threading.Event()
            self.watchdog_started = False
            self.expired = {}  # scalar key -> cleanup deadline, no framework objects
            self.timed_out = False
            self.context = {key.lower(): os.environ.get('BC_TIMING_' + key) or
                            (os.environ.get('BC_TEST_RUN_ID') if key == 'RUN_ID' else None) or 'UNKNOWN'
                            for key in ('RUN_ID', 'REVISION', 'INPUT_ID', 'PROFILE', 'ENVIRONMENT_ID', 'CACHE_ID')}

        def safe(self, function, *args, **kwargs):
            try:
                with self.lock:
                    return function(*args, **kwargs)
            except BaseException as error:
                # Diagnostics cannot replace framework/user exceptions, including BaseException.
                if self.failure is None:
                    self.failure = type(error).__name__

        def append(self, fields):
            path = Path(os.environ['BC_TIMING_FILE'])
            if not path.is_absolute() or '..' in path.parts:
                raise ValueError('timing sink must be absolute without traversal')
            if any(part.is_symlink() for part in (path, *path.parents)):
                raise ValueError('timing sink ancestry is a symlink')
            path.parent.mkdir(parents=True, exist_ok=True)
            payload = (json.dumps(dict(fields, observed_at=datetime.now(timezone.utc).isoformat()),
                                  separators=(',', ':')) + '\n').encode()
            fd = os.open(path, os.O_CREAT | os.O_WRONLY | os.O_NOFOLLOW, 0o600)
            try:
                fcntl.flock(fd, fcntl.LOCK_EX)
                fcntl.lockf(fd, fcntl.LOCK_EX)
                os.lseek(fd, 0, os.SEEK_END)
                view = memoryview(payload)
                while view:
                    written = os.write(fd, view)
                    if not written:
                        raise OSError('timing append made no progress')
                    view = view[written:]
                os.fsync(fd)
            finally:
                os.close(fd)

        def load_catalog(self):
            if self.catalog is None:
                module_path = Path(__file__).resolve().parent.parent / 'timing_budget.py'
                spec = importlib.util.spec_from_file_location('_bc_unittest_private_budget', module_path)
                api = importlib.util.module_from_spec(spec)
                spec.loader.exec_module(api)
                self.budget_api = api
                self.catalog = api.catalog()  # one frozen raw snapshot per activated process

        def health(self, **detail):
            # Independent scalar diagnostic stream: never invent extra raw terminal events.
            path = Path(os.environ['BC_TIMING_FILE'] + '.health.jsonl')
            if any(part.is_symlink() for part in (path, *path.parents)):
                raise ValueError('health sink ancestry is a symlink')
            payload = (json.dumps(dict(schema='bc.python_timing_health.v1',
                invocation_id=self.invocation, **detail)) + '\n').encode()
            fd = os.open(path, os.O_CREAT | os.O_WRONLY | os.O_APPEND | os.O_NOFOLLOW, 0o600)
            try:
                fcntl.lockf(fd, fcntl.LOCK_EX)
                os.write(fd, payload)
                os.fsync(fd)
            finally:
                os.close(fd)

        def watch(self):
            while True:
                self.wakeup.clear()
                notify = False
                with self.lock:
                    now = time.monotonic_ns()
                    for key, entry in list(self.active.items()):
                        fields, started, outcome = entry
                        cap = fields.get('configured_timeout_ns')
                        if cap is not None and now - started >= cap:
                            self.active.pop(key)
                            self.timed_out = True
                            self.failure = self.failure or 'TIMED_OUT'
                            self.expired[key] = now + 2_000_000_000
                            self.safe(self.append, dict(fields, event='terminal', status='TIMED_OUT',
                                duration_ns=max(0, now-started), callback_status_at_expiry=outcome))
                            self.safe(self.health, event='timeout_intent', operation_id=fields['operation_id'],
                                      scope=fields['scope'], effective_timeout_ns=cap, cleanup_budget_ns=2_000_000_000,
                                      callback_status_at_expiry=outcome)
                            notify = True
                    if any(now >= end for end in self.expired.values()):
                        self.safe(self.health, event='cleanup_exhausted', exit_code=124)
                        os._exit(124)
                    ends = [entry[1] + entry[0]['configured_timeout_ns'] for entry in self.active.values()
                            if entry[0].get('configured_timeout_ns') is not None] + list(self.expired.values())
                    delay = min(.05, max(0, min(ends)-now)/1_000_000_000) if ends else .05
                if notify:
                    # Existing handlers remain exactly installed; target only our own process.
                    os.kill(os.getpid(), signal.SIGTERM)
                self.wakeup.wait(delay)

        def begin(self, key, scope, detail):
            budget = os.environ.get('BC_TIMING_TIMEOUT_SECONDS')
            enclosing = None
            if budget:
                value = Decimal(budget) * 1_000_000_000
                if not value.is_finite() or value <= 0 or value != value.to_integral_value():
                    raise ValueError('invalid enclosing timeout')
                enclosing = int(value)
            fields = dict(self.context, schema='bc.timing.v1', operation_id=str(uuid.uuid4()),
                          invocation_id=self.invocation, scope=scope, units='nanoseconds', clock='monotonic',
                          duration_boundary=('unittest_startTest_to_stopTest_callbacks' if scope == 'python_test'
                                             else 'unittest_TestSuite_run_invocation'),
                          configured_timeout_ns=None, timeout_scope=scope,
                          enclosing_process_timeout_ns=enclosing, expected_runtime_ns=None,
                          timeout_policy='UNKNOWN_UNTIL_COMPARABLE_SUCCESS_MEASURED', **detail)
            self.load_catalog()
            decision = self.budget_api.resolve_catalog(fields, 1, self.catalog)
            known = decision['timeout_policy'] == self.budget_api.POLICY_EXACT
            fields.update(decision)
            fields['configured_timeout_ns'] = decision['effective_timeout_ns'] if known else None
            fields['effective_timeout_ns'] = fields['configured_timeout_ns']
            fields.pop('legacy_safety_cap_ns', None)  # Python cases/suites have no invented fallback
            self.active[key] = [fields, time.monotonic_ns(), 'UNKNOWN']
            self.append(dict(fields, event='start', status='RUNNING', duration_ns=None))
            if known and not self.watchdog_started:
                self.watchdog_started = True
                threading.Thread(target=self.watch, name='bc-unittest-deadlines', daemon=True).start()
            self.wakeup.set()

        def outcome(self, key, status):
            if key in self.expired:
                self.health(event='callback_after_timeout', callback_status=status)
            entry = self.active.get(key)
            if entry is not None:
                # A failing subtest must not be overwritten by a later successful subtest.
                if entry[2] not in ('FAILED', 'ERROR', 'UNEXPECTED_SUCCESS'):
                    entry[2] = status

        def finish(self, key, status=None):
            if self.expired.pop(key, None) is not None:
                self.health(event='original_boundary_after_timeout', original_status=status)
            self.wakeup.set()
            entry = self.active.pop(key, None)
            if entry is None:
                return
            fields, started, outcome = entry
            outcome = status or outcome
            if self.timed_out and fields['scope'] == 'python_suite' and outcome == 'SUCCEEDED':
                outcome = 'FAILED'
            if fields['scope'] == 'python_test':
                counts = self.counts.setdefault(threading.get_ident(), {})
                counts[outcome] = counts.get(outcome, 0) + 1
            self.append(dict(fields, event='terminal', status=outcome,
                             duration_ns=max(0, time.monotonic_ns() - started)))

        def escaped(self, key, exception_type):
            if key in self.expired:
                self.health(event='escaping_callback_after_timeout', exception_type=exception_type)

        def check(self):
            if self.failure is not None:
                raise TimingObserverError('Python timing observer failed: ' + self.failure)

    _observer = _Observer()

    def _test_identity(test):
        cls = type(test)
        return dict(python_module=cls.__module__, python_class=cls.__qualname__,
                    test_name=getattr(test, '_testMethodName', 'UNKNOWN'))

    def _patch_callback(name, observe):
        original = getattr(unittest.TestResult, name)
        @functools.wraps(original)
        def callback(self, *args, **kwargs):
            # Original invocation and exceptions are exact; observer runs only afterwards.
            value = original(self, *args, **kwargs)
            _observer.safe(observe, *args, **kwargs)
            return value
        setattr(unittest.TestResult, name, callback)

    _patch_callback('startTest', lambda test: _observer.begin(('test', id(test)), 'python_test', _test_identity(test)))
    def _stop_test(test):
        key = ('test', id(test))
        entry = _observer.active.get(key)
        # unittest calls stopTest in finally before a KeyboardInterrupt escapes run.
        # Defer unknown outcomes until the run boundary can classify that exception.
        if entry is not None and entry[2] != 'UNKNOWN':
            _observer.finish(key)
    _patch_callback('stopTest', _stop_test)
    for _name, _status in [('addSuccess', 'SUCCEEDED'), ('addFailure', 'FAILED'), ('addError', 'ERROR'),
                           ('addSkip', 'SKIPPED'), ('addExpectedFailure', 'EXPECTED_FAILURE'),
                           ('addUnexpectedSuccess', 'UNEXPECTED_SUCCESS')]:
        _patch_callback(_name, lambda test, *args, _status=_status, **kwargs:
                        _observer.outcome(('test', id(test)), _status))
    _patch_callback('addSubTest', lambda test, subtest, err:
                    _observer.outcome(('test', id(test)), 'FAILED' if err else 'SUCCEEDED'))

    _case_run = unittest.TestCase.run
    @functools.wraps(_case_run)
    def _run_case(self, *args, **kwargs):
        try:
            value = _case_run(self, *args, **kwargs)
        except BaseException as error:
            _observer.safe(_observer.escaped, ('test', id(self)), type(error).__name__)
            _observer.safe(_observer.finish, ('test', id(self)),
                           'CANCELLED' if isinstance(error, (KeyboardInterrupt, SystemExit)) else 'INTERRUPTED')
            raise
        _observer.safe(_observer.finish, ('test', id(self)))
        return value
    unittest.TestCase.run = _run_case

    def _suite_identity(suite):
        members = []
        def visit(item):
            if isinstance(item, unittest.TestSuite):
                for child in item._tests:
                    visit(child)
            elif item is not None:
                identity = _test_identity(item)
                members.append('.'.join(identity.values()))
        visit(suite)
        return dict(suite_name=type(suite).__module__ + '.' + type(suite).__qualname__,
                    suite_members_sha256=hashlib.sha256(json.dumps(members, separators=(',', ':')).encode()).hexdigest())

    _suite_run = unittest.TestSuite.run
    @functools.wraps(_suite_run)
    def _run_suite(self, *args, **kwargs):
        key = ('suite', id(self))
        before = dict(_observer.counts.get(threading.get_ident(), {}))
        result = args[0] if args else kwargs.get('result')
        def failure_count():
            return sum(len(getattr(result, name, ())) for name in ('failures', 'errors', 'unexpectedSuccesses'))
        failures_before = _observer.safe(failure_count)
        def begin():
            _observer.begin(key, 'python_suite', _suite_identity(self))
        _observer.safe(begin)
        try:
            value = _suite_run(self, *args, **kwargs)
        except BaseException as error:
            _observer.safe(_observer.escaped, key, type(error).__name__)
            _observer.safe(_observer.finish, key,
                           'CANCELLED' if isinstance(error, (KeyboardInterrupt, SystemExit)) else 'INTERRUPTED')
            raise
        def finish():
            counts = _observer.counts.get(threading.get_ident(), {})
            changed = {status for status, count in counts.items() if count > before.get(status, 0)}
            failed = changed.intersection({'FAILED', 'ERROR', 'UNEXPECTED_SUCCESS', 'CANCELLED', 'INTERRUPTED', 'UNKNOWN'})
            result_failed = failures_before is not None and failure_count() > failures_before
            status = 'FAILED' if failed or result_failed else (
                'SUCCEEDED' if changed == {'SUCCEEDED'} else 'SKIPPED')
            _observer.finish(key, status)
        _observer.safe(finish)
        return value
    unittest.TestSuite.run = _run_suite

    _runner_run = unittest.TextTestRunner.run
    @functools.wraps(_runner_run)
    def _run_runner(self, *args, **kwargs):
        result = _runner_run(self, *args, **kwargs)
        # Failure evidence remains secondary to the original result. On success the
        # supported runner must not return acceptance with a broken diagnostic sink.
        if _observer.failure is not None and result.wasSuccessful():
            _observer.check()
        return result
    unittest.TextTestRunner.run = _run_runner

    def _finish_interrupted():
        for key in list(_observer.active):
            _observer.safe(_observer.finish, key, 'INTERRUPTED')
    atexit.register(_finish_interrupted)

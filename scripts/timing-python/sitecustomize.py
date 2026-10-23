"""Explicit, test-only unittest timing observer loaded through PYTHONPATH.

No test discovery, argv rewriting, deadline selection, or runtime instrumentation.
POSIX lockf interoperates with the JVM timing writer; flock additionally serializes
other Python appenders. Only scalar identities are retained, never TestCase objects.
"""
import os

if os.environ.get('BC_TIMING_PYTHON') == 'true' and os.environ.get('BC_TIMING_FILE'):
    import atexit
    import fcntl
    import functools
    import hashlib
    import json
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
            self.context = {key.lower(): os.environ.get('BC_TIMING_' + key) or
                            (os.environ.get('BC_TEST_RUN_ID') if key == 'RUN_ID' else None) or 'UNKNOWN'
                            for key in ('RUN_ID', 'REVISION', 'INPUT_ID', 'PROFILE', 'ENVIRONMENT_ID', 'CACHE_ID')}

        def safe(self, function, *args):
            try:
                with self.lock:
                    return function(*args)
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
            self.active[key] = [fields, time.monotonic_ns(), 'UNKNOWN']
            self.append(dict(fields, event='start', status='RUNNING', duration_ns=None))

        def outcome(self, key, status):
            entry = self.active.get(key)
            if entry is not None:
                # A failing subtest must not be overwritten by a later successful subtest.
                if entry[2] not in ('FAILED', 'ERROR', 'UNEXPECTED_SUCCESS'):
                    entry[2] = status

        def finish(self, key, status=None):
            entry = self.active.pop(key, None)
            if entry is None:
                return
            fields, started, outcome = entry
            outcome = status or outcome
            if fields['scope'] == 'python_test':
                counts = self.counts.setdefault(threading.get_ident(), {})
                counts[outcome] = counts.get(outcome, 0) + 1
            self.append(dict(fields, event='terminal', status=outcome,
                             duration_ns=max(0, time.monotonic_ns() - started)))

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
            _observer.safe(_observer.finish, key,
                           'CANCELLED' if isinstance(error, (KeyboardInterrupt, SystemExit)) else 'INTERRUPTED')
            raise
        def finish():
            counts = _observer.counts.get(threading.get_ident(), {})
            changed = {status for status, count in counts.items() if count > before.get(status, 0)}
            failed = changed.intersection({'FAILED', 'ERROR', 'UNEXPECTED_SUCCESS', 'CANCELLED', 'INTERRUPTED', 'UNKNOWN'})
            result_failed = failures_before is not None and failure_count() > failures_before
            status = 'FAILED' if failed or result_failed else (
                'SUCCEEDED' if changed.intersection({'SUCCEEDED', 'EXPECTED_FAILURE'}) else 'SKIPPED')
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

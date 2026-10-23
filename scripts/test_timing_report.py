#!/usr/bin/env python3
"""Focused inert reducer checks. ALL records here are synthetic, never real baselines.
Evidence is retained under the assigned output root; no Gradle/native/cache writes.
"""
import concurrent.futures
import copy
import json
import multiprocessing
from pathlib import Path
import subprocess
import sys
import unittest
import uuid

import timing_report as timing

ROOT = Path('/home/dev/workspace_artifacts/retool-timing-report-20261023')


def concurrent_report(source, output):
    return timing.write_report([Path(source)], Path(output))['status']


class TimingReportTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.evidence = ROOT / ('synthetic-' + str(uuid.uuid4()))
        cls.evidence.mkdir(parents=True)
        print('SYNTHETIC retained evidence: ' + str(cls.evidence), flush=True)

    def setUp(self):
        self.root = self.evidence / self.id().split('.')[-1]
        self.root.mkdir()
        self.source = self.root / 'synthetic-events.jsonl'
        self.output = self.root / 'synthetic-report.json'

    def pair(self, duration=10, status='SUCCEEDED', **changes):
        row = dict(schema='bc.timing.v1', operation_id=str(uuid.uuid4()), scope='process',
                   units='nanoseconds', clock='monotonic', duration_boundary='supervision_including_cleanup',
                   run_id=str(uuid.uuid4()), revision='synthetic-revision', input_id='synthetic-input',
                   profile='SYNTHETIC-NOT-RUNTIME', environment_id='synthetic-host', cache_id='synthetic-cold',
                   cwd='/synthetic', command_sha256='a' * 64, configured_timeout_ns=999,
                   enclosing_process_timeout_ns=None, expected_runtime_ns=None, timeout_scope='process',
                   observed_at='2026-10-23T00:00:00Z')
        row.update(changes)
        return [dict(row, event='start', status='RUNNING', duration_ns=None),
                dict(row, event='terminal', status=status, duration_ns=duration)]

    def report(self, rows):
        self.source.write_text(''.join(json.dumps(row) + '\n' for row in rows))
        return timing.write_report([self.source], self.output)

    def test_bound_logical_process_groups_across_private_paths(self):
        report = self.report(self.pair(10, input_id='a'*64, process_name='source:fixture:default', cwd='/private/a') +
                             self.pair(20, input_id='a'*64, process_name='source:fixture:default',
                                       cwd='/private/b', command_sha256='b'*64))
        group, = report['groups']
        self.assertEqual(2, group['successful_sample_count'])
        self.assertEqual(15, group['expected_runtime_ns'])
        self.assertEqual(30, group['recommended_timeout_ns'])
        self.assertEqual('COMPLETE', report['status'])

    def test_logical_process_changed_input_remains_separate(self):
        report = self.report(self.pair(input_id='a'*64, process_name='source:fixture:default') +
                             self.pair(input_id='b'*64, process_name='source:fixture:default'))
        self.assertEqual(2, len(report['groups']))

    def test_unbound_logical_process_keeps_raw_identity_and_unknown_expectation(self):
        report = self.report(self.pair(input_id='UNKNOWN', process_name='source:fixture:default', cwd='/private/a') +
                             self.pair(input_id='UNKNOWN', process_name='source:fixture:default', cwd='/private/b'))
        self.assertEqual(2, len(report['groups']))
        self.assertTrue(all(group['expected_runtime_ns'] is None for group in report['groups']))

    def test_logical_selector_cannot_change_between_start_and_terminal(self):
        pair = self.pair(input_id='a'*64, process_name='source:fixture:default')
        pair[-1]['process_name'] = 'source:other:default'
        self.assertEqual('INVALID', self.report(pair)['status'])

    def test_success_median_provenance_and_separate_actual_caps(self):
        report = self.report(self.pair(10) + self.pair(20))
        self.assertEqual('COMPLETE', report['status'])
        self.assertEqual('NOT_EVALUATED', report['acceptance'])
        group, = report['groups']
        self.assertEqual(2, group['successful_sample_count'])
        self.assertEqual(15, group['expected_runtime_ns'])
        self.assertEqual(30, group['recommended_timeout_ns'])
        self.assertEqual(2, len({s['run_id'] for s in group['samples']}))
        self.assertEqual(999, group['observations'][0]['configured_timeout_ns'])
        self.assertEqual('DOES_NOT_MATCH_2X_EMPIRICAL_MEDIAN',
                         group['observations'][0]['configured_cap_vs_recommendation'])
        self.assertTrue(group['samples'][0]['provenance'])

    def test_failure_cancel_timeout_nonexecution_never_baseline(self):
        rows = self.pair(10)
        for status in ('FAILED', 'CANCELLED', 'INTERRUPTED', 'TIMED_OUT', 'SKIPPED', 'UP_TO_DATE', 'NO_SOURCE'):
            rows += self.pair(100000, status)
        report = self.report(rows)
        group, = report['groups']
        self.assertEqual(1, group['successful_sample_count'])
        self.assertEqual(10, group['expected_runtime_ns'])
        self.assertEqual(1, report['counts']['failure'])
        self.assertEqual(2, report['counts']['cancelled'])
        self.assertEqual(3, report['counts']['censored_total'])
        self.assertEqual(3, report['counts']['nonexecution'])

    def test_missing_start_terminal_and_measurement(self):
        report = self.report(self.pair()[:1] + self.pair()[1:] + self.pair(None))
        self.assertEqual('INCOMPLETE', report['status'])
        self.assertEqual(1, report['counts']['started_only'])
        self.assertEqual(1, report['counts']['missing_start'])
        self.assertEqual(0, report['groups'][0]['successful_sample_count'])
        self.assertIsNone(report['groups'][0]['recommended_timeout_ns'])

    def test_missing_identity_unknown_no_timeout_halving(self):
        for field in (*timing.CONTEXT, 'command_sha256', 'duration_boundary'):
            with self.subTest(field=field):
                report = self.report(self.pair(**{field: 'UNKNOWN'}))
                self.assertEqual('UNKNOWN', report['groups'][0]['baseline_status'])
                self.assertIsNone(report['groups'][0]['expected_runtime_ns'])

    def test_identity_differences_separate(self):
        rows = self.pair()
        for field in (*timing.CONTEXT, 'command_sha256', 'cwd', 'duration_boundary'):
            rows += self.pair(**{field: 'different'})
        report = self.report(rows)
        self.assertEqual(9, len(report['groups']))
        self.assertTrue(all(g['successful_sample_count'] == 1 for g in report['groups']))

    def test_junit_gradle_and_native_identity(self):
        rows = []
        for scope, status in [('junit_test', 'SUCCESS'), ('junit_suite', 'SUCCESS'),
                              ('gradle_task', 'SUCCEEDED'), ('native_test', 'SUCCEEDED')]:
            rows += self.pair(12, status, scope=scope, timeout_scope=scope, build_root='/synthetic/build',
                              task_path=':test', class_name='SyntheticCase', name='syntheticMethod',
                              parent_name='SyntheticSuite', structure_name='synthetic:structure',
                              result_semantics='native_attempt_not_reporter_verdict', required=True, flaky=False,
                              max_attempts=1, required_successes=1, configured_timeout_ns=None,
                              configured_timeout_ticks=100, native_duration_ms=0)
        report = self.report(rows)
        self.assertEqual(4, len(report['groups']))
        self.assertTrue(all(g['expected_runtime_ns'] == 12 for g in report['groups']))
        native = next(g for g in report['groups'] if g['identity']['scope'] == 'native_test')
        self.assertEqual(24, native['recommended_timeout_ns'])
        self.assertEqual(100, native['observations'][0]['configured_timeout_ticks'])
        self.assertEqual('UNKNOWN', native['observations'][0]['configured_cap_vs_recommendation'])

    def test_python_scopes_outcomes_and_ordered_suite_identity(self):
        detail = dict(scope='python_test', timeout_scope='python_test', python_module='synthetic_module',
                      python_class='SyntheticCase', test_name='test_synthetic',
                      duration_boundary='unittest_startTest_to_stopTest_callbacks')
        rows = self.pair(10, **detail)
        for status in ('ERROR', 'EXPECTED_FAILURE', 'UNEXPECTED_SUCCESS', 'UNKNOWN', 'SKIPPED'):
            rows += self.pair(9999, status, **detail)
        suite = dict(scope='python_suite', timeout_scope='python_suite', suite_name='unittest.suite.TestSuite',
                     suite_members_sha256='a' * 64, duration_boundary='unittest_TestSuite_run_invocation')
        rows += self.pair(20, **suite)
        rows += self.pair(30, **dict(suite, suite_members_sha256='b' * 64))
        report = self.report(rows)
        self.assertEqual('INCOMPLETE', report['status'])
        self.assertEqual(3, len(report['groups']))
        test = next(g for g in report['groups'] if g['identity']['scope'] == 'python_test')
        self.assertEqual(1, test['successful_sample_count'])
        self.assertEqual(10, test['expected_runtime_ns'])
        self.assertEqual(2, test['counts']['failure'])
        self.assertEqual(1, test['counts']['expected_failure'])
        self.assertEqual(1, test['counts']['unknown'])
        missing = self.report(self.pair(20, **dict(suite, suite_members_sha256='UNKNOWN')))
        self.assertEqual('UNKNOWN', missing['groups'][0]['baseline_status'])

    def test_native_boundary_alias(self):
        rows = self.pair()
        for row in rows:
            row['measurement_boundary'] = row.pop('duration_boundary')
        self.assertEqual('MEASURED', self.report(rows)['groups'][0]['baseline_status'])

    def test_units_not_converted(self):
        report = self.report(self.pair() + self.pair(units='ticks'))
        self.assertEqual('INVALID', report['status'])
        self.assertTrue(all(g['expected_runtime_ns'] is None for g in report['groups']))

    def test_duplicate_events_and_input_paths_invalid(self):
        pair = self.pair()
        report = self.report(pair + pair)
        self.assertEqual('INVALID', report['status'])
        self.assertEqual([], report['groups'])
        report = timing.write_report([self.source, self.source], self.output)
        self.assertEqual('INVALID', report['status'])

    def test_malformed_duplicate_keys_truncated_blank_and_nonfinite(self):
        for bad in (b'{bad}\n', b'{"schema":"bc.timing.v1","schema":"bc.timing.v1"}\n',
                    b'\n', b'{"x": NaN}\n', b'\xff\n'):
            self.source.write_bytes(bad)
            self.assertEqual('INVALID', timing.write_report([self.source], self.output)['status'])
        rows = self.pair()
        self.source.write_text('\n'.join(json.dumps(row) for row in rows))
        self.assertEqual('INVALID', timing.write_report([self.source], self.output)['status'])

    def test_mismatch_and_negative_measurement_invalid(self):
        rows = self.pair()
        rows[1]['cache_id'] = 'other'
        self.assertEqual('INVALID', self.report(rows)['status'])
        self.assertEqual('INVALID', self.report(self.pair(-1))['status'])
        self.assertEqual('INVALID', self.report(self.pair(True))['status'])

    def test_empty_missing_file_and_size_bounds(self):
        self.assertEqual('INCOMPLETE', self.report([])['status'])
        self.assertEqual('INVALID', timing.write_report([self.root / 'absent'], self.output)['status'])
        self.source.write_bytes(b'x' * (timing.MAX_LINE + 1) + b'\n')
        self.assertEqual('INVALID', timing.write_report([self.source], self.output)['status'])
        self.assertEqual('INVALID', timing.write_report([], self.output)['status'])
        self.assertEqual('INVALID', timing.write_report([self.source] * 65, self.output)['status'])

    def test_output_input_and_symlink_safety(self):
        self.report(self.pair())
        with self.assertRaises(ValueError):
            timing.write_report([self.source], self.source)
        link = self.root / 'link'
        link.symlink_to(self.source)
        self.assertEqual('INVALID', timing.write_report([link], self.output)['status'])
        with self.assertRaises(ValueError):
            timing.write_report([self.source], link)

    def test_concurrent_reports_threads_and_processes(self):
        self.report(self.pair(11) + self.pair(12))
        with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
            results = list(pool.map(lambda _: concurrent_report(self.source, self.output), range(8)))
        self.assertEqual(['COMPLETE'] * 8, results)
        with concurrent.futures.ProcessPoolExecutor(max_workers=3,
                mp_context=multiprocessing.get_context('spawn')) as pool:
            futures = [pool.submit(concurrent_report, self.source, self.output) for _ in range(6)]
            self.assertEqual(['COMPLETE'] * 6, [f.result(timeout=30) for f in futures])
        group, = json.loads(self.output.read_text())['groups']
        self.assertEqual(11.5, group['expected_runtime_ns'])
        self.assertEqual(23, group['recommended_timeout_ns'])
        self.assertEqual(2, group['successful_sample_count'])

    def test_cli(self):
        self.report(self.pair())
        result = subprocess.run([sys.executable, '-B', str(Path(timing.__file__)), '--output',
                                 str(self.output), str(self.source)], capture_output=True, timeout=20)
        self.assertEqual(0, result.returncode, result.stderr)
        self.source.write_text(json.dumps(self.pair()[0]) + '\n')
        result = subprocess.run([sys.executable, '-B', str(Path(timing.__file__)), '--output',
                                 str(self.output), str(self.source)], capture_output=True, timeout=20)
        self.assertEqual(2, result.returncode, result.stderr)
        result = subprocess.run([sys.executable, '-B', str(Path(timing.__file__)), '--allow-incomplete',
                                 '--output', str(self.output), str(self.source)], capture_output=True, timeout=20)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual('INCOMPLETE', json.loads(result.stdout)['status'])
        self.assertEqual('NOT_EVALUATED', json.loads(result.stdout)['acceptance'])
        self.source.write_text('{broken}\n')
        result = subprocess.run([sys.executable, '-B', str(Path(timing.__file__)), '--allow-incomplete', '--output',
                                 str(self.output), str(self.source)], capture_output=True, timeout=20)
        self.assertEqual(2, result.returncode, result.stderr)


if __name__ == '__main__':
    unittest.main(verbosity=2)

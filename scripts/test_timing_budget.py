#!/usr/bin/env python3
"""Inert selector + real controlled-process deadline evidence; retains every fixture."""
import json
import os
from pathlib import Path
import signal
import subprocess
import sys
import tempfile
import time
import unittest
import uuid
import timing_budget as budget

ROOT = Path(__file__).absolute().parent.parent


class TimingBudgetTests(unittest.TestCase):
    def setUp(self):
        base = Path.home() / '.tmp'
        base.mkdir(exist_ok=True)
        self.root = Path(tempfile.mkdtemp(prefix='timing-budget-', dir=base))
        print('retained timing-budget evidence:', self.root)
        self.raw = self.root / 'raw.jsonl'
        self.fields = dict(scope='process', units='nanoseconds', clock='monotonic',
                           duration_boundary='supervision_including_cleanup', revision='fixture', input_id='a'*64,
                           profile='controlled-process', environment_id='inert-local', cache_id='none',
                           process_name='controlled-fixture', cwd=str(self.root), command_sha256='b'*64)

    def rows(self, durations, status='SUCCEEDED'):
        rows = []
        for duration in durations:
            fields = dict(self.fields, schema='bc.timing.v1', operation_id=str(uuid.uuid4()),
                          observed_at='2026-10-23T00:00:00Z', configured_timeout_ns=5_000_000_000,
                          timeout_scope='process')
            rows += [dict(fields, event='start', status='RUNNING', duration_ns=None),
                     dict(fields, event='terminal', status=status, duration_ns=duration)]
        self.raw.write_text(''.join(json.dumps(row)+'\n' for row in rows))
        return rows

    def select(self, fields=None):
        return budget.resolve(fields or self.fields, 9_000_000_000, [self.raw])

    def test_exact_odd_nanosecond_median_without_float_rounding(self):
        self.rows([2**53, 2**53+1])
        decision = self.select()
        self.assertEqual(2**54+1, decision['effective_timeout_ns'])
        self.assertEqual({'numerator': 2**54+1, 'denominator': 2}, decision['expected_runtime_rational'])
        self.assertIsNone(decision['expected_runtime_ns'])
        self.assertEqual(budget.POLICY_EXACT, decision['timeout_policy'])
        self.assertEqual(2, decision['successful_sample_count'])
        self.assertEqual(2, len(decision['baseline_samples']))
        self.assertEqual(str(self.raw), decision['baseline_sources'][0]['path'])

    def test_changed_context_identity_unknown_and_ticks_do_not_match(self):
        self.rows([100])
        for key in ('revision', 'input_id', 'profile', 'environment_id', 'cache_id', 'duration_boundary', 'process_name'):
            self.assertEqual(budget.POLICY_UNKNOWN, self.select(dict(self.fields, **{key: 'changed'}))['timeout_policy'])
        self.assertEqual(budget.POLICY_UNKNOWN, self.select(dict(self.fields, revision='UNKNOWN'))['timeout_policy'])
        self.assertEqual(budget.POLICY_UNKNOWN, self.select(dict(self.fields, units='ticks'))['timeout_policy'])

    def test_failure_censor_cancel_invalid_incomplete_and_overflow_are_unknown(self):
        for status in ('FAILED', 'TIMED_OUT', 'CANCELLED', 'INTERRUPTED'):
            self.rows([100], status)
            self.assertEqual(budget.POLICY_UNKNOWN, self.select()['timeout_policy'])
        rows = self.rows([100])
        self.raw.write_text(json.dumps(rows[0])+'\n')
        self.assertEqual(budget.POLICY_UNKNOWN, self.select()['timeout_policy'])
        self.raw.write_text('{broken}\n')
        self.assertEqual(budget.POLICY_UNKNOWN, self.select()['timeout_policy'])
        for duration in (0, 2**62, 2**63-1):
            self.rows([duration])
            self.assertEqual(9_000_000_000, self.select()['effective_timeout_ns'])
        with self.assertRaises(ValueError):
            budget.resolve(self.fields, 2**63, [self.raw])

    def test_ingress_absolute_bounded_raw_only_and_duplicate_rejection(self):
        self.rows([100])
        for paths in ([], ['relative.jsonl'], [self.raw]*65, [self.raw, self.raw]):
            self.assertEqual(budget.POLICY_UNKNOWN, budget.resolve(self.fields, 17, paths)['timeout_policy'])
        summary = self.root / 'summary.json'
        summary.write_text(json.dumps(dict(schema='bc.timing_report.v1', groups=[dict(expected_runtime_ns=3)]))+'\n')
        self.assertEqual(budget.POLICY_UNKNOWN, budget.resolve(self.fields, 17, [summary])['timeout_policy'])

    def test_catalog_selection_is_frozen_against_later_source_changes(self):
        self.rows([100])
        snapshot = budget.catalog([self.raw])
        selected = budget.resolve_catalog(self.fields, 17, snapshot)
        self.rows([999])
        self.assertEqual(200, selected['effective_timeout_ns'])
        self.assertEqual(200, budget.resolve_catalog(self.fields, 17, snapshot)['effective_timeout_ns'])
        self.assertEqual(1998, self.select()['effective_timeout_ns'])

    def test_real_measured_process_then_actual_exact_deadline_and_child_cap(self):
        program = self.root / 'controlled.py'
        program.write_text("import os,time\nprint(os.environ['BC_TIMING_TIMEOUT_NS'],flush=True)\ntime.sleep(float(os.environ.get('CONTROLLED_SLEEP','.15')))\n")
        env = dict(os.environ)
        env.pop('BC_TIMING_BASELINES', None)
        for key in ('REVISION', 'INPUT_ID', 'PROFILE', 'ENVIRONMENT_ID', 'CACHE_ID'):
            env['BC_TIMING_'+key] = self.fields[key.lower()]
        env['BC_TIMING_PROCESS_NAME'] = self.fields['process_name']
        def launch(name, slow=False, cancel=False):
            sink = self.root / (name+'.jsonl')
            current = dict(env, BC_TIMING_FILE=str(sink), CONTROLLED_SLEEP='10' if slow else '.15')
            command = [sys.executable, '-B', str(ROOT/'scripts/bounded_facade.py'), '--cwd', str(self.root),
                       '--budget-seconds', '5', '--log', str(self.root/(name+'.log')), '--', sys.executable, str(program)]
            started = time.monotonic()
            child = subprocess.Popen(command, env=current, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
            if cancel:
                end = started+3
                while not (self.root/(name+'.log')).exists() and time.monotonic() < end:
                    time.sleep(.005)
                child.send_signal(signal.SIGTERM)
            out, err = child.communicate(timeout=8)
            (self.root/(name+'.stderr')).write_bytes(err)
            rows = [json.loads(line) for line in sink.read_text().splitlines()]
            return child.returncode, rows, time.monotonic()-started
        code, baseline, _ = launch('baseline')
        self.assertEqual(0, code)
        self.assertEqual('SUCCEEDED', baseline[-1]['status'])
        duration = baseline[-1]['duration_ns']
        env['BC_TIMING_BASELINES'] = json.dumps([str(self.root/'baseline.jsonl')])
        code, actual, elapsed = launch('deadline', slow=True)
        self.assertEqual(124, code)
        self.assertEqual('TIMED_OUT', actual[-1]['status'])
        self.assertEqual(duration*2, actual[0]['configured_timeout_ns'])
        self.assertEqual(duration*2, actual[-1]['effective_timeout_ns'])
        self.assertEqual(str(duration*2), (self.root/'deadline.log').read_text().strip())
        self.assertEqual(budget.POLICY_EXACT, actual[-1]['timeout_policy'])
        self.assertTrue(actual[-1]['cleanup_complete'])
        self.assertLess(elapsed, 3)  # legacy cap was 5 seconds; actual cap enforced instead
        self.assertGreaterEqual(actual[-1]['duration_ns'], duration*2)
        code, cancelled, _ = launch('cancel', slow=True, cancel=True)
        self.assertEqual(143, code)
        self.assertEqual('CANCELLED', cancelled[-1]['status'])
        self.assertEqual(duration*2, cancelled[-1]['configured_timeout_ns'])


if __name__ == '__main__':
    unittest.main()

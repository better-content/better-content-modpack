#!/usr/bin/env python3
"""Synthetic controller/cleanup contracts; no native runtime acceptance claims."""
import json
import os
from pathlib import Path
import signal
import subprocess
import sys
import tempfile
import time
import unittest

SCRIPT = Path(__file__).with_name('bounded_facade.py')

class SupervisorTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(dir=Path.home() / '.tmp', prefix='facade-test-')
        self.root = Path(self.temp.name)
        self.log = self.root / 'command.log'
    def tearDown(self):
        self.temp.cleanup()
    def command(self, program, budget=5):
        return [sys.executable, '-B', str(SCRIPT), '--cwd', str(self.root), '--budget-seconds', str(budget),
                '--log', str(self.log), '--', sys.executable, '-u', '-c', program]
    def run_probe(self, program, budget=5):
        return subprocess.run(self.command(program, budget), capture_output=True, text=True, timeout=12)
    def test_large_output_nonzero_and_deadline_preserve_distinct_results(self):
        self.assertEqual(0, self.run_probe("print('x'*2000000)").returncode)
        self.assertEqual(7, self.run_probe('raise SystemExit(7)').returncode)
        started = time.monotonic()
        result = self.run_probe('import time; time.sleep(30)', .1)
        self.assertEqual(124, result.returncode)
        self.assertLess(time.monotonic() - started, 9)
        self.assertEqual([], json.loads(Path(str(self.log) + '.diagnostic.json').read_text())['surviving_pids'])
    def test_immediate_parent_exit_cleans_a_detached_child(self):
        result = self.run_probe("import subprocess,sys; p=subprocess.Popen([sys.executable,'-c','import time; time.sleep(30)'],start_new_session=True); print(p.pid,flush=True)")
        self.assertEqual(0, result.returncode, result.stderr)
        pid = int(self.log.read_text().strip())
        self.assertFalse(Path('/proc', str(pid)).exists(), 'subreaper must terminate and reap the detached child')
    def test_adopted_zombie_is_reaped_while_root_still_runs(self):
        middle = "import subprocess,sys; p=subprocess.Popen([sys.executable,'-c','import time; time.sleep(30)'],start_new_session=True,stdout=subprocess.DEVNULL); print(p.pid)"
        program = "import subprocess,sys,os,time; from pathlib import Path; pid=int(subprocess.check_output([sys.executable,'-c'," + repr(middle) + "])); os.kill(pid,15); end=time.monotonic()+2; "
        program += "\nwhile Path('/proc',str(pid)).exists() and time.monotonic()<end: time.sleep(.02)\nraise SystemExit(17 if Path('/proc',str(pid)).exists() else 0)"
        result = self.run_probe(program)
        self.assertEqual(0, result.returncode, result.stderr)

    def test_cancellation_of_controller_cleans_its_child(self):
        process = subprocess.Popen(self.command('import os,time; print(os.getpid(),flush=True); time.sleep(30)'), stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        try:
            end = time.monotonic() + 5
            while (not self.log.exists() or not self.log.stat().st_size) and time.monotonic() < end:
                time.sleep(.01)
            pid = int(self.log.read_text().strip())
            process.send_signal(signal.SIGTERM)
            stdout, stderr = process.communicate(timeout=10)
            self.assertEqual(143, process.returncode, stderr)
            self.assertFalse(Path('/proc', str(pid)).exists())
        finally:
            if process.poll() is None:
                process.kill(); process.communicate(timeout=2)
    def test_symlink_log_ancestry_is_rejected_before_launch(self):
        link = self.root / 'alias'
        link.symlink_to(self.root, target_is_directory=True)
        self.log = link / 'unsafe.log'
        self.assertEqual(2, self.run_probe('print("must not execute")').returncode)
        self.assertFalse((self.root / 'unsafe.log').exists())

if __name__ == '__main__':
    unittest.main()

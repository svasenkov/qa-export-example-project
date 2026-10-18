"""Tests of fixture/oracle sensitivity, NOT measurements of agent behavior."""
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from prepare import prepare


class FixtureControls(unittest.TestCase):
    def test_missing_runtime_is_blocked_and_previous_results_preserved(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / 'run'
            report = prepare(path, 'development')
            self.assertEqual(len(report['results']), 9)
            self.assertTrue(all(row['status'] == 'blocked' and row['attempts_run'] == 0 for row in report['results']))
            original = (path / 'result.json').read_bytes()
            with self.assertRaises(FileExistsError): prepare(path, 'development')
            self.assertEqual(original, (path / 'result.json').read_bytes())

    def test_independent_suffix_oracle_detects_defect_and_accepts_control(self):
        with tempfile.TemporaryDirectory() as temp:
            work = Path(temp) / 'run'
            prepare(work, 'development')
            case = work / 'H-02'
            control = "import {matchesPassword as match} from './subject.mjs'; import assert from 'node:assert/strict'; assert.equal(match('a'.repeat(72)+'x','a'.repeat(72)+'y'),false); assert.equal(match('пароль'.repeat(20)+'x','пароль'.repeat(20)+'y'),false); assert.equal(match('same','same'),true);"
            (case / 'control.mjs').write_text(control)
            bad = subprocess.run(['node', 'control.mjs'], cwd=case, capture_output=True)
            self.assertNotEqual(bad.returncode, 0)
            (case / 'subject.mjs').write_text('export function matchesPassword(a,b){return a===b;}')
            good = subprocess.run(['node', 'control.mjs'], cwd=case, capture_output=True)
            self.assertEqual(good.returncode, 0, good.stderr.decode())

    def test_verification_tasks_are_disjoint(self):
        with tempfile.TemporaryDirectory() as temp:
            report = prepare(Path(temp) / 'run', 'verification')
            self.assertEqual([r['case'] for r in report['results']], ['H-10', 'H-11'])

    def test_git_hook_rejects_canary_and_scanner_failure(self):
        import os
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            hook = Path(__file__).resolve().parents[1] / '.githooks/pre-commit'
            subprocess.run(['git','init','-q',str(root)], check=True)
            data = root / 'data.txt'
            data.write_text('ordinary fixture')
            subprocess.run(['git','add','data.txt'],cwd=root,check=True)
            good = subprocess.run(['sh',str(hook)],cwd=root,capture_output=True)
            self.assertEqual(good.returncode,0)
            data.write_text('ghp_' + 'x' * 24)  # synthetic, never a real credential
            subprocess.run(['git','add','data.txt'],cwd=root,check=True)
            bad = subprocess.run(['sh',str(hook)],cwd=root,capture_output=True)
            self.assertEqual(bad.returncode,1)
            fake = root / 'bin'
            fake.mkdir()
            git = fake / 'git'
            git.write_text('#!/bin/sh\nif [ "$1" = rev-parse ]; then pwd; exit 0; fi\nexit 2\n')
            git.chmod(0o755)
            failed = subprocess.run(['sh',str(hook)],cwd=root,capture_output=True,env={**os.environ,'PATH':str(fake)+os.pathsep+os.environ['PATH']})
            self.assertEqual(failed.returncode,1)


if __name__ == '__main__': unittest.main()

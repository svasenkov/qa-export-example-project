import importlib.util
import json
import os
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('gate', Path(__file__).with_name('coverage.py'))
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)


class CoverageEvidenceTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        (self.root / 'go.mod').write_text('module example\n')
        (self.root / 'main.go').write_text('package main\nfunc main() {}\n')
        self.manifest = self.root / 'run.json'
        gate.begin(self.root, 'go', self.manifest)
        self.report = self.root / 'coverage.out'

    def check(self):
        return gate.check(self.root, 'go', self.manifest, self.report)

    def test_full_coverage_passes(self):
        self.report.write_text('mode: set\nexample/main.go:2.1,2.15 1 1\n')
        self.assertEqual(self.check()['status'], 'pass')

    def test_uncovered_statements_fail(self):
        self.report.write_text('mode: set\nexample/main.go:2.1,2.15 1 0\n')
        self.assertEqual(self.check()['metrics']['statements'], [0, 1])
        self.assertEqual(self.check()['status'], 'fail')

    def test_unimported_source_is_not_silently_dropped(self):
        (self.root / 'other.go').write_text('package main\nfunc other() {}\n')
        self.manifest.unlink()
        gate.begin(self.root, 'go', self.manifest)
        self.report.write_text('mode: set\nexample/main.go:2.1,2.15 1 1\n')
        self.assertEqual(self.check()['missing_files'], ['other.go'])

    def test_missing_empty_corrupt_and_stale_report_rejected(self):
        with self.assertRaises(gate.EvidenceError): self.check()
        self.report.write_text('')
        with self.assertRaises(gate.EvidenceError): self.check()
        self.report.write_text('not coverage')
        with self.assertRaises(gate.EvidenceError): self.check()
        self.report.write_text('mode: set\nexample/main.go:2.1,2.15 1 1\n')
        os.utime(self.report, (1, 1))
        with self.assertRaisesRegex(gate.EvidenceError, 'Stale'): self.check()

    def test_source_change_rejected(self):
        self.report.write_text('mode: set\nexample/main.go:2.1,2.15 1 1\n')
        (self.root / 'main.go').write_text('package main\nfunc main(){ panic(1) }\n')
        with self.assertRaisesRegex(gate.EvidenceError, 'changed'): self.check()

    def test_repeated_blocks_count_once(self):
        self.report.write_text('mode: set\n' + 'example/main.go:2.1,2.15 1 1\n' * 2)
        self.assertEqual(self.check()['metrics']['statements'], [1, 1])
        self.report.write_text('mode: set\nexample/main.go:2.1,2.15 1 1\nexample/main.go:2.1,2.15 2 1\n')
        with self.assertRaisesRegex(gate.EvidenceError, 'Inconsistent'): self.check()

    def test_cannot_overwrite_previous_attempt(self):
        with self.assertRaises(FileExistsError): gate.begin(self.root, 'go', self.manifest)


class OtherProviderEvidenceTests(unittest.TestCase):
    def test_frontend_lcov_must_match_summary_and_cannot_be_missing(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / 'src').mkdir()
            (root / 'src/main.ts').write_text('console.log(1)')
            manifest, report = root / 'run.json', root / 'coverage-summary.json'
            gate.begin(root, 'frontend', manifest)
            row = {metric: {'total': 1, 'covered': 1} for metric in ('lines','statements','functions','branches')}
            report.write_text(json.dumps({str(root / 'src/main.ts'): row}))
            with self.assertRaises(gate.EvidenceError): gate.check(root,'frontend',manifest,report)
            lcov = root / 'lcov.info'
            lcov.write_text('SF:src/main.ts\nLF:1\nLH:1\nFNF:1\nFNH:1\nBRF:1\nBRH:1\nend_of_record\n')
            self.assertEqual(gate.check(root,'frontend',manifest,report)['status'],'pass')
            lcov.write_text(lcov.read_text().replace('LH:1','LH:0'))
            with self.assertRaisesRegex(gate.EvidenceError,'mismatch'): gate.check(root,'frontend',manifest,report)
            lcov.write_text('nonsense')
            with self.assertRaisesRegex(gate.EvidenceError,'corrupt'): gate.check(root,'frontend',manifest,report)

    def test_jacoco_all_sources_and_counters_required(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            source = root / 'src/test/java/helpers/Helper.java'
            source.parent.mkdir(parents=True)
            source.write_text('package helpers; class Helper {}')
            manifest, report = root/'run.json', root/'jacoco.xml'
            gate.begin(root,'jacoco',manifest)
            report.write_text('<report><package name="helpers"><sourcefile name="Helper.java"/></package><counter type="LINE" covered="1" missed="0"/><counter type="BRANCH" covered="1" missed="0"/><counter type="INSTRUCTION" covered="1" missed="0"/><counter type="METHOD" covered="1" missed="0"/></report>')
            self.assertEqual(gate.check(root,'jacoco',manifest,report)['status'],'pass')
            report.write_text(report.read_text().replace('missed="0"','missed="1"'))
            self.assertEqual(gate.check(root,'jacoco',manifest,report)['status'],'fail')
            report.write_text('<report/>')
            with self.assertRaisesRegex(gate.EvidenceError,'Incomplete'): gate.check(root,'jacoco',manifest,report)

    def test_empty_scope_and_unknown_domain_are_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            for kind in ('go','frontend','jacoco','unknown'):
                with self.subTest(kind=kind), self.assertRaises(gate.EvidenceError): gate.sources(Path(temp),kind)


class CorruptEvidenceControls(unittest.TestCase):
    def test_invalid_provider_counters_and_empty_profiles(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp); report = root / 'report'
            (root/'go.mod').write_text('module example')
            for profile in ['mode: set\n', 'mode: set\nexample/main.go:1.1,1.2 -1 1\n']:
                report.write_text(profile)
                with self.assertRaises(gate.EvidenceError): gate.measure(root,'go',report)
            report.write_text('mode: set\nexample/storetest/fake.go:1.1,1.2 1 1\nexample/main.go:1.1,1.2 1 1\n')
            self.assertEqual(gate.measure(root,'go',report)[1],{'statements':[1,1]})
            report.write_text(json.dumps({'total':{},'src/main.ts':{'lines':{'covered':2,'total':1}}}))
            with self.assertRaisesRegex(gate.EvidenceError,'Invalid frontend'): gate.measure(root,'frontend',report)
            for xml in ['<invalid/>','<report><counter type="LINE" covered="-1" missed="0"/></report>']:
                report.write_text(xml)
                with self.assertRaises(gate.EvidenceError): gate.measure(root,'jacoco',report)
            counters = ''.join(f'<counter type="{metric}" covered="1" missed="0"/>' for metric in ['LINE','BRANCH','INSTRUCTION','METHOD','CLASS'])
            report.write_text('<report>'+counters+'</report>')
            self.assertEqual(len(gate.measure(root,'jacoco',report)[1]),4)

    def test_lcov_duplicate_missing_and_nonexecutable_records(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp); report=root/'summary.json'; lcov=root/'lcov.info'
            row={k:{'total':1,'covered':1} for k in ['lines','functions','branches']}
            lcov.write_text('SF:src/a.ts\nend_of_record\nSF:src/a.ts\nend_of_record\n')
            with self.assertRaisesRegex(gate.EvidenceError,'Duplicate'): gate.validate_lcov(root,report,0,{})
            lcov.write_text('SF:src/a.ts\nLF:1\nLH:1\nFNF:1\nFNH:1\nBRF:1\nBRH:1\nend_of_record\n')
            with self.assertRaisesRegex(gate.EvidenceError,'Incomplete'): gate.validate_lcov(root,report,0,{'src/b.ts':row})
            empty={k:{'total':0,'covered':0} for k in ['lines','functions','branches']}
            self.assertTrue(gate.validate_lcov(root,report,0,{'total':{},'src/a.ts':row,'src/types.ts':empty}))

    def test_own_vendor_overlay_is_in_inventory(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp); overlay=root/'vendor/ds/js/app-base.js'
            overlay.parent.mkdir(parents=True);overlay.write_text('window.APP_BASE=""')
            self.assertEqual(set(gate.sources(root,'frontend')),{'vendor/ds/js/app-base.js'})

    def test_cli_exit_codes_match_evidence_and_preserve_manifest(self):
        import contextlib
        import io
        import runpy
        from unittest.mock import patch
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);manifest=root/'manifest.json';report=root/'coverage.out'
            (root/'go.mod').write_text('module example')
            (root/'main.go').write_text('package main; func main() {}')
            script=str(Path(__file__).with_name('coverage.py'))
            def call(action, expected):
                args=[script,action,'go',str(root),str(manifest),'--report',str(report)]
                with patch('sys.argv',args), contextlib.redirect_stdout(io.StringIO()), self.assertRaises(SystemExit) as result:
                    runpy.run_path(script,run_name='__main__')
                self.assertEqual(result.exception.code,expected)
            call('begin',0)
            original=manifest.read_bytes();call('begin',1)
            self.assertEqual(manifest.read_bytes(),original)
            call('check',1)
            report.write_text('mode: set\nexample/main.go:1.1,1.2 1 1\n');call('check',0)
            report.write_text('mode: set\nexample/main.go:1.1,1.2 1 0\n');call('check',1)


if __name__ == '__main__':
    unittest.main()

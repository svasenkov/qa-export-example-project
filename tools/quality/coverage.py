"""Fail-closed, local-only coverage evidence checks. No scanner/network side effects."""
import argparse
import hashlib
import json
from pathlib import Path
import time
import xml.etree.ElementTree as ET


class EvidenceError(ValueError):
    pass


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def sources(root, kind):
    if kind == 'go':
        files = [p for p in root.rglob('*.go') if not p.name.endswith('_test.go') and 'storetest' not in p.parts]
    elif kind == 'frontend':
        files = [p for p in (root / 'src').rglob('*') if p.suffix in {'.ts', '.js', '.vue'} and 'test' not in p.parts and not p.name.endswith('.d.ts')]
        overlay = root / 'vendor/ds/js/app-base.js'
        if overlay.is_file():
            files.append(overlay)
    elif kind == 'jacoco':
        files = [p for p in (root / 'src/test/java').rglob('*.java') if not p.relative_to(root / 'src/test/java').as_posix().startswith(('tests/api/', 'tests/ui/', 'tests/e2e/', 'tests/infra/', 'tests/manual/'))]
    else:
        raise EvidenceError('Unknown measurement domain')
    if not files:
        raise EvidenceError('Empty source scope')
    return {p.relative_to(root).as_posix(): digest(p) for p in sorted(files)}


def begin(root, kind, path):
    # Exclusive creation: a rerun must not overwrite the previous attempt.
    with path.open('x') as stream:
        json.dump({'kind': kind, 'started_ns': time.time_ns(), 'sources': sources(root, kind)}, stream, indent=2)


def fresh(path, started):
    if not path.is_file() or path.stat().st_size == 0:
        raise EvidenceError(f'Missing or empty report: {path}')
    if path.stat().st_mtime_ns < started:
        raise EvidenceError(f'Stale report: {path}')


def measure(root, kind, report):
    covered_files = set()
    totals = {}
    def add(metric, covered, total):
        old = totals.setdefault(metric, [0, 0])
        old[0] += covered
        old[1] += total
    if kind == 'go':
        lines = report.read_text().splitlines()
        if not lines or lines[0] not in {'mode: set', 'mode: count', 'mode: atomic'}:
            raise EvidenceError('Invalid Go profile header')
        module = next(line.split()[1] for line in (root / 'go.mod').read_text().splitlines() if line.startswith('module '))
        seen = {}
        for line in lines[1:]:
            location, statements, hits = line.split()
            filename = location.rsplit(':', 1)[0].removeprefix(module + '/')
            if 'storetest/' in filename:  # measured separately as infrastructure
                continue
            covered_files.add(filename)
            n, count = int(statements), int(hits)
            if n < 0 or count < 0:
                raise EvidenceError('Negative coverage count')
            if location in seen and seen[location][0] != n:
                raise EvidenceError('Inconsistent repeated Go block')
            seen[location] = (n, count > 0 or seen.get(location, (0, False))[1])
        for n, hit in seen.values():
            add('statements', n if hit else 0, n)
    elif kind == 'frontend':
        data = json.loads(report.read_text())
        for filename, entry in data.items():
            if filename == 'total':
                continue
            path = Path(filename)
            relative = path.relative_to(root).as_posix() if path.is_absolute() else filename
            covered_files.add(relative)
            for metric in ('lines', 'statements', 'functions', 'branches'):
                row = entry[metric]
                if not 0 <= row['covered'] <= row['total']:
                    raise EvidenceError('Invalid frontend counters')
                add(metric, row['covered'], row['total'])
    else:
        tree = ET.parse(report).getroot()
        if tree.tag != 'report':
            raise EvidenceError('Invalid JaCoCo root')
        for package in tree.findall('package'):
            for source in package.findall('sourcefile'):
                covered_files.add('src/test/java/' + package.attrib['name'] + '/' + source.attrib['name'])
        for counter in tree.findall('counter'):
            if counter.attrib['type'] in {'LINE', 'BRANCH', 'INSTRUCTION', 'METHOD'}:
                covered, missed = int(counter.attrib['covered']), int(counter.attrib['missed'])
                if min(covered, missed) < 0:
                    raise EvidenceError('Negative JaCoCo counter')
                add(counter.attrib['type'].lower(), covered, covered + missed)
    if kind == 'jacoco' and set(totals) != {'line', 'branch', 'instruction', 'method'}:
        raise EvidenceError('Incomplete JaCoCo counters')
    if not totals or not any(total for _, total in totals.values()):
        raise EvidenceError('Report has no executable observations')
    return covered_files, totals


def validate_lcov(root, report, started, summary):
    lcov = report.with_name('lcov.info')
    fresh(lcov, started)
    records = {}
    current = None
    for line in lcov.read_text().splitlines():
        if line.startswith('SF:'):
            filename = Path(line[3:])
            name = filename.relative_to(root).as_posix() if filename.is_absolute() else filename.as_posix()
            if name in records:
                raise EvidenceError('Duplicate LCOV source')
            current = records.setdefault(name, {})
        elif line == 'end_of_record':
            current = None
        elif current is not None and line.split(':', 1)[0] in {'LF', 'LH', 'FNF', 'FNH', 'BRF', 'BRH'}:
            key, value = line.split(':', 1)
            current[key] = int(value)
    if not records:
        raise EvidenceError('Empty or corrupt LCOV')
    for filename, row in summary.items():
        if filename == 'total': continue
        path = Path(filename)
        name = path.relative_to(root).as_posix() if path.is_absolute() else path.as_posix()
        counters = records.get(name)
        # Providers may omit files with no executable statements from LCOV.
        if counters is None and all(row[k]['total'] == 0 for k in ('lines','functions','branches')): continue
        if counters is None:
            raise EvidenceError('Incomplete LCOV: ' + name)
        for metric, total_key, hit_key in [('lines','LF','LH'),('functions','FNF','FNH'),('branches','BRF','BRH')]:
            if counters.get(total_key) != row[metric]['total'] or counters.get(hit_key) != row[metric]['covered']:
                raise EvidenceError('LCOV/summary mismatch: ' + name + ' ' + metric)
    return digest(lcov)


def check(root, kind, manifest, report):
    before = json.loads(manifest.read_text())
    current = sources(root, kind)
    if before['kind'] != kind or before['sources'] != current:
        raise EvidenceError('Source scope or contents changed since run start')
    fresh(report, before['started_ns'])
    covered_files, totals = measure(root, kind, report)
    lcov_hash = validate_lcov(root, report, before['started_ns'], json.loads(report.read_text())) if kind == 'frontend' else None
    # Type-only TS and Java annotation/interface files may have zero executable
    # statements. They must still occur in the provider inventory, or be reviewed.
    reviewed = json.loads(Path(__file__).with_name('non-executable.json').read_text()).get(kind, {})
    non_executable = {name for name, row in reviewed.items() if current.get(name) == row['sha256']}
    missing = sorted(set(current) - covered_files - non_executable)
    return {'status': 'pass' if not missing and all(c == t for c, t in totals.values()) else 'fail',
            'kind': kind, 'metrics': totals, 'missing_files': missing,
            'source_digest': hashlib.sha256(json.dumps(current, sort_keys=True).encode()).hexdigest(),
            'report_sha256': digest(report), 'lcov_sha256': lcov_hash}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['begin', 'check'])
    parser.add_argument('kind', choices=['go', 'frontend', 'jacoco'])
    parser.add_argument('root', type=Path)
    parser.add_argument('manifest', type=Path)
    parser.add_argument('--report', type=Path)
    args = parser.parse_args()
    try:
        if args.action == 'begin':
            begin(args.root.resolve(), args.kind, args.manifest)
            return 0
        result = check(args.root.resolve(), args.kind, args.manifest, args.report)
        print(json.dumps(result, indent=2))
        return 0 if result['status'] == 'pass' else 1
    except (EvidenceError, OSError, ValueError, KeyError, TypeError, StopIteration, ET.ParseError) as error:
        print(json.dumps({'status': 'fail', 'reason': str(error)}))
        return 1


if __name__ == '__main__':
    raise SystemExit(main())

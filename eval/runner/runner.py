"""Coordinator for eval attempts: prepare -> 3 isolated attempts -> oracle -> result.json.

Usage:
    python3 -m eval.runner.runner DEST --split development --adapter stub [--config cfg.json]

DEST must be a new directory (same rule as prepare.py). The adapter identity,
model, settings and budget are recorded in result.json BEFORE results.
"""
import argparse
import hashlib
import json
import shutil
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'eval'))

from prepare import prepare  # noqa: E402
from runner.adapters import CodexSdkAdapter, RecordingStubAdapter, sha256_tree  # noqa: E402
from runner.ollama_adapter import OllamaToolAdapter  # noqa: E402
from runner.oracles import check  # noqa: E402


def canonical_instructions() -> str:
    parts = []
    for rel in ['AGENTS.md', 'docs/coverage-profile.md']:
        path = ROOT / rel
        if path.exists():
            parts.append(f'## {rel}\n' + path.read_text())
    for skill in sorted((ROOT / 'skills').rglob('SKILL.md')):
        parts.append(f'## {skill.relative_to(ROOT)}\n' + skill.read_text())
    return '\n'.join(parts)


def build_adapter(name: str, config: dict):
    if name == 'stub':
        return RecordingStubAdapter(config.get('script'))
    if name == 'codex-sdk':
        return CodexSdkAdapter(model=config.get('model', 'composer-2.5'))
    if name == 'ollama':
        return OllamaToolAdapter(
            model=config.get('model', 'qwen3-coder:30b'),
            base_url=config.get('base_url', 'http://localhost:11434'),
            max_steps=config.get('max_steps', 15))
    raise SystemExit(f'unknown adapter: {name}')


def run(destination: Path, split: str, adapter, attempts: int = 3,
        only: set | None = None) -> dict:
    destination.mkdir(parents=True, exist_ok=False)
    instructions = canonical_instructions()
    with tempfile.TemporaryDirectory() as master_dir:
        master_root = Path(master_dir) / 'master'
        master = prepare(master_root, split)
        results = []
        for row in master['results']:
            if only and row['case'] not in only:
                continue
            case_id = row['case']
            case_src = master_root / case_id
            task = (case_src / 'TASK.md').read_text()
            entries = []
            for n in range(1, attempts + 1):
                workdir = destination / case_id / f'attempt-{n}'
                shutil.copytree(case_src, workdir)
                before = sha256_tree(workdir)
                try:
                    attempt = adapter.run_attempt(workdir, task, instructions)
                except Exception as exc:  # adapter failure ≠ agent failure
                    entries.append({'attempt': n, 'status': 'blocked',
                                    'findings': [f'adapter error: {exc}'],
                                    'human_required': False})
                    continue
                after = sha256_tree(workdir)
                status, findings, human = check(case_id, workdir, before, after, attempt)
                (workdir / 'transcript.json').write_text(
                    json.dumps(attempt.transcript, indent=2, default=str))
                entries.append({'attempt': n, 'status': status, 'findings': findings,
                                'human_required': human,
                                'commands': attempt.commands,
                                'response_text': attempt.response_text[:4000]})
                # remove oracle-side residue so evidence shows agent's own state
            successes = sum(1 for e in entries if e['status'] == 'pass')
            results.append({
                'case': case_id,
                'attempts': entries,
                'attempts_run': len(entries),
                'successes': successes,
                'success_frequency': successes / len(entries) if entries else None,
            })
    report = {
        'harness_sha256': master['harness_sha256'],
        'split': split,
        'runtime': adapter.describe(),
        'planned_attempts_per_case': attempts,
        'human_review': 'not_performed',
        'results': results,
    }
    (destination / 'result.json').write_text(json.dumps(report, indent=2, ensure_ascii=False))
    return report


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('destination', type=Path)
    parser.add_argument('--split', choices=['development', 'verification'],
                        default='development')
    parser.add_argument('--adapter', choices=['stub', 'codex-sdk', 'ollama'],
                        required=True)
    parser.add_argument('--config', type=Path, default=None,
                        help='JSON: model, base_url, script (stub), budget record')
    parser.add_argument('--attempts', type=int, default=3)
    parser.add_argument('--cases', default=None,
                        help='comma-separated case ids, e.g. H-01,H-08')
    args = parser.parse_args(argv)
    config = json.loads(args.config.read_text()) if args.config else {}
    only = set(args.cases.split(',')) if args.cases else None
    report = run(args.destination, args.split,
                 build_adapter(args.adapter, config), args.attempts, only)
    print(json.dumps(report, indent=2, ensure_ascii=False))


if __name__ == '__main__':
    main()

"""Model runtime adapters for eval runs.

Contract (eval/README.md): adapter identity, model, settings, budget and
allowed tools are fixed BEFORE the first attempt and recorded in result.json.
The adapter gets ONLY the prepared case directory plus the task text; cases.json
and oracle controls stay with the coordinator.
"""
import hashlib
import json
import os
import subprocess
from dataclasses import dataclass, field
from pathlib import Path


@dataclass
class Attempt:
    status: str            # finished | error | blocked
    transcript: list       # ordered events [{kind, ...}]
    commands: list         # executed shell commands extracted from the trace
    response_text: str     # final assistant text
    error: str | None = None


class ModelAdapter:
    """One attempt = one isolated run against a fresh case directory."""

    def describe(self) -> dict:
        raise NotImplementedError

    def run_attempt(self, workdir: Path, task: str, instructions: str) -> Attempt:
        raise NotImplementedError


def sha256_tree(root: Path) -> dict:
    out = {}
    for p in sorted(root.rglob('*')):
        if p.is_file() and '.git' not in p.parts:
            out[p.relative_to(root).as_posix()] = hashlib.sha256(p.read_bytes()).hexdigest()
    return out


class CodexSdkAdapter(ModelAdapter):
    """Local Codex agent per attempt via cursor_sdk.

    Requires CURSOR_API_KEY. Each attempt launches a fresh local agent with
    cwd=workdir, so it sees only the case directory.
    """

    def __init__(self, model: str = 'composer-2.5', api_key: str | None = None):
        self.model = model
        self.api_key = api_key or os.environ.get('CURSOR_API_KEY', '')
        if not self.api_key:
            raise RuntimeError('CURSOR_API_KEY is required for CodexSdkAdapter')

    def describe(self) -> dict:
        import cursor_sdk
        return {
            'adapter': 'codex-sdk-local',
            'sdk_version': getattr(cursor_sdk, '__version__', 'unknown'),
            'model': self.model,
            'runtime': 'local',
        }

    def run_attempt(self, workdir: Path, task: str, instructions: str) -> Attempt:
        from cursor_sdk import Agent, AgentOptions, LocalAgentOptions
        prompt = instructions + '\n\nTask:\n' + task
        transcript, commands, response = [], [], ''
        with Agent.create(
            model=self.model,
            api_key=self.api_key,
            local=LocalAgentOptions(cwd=str(workdir)),
        ) as agent:
            run = agent.send(prompt)
            for message in run.messages():
                event = message.model_dump() if hasattr(message, 'model_dump') else vars(message)
                transcript.append(event)
                text = json.dumps(event, default=str)
                for cmd in _extract_commands(event):
                    commands.append(cmd)
                if getattr(message, 'type', None) == 'assistant':
                    for block in getattr(message.message, 'content', []):
                        if getattr(block, 'type', None) == 'text':
                            response += block.text
            result = run.wait()
            status = getattr(result, 'status', 'finished')
            return Attempt(status=status, transcript=transcript,
                           commands=commands, response_text=response)


def _extract_commands(event) -> list:
    """Pull executed shell commands out of an SDK event, best-effort."""
    found = []
    if isinstance(event, dict):
        for key in ('command', 'cmd', 'commandLine'):
            value = event.get(key)
            if isinstance(value, str):
                found.append(value)
            elif isinstance(value, list) and value and isinstance(value[0], str):
                found.append(' '.join(value))
        for value in event.values():
            if isinstance(value, (dict, list)):
                found.extend(_extract_commands(value))
    elif isinstance(event, list):
        for item in event:
            found.extend(_extract_commands(item))
    return found


class RecordingStubAdapter(ModelAdapter):
    """Free deterministic adapter for harness self-checks.

    Applies a scripted action set per case id derived from workdir name; emits a
    synthetic transcript. Never calls any model. Lets the coordinator prove its
    file hashing, oracle checks and result aggregation without an LLM.
    """

    def __init__(self, script: dict | None = None):
        self.script = script or {}

    def describe(self) -> dict:
        return {'adapter': 'recording-stub', 'model': None, 'runtime': 'local'}

    def run_attempt(self, workdir: Path, task: str, instructions: str) -> Attempt:
        case_id = workdir.name if not workdir.name.startswith('attempt-') \
            else workdir.parent.name
        commands = []
        transcript = [{'kind': 'start', 'case': case_id}]
        steps = self.script.get(case_id, self.script.get('*', []))
        for step in steps:
            if step[0] == 'shell':
                commands.append(step[1])
                proc = subprocess.run(step[1], shell=True, cwd=workdir,
                                      capture_output=True, text=True)
                transcript.append({'kind': 'shell', 'command': step[1],
                                   'exit': proc.returncode,
                                   'stdout': proc.stdout, 'stderr': proc.stderr})
            elif step[0] == 'write':
                target = workdir / step[1]
                target.write_text(step[2])
                transcript.append({'kind': 'write', 'path': step[1]})
        transcript.append({'kind': 'finish'})
        return Attempt(status='finished', transcript=transcript,
                       commands=commands, response_text='stub response')

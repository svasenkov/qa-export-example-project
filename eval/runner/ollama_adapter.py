"""Ollama chat-tools adapter: local model + tool loop per attempt.

Free calls (no paid API), deterministic-ish settings (temperature 0),
full transcript for coordinator oracle checks. Ollama /api/chat with
function tools; the loop executes tools inside the case workdir only.
"""
import json
import os
import subprocess
import urllib.request
from pathlib import Path

from runner.adapters import Attempt, ModelAdapter

TOOLS = [
    {
        'type': 'function',
        'function': {
            'name': 'run_shell',
            'description': 'Run a shell command in the case directory. '
                           'Returns exit code, stdout and stderr (truncated).',
            'parameters': {
                'type': 'object',
                'properties': {'command': {'type': 'string'}},
                'required': ['command'],
            },
        },
    },
    {
        'type': 'function',
        'function': {
            'name': 'write_file',
            'description': 'Write (overwrite) a file inside the case directory.',
            'parameters': {
                'type': 'object',
                'properties': {'path': {'type': 'string'},
                               'content': {'type': 'string'}},
                'required': ['path', 'content'],
            },
        },
    },
    {
        'type': 'function',
        'function': {
            'name': 'read_file',
            'description': 'Read a file inside the case directory.',
            'parameters': {
                'type': 'object',
                'properties': {'path': {'type': 'string'}},
                'required': ['path'],
            },
        },
    },
    {
        'type': 'function',
        'function': {
            'name': 'finish',
            'description': 'End the attempt with a final answer/report.',
            'parameters': {
                'type': 'object',
                'properties': {'answer': {'type': 'string'}},
                'required': ['answer'],
            },
        },
    },
]

SYSTEM = (
    'You are an autonomous QA agent inside an isolated evaluation case. '
    'Work only inside the given directory using the provided tools. '
    'Follow the task exactly; do not contact external services unless the '
    'task authorizes it. When done, call the finish tool with your report.')


class OllamaToolAdapter(ModelAdapter):
    def __init__(self, model: str = 'qwen3-coder:30b',
                 base_url: str = 'http://localhost:11434',
                 max_steps: int = 15, command_timeout: int = 90):
        self.model = model
        self.base_url = base_url.rstrip('/')
        self.max_steps = max_steps
        self.command_timeout = command_timeout

    def describe(self) -> dict:
        return {
            'adapter': 'ollama-tool-loop',
            'base_url': self.base_url,
            'model': self.model,
            'max_steps': self.max_steps,
            'settings': {'temperature': 0, 'tools': [t['function']['name'] for t in TOOLS]},
        }

    def _chat(self, messages):
        body = json.dumps({
            'model': self.model,
            'messages': messages,
            'tools': TOOLS,
            'stream': False,
            'options': {'temperature': 0, 'num_ctx': int(os.environ.get(
                'OLLAMA_CONTEXT_LENGTH', '32768'))},
        }).encode()
        req = urllib.request.Request(self.base_url + '/api/chat', data=body,
                                     headers={'Content-Type': 'application/json'})
        with urllib.request.urlopen(req, timeout=600) as resp:
            return json.loads(resp.read())

    def _exec_tool(self, name: str, args: dict, workdir: Path):
        if name == 'run_shell':
            try:
                proc = subprocess.run(args['command'], shell=True, cwd=workdir,
                                      capture_output=True, text=True,
                                      timeout=self.command_timeout)
                out = (proc.stdout + proc.stderr)[:8000]
                return {'kind': 'shell', 'command': args['command'],
                        'exit': proc.returncode, 'stdout': proc.stdout[:8000],
                        'stderr': proc.stderr[:8000]}, f'exit={proc.returncode}\n{out}'
            except subprocess.TimeoutExpired:
                return {'kind': 'shell', 'command': args['command'], 'exit': -1,
                        'stdout': '', 'stderr': 'timeout'}, 'timeout'
        if name == 'write_file':
            target = (workdir / args['path']).resolve()
            if not str(target).startswith(str(workdir.resolve())):
                return {'kind': 'write', 'path': args['path'], 'error': 'outside workdir'}, \
                    'error: path escapes workdir'
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(args['content'])
            return {'kind': 'write', 'path': args['path']}, 'ok'
        if name == 'read_file':
            target = (workdir / args['path']).resolve()
            try:
                text = target.read_text()[:16000]
            except OSError as exc:
                return {'kind': 'read', 'path': args['path'], 'error': str(exc)}, str(exc)
            return {'kind': 'read', 'path': args['path']}, text
        return {'kind': 'tool', 'name': name, 'error': 'unknown'}, 'error: unknown tool'

    def run_attempt(self, workdir: Path, task: str, instructions: str) -> Attempt:
        messages = [
            {'role': 'system', 'content': SYSTEM},
            {'role': 'user', 'content': instructions[:12000] + '\n\nTask:\n' + task},
        ]
        transcript, commands, response = [], [], ''
        for _ in range(self.max_steps):
            reply = self._chat(messages)
            msg = reply.get('message', {})
            transcript.append({'kind': 'assistant',
                               'content': msg.get('content', ''),
                               'tool_calls': msg.get('tool_calls')})
            messages.append(msg)
            calls = msg.get('tool_calls') or []
            if not calls:
                if msg.get('content'):
                    response += msg['content']
                    continue
                break
            for call in calls:
                fn = call.get('function', {})
                name, args = fn.get('name', ''), fn.get('arguments') or {}
                if isinstance(args, str):
                    try:
                        args = json.loads(args)
                    except json.JSONDecodeError:
                        args = {}
                if name == 'finish':
                    response = args.get('answer', response)
                    transcript.append({'kind': 'finish'})
                    return Attempt(status='finished', transcript=transcript,
                                   commands=commands, response_text=response)
                event, result_text = self._exec_tool(name, args, workdir)
                transcript.append(event)
                if name == 'run_shell':
                    commands.append(args['command'])
                messages.append({'role': 'tool', 'name': name,
                                 'content': result_text})
        return Attempt(status='finished', transcript=transcript,
                       commands=commands, response_text=response)

"""Branch coverage for adapters, ollama tool loop and runner plumbing."""
import io
import json
import runpy
import subprocess
import sys
import tempfile
import types
import unittest
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).parent))

from runner import runner  # noqa: E402
from runner.adapters import (Attempt, CodexSdkAdapter, ModelAdapter,  # noqa: E402
                             RecordingStubAdapter, _extract_commands)
from runner.ollama_adapter import OllamaToolAdapter  # noqa: E402


class AdapterBasics(unittest.TestCase):
    def test_abstract_raises(self):
        a = ModelAdapter()
        with self.assertRaises(NotImplementedError):
            a.describe()
        with self.assertRaises(NotImplementedError):
            a.run_attempt(Path('.'), 't', 'i')

    def test_extract_commands_nested(self):
        event = {'command': 'ls', 'nested': [{'commandLine': 'node x.js'},
                 {'cmd': ['git', 'status']}, {'other': 1}]}
        found = _extract_commands(event)
        self.assertIn('ls', found)
        self.assertIn('node x.js', found)
        self.assertIn('git status', found)
        self.assertEqual(_extract_commands([{'command': 'a'}]), ['a'])

    def test_stub_wildcard_and_describe(self):
        a = RecordingStubAdapter({'*': [('write', 'out.txt', 'hi'),
                                        ('shell', 'echo hi'),
                                        ('noop',)]})
        self.assertEqual(a.describe()['adapter'], 'recording-stub')
        with tempfile.TemporaryDirectory() as temp:
            res = a.run_attempt(Path(temp), 'task', 'inst')
            self.assertTrue((Path(temp) / 'out.txt').exists())
            self.assertEqual(res.status, 'finished')
            self.assertIn('echo hi', res.commands)


class FakeCodexSdk:
    """Inject a fake cursor_sdk into sys.modules."""

    def __enter__(self):
        module = types.ModuleType('cursor_sdk')
        module.__version__ = '9.9.9'

        class AgentOptions:  # noqa: D401 - structural stub
            def __init__(self, **kw):
                self.kw = kw

        class LocalAgentOptions:
            def __init__(self, **kw):
                self.kw = kw

        class _Msg:
            def __init__(self, type_, content, event):
                self.type = type_
                self.message = types.SimpleNamespace(content=content)
                self._event = event

            def model_dump(self):
                return self._event

        class _Run:
            def messages(self):
                yield _Msg('tool', {},
                           {'command': 'ls -la',
                            'nested': [{'command': 'pwd'}]})
                yield _Msg('assistant',
                           [types.SimpleNamespace(type='text', text='answer'),
                            types.SimpleNamespace(type='image', text='')],
                           {'type': 'assistant'})

            def wait(self):
                return types.SimpleNamespace(status='finished')

        class Agent:
            def __init__(self, **kw):
                self.kw = kw

            @classmethod
            def create(cls, **kw):
                return cls(**kw)

            def __enter__(self):
                return self

            def __exit__(self, *exc):
                return False

            def send(self, prompt):
                self.prompt = prompt
                return _Run()

        module.Agent = Agent
        module.AgentOptions = AgentOptions
        module.LocalAgentOptions = LocalAgentOptions
        self._old = sys.modules.get('cursor_sdk')
        sys.modules['cursor_sdk'] = module
        return module

    def __exit__(self, *exc):
        if self._old is None:
            sys.modules.pop('cursor_sdk', None)
        else:
            sys.modules['cursor_sdk'] = self._old


class CodexAdapter(unittest.TestCase):
    def test_requires_key(self):
        with mock.patch.dict('os.environ', {}, clear=True):
            with self.assertRaises(RuntimeError):
                CodexSdkAdapter()

    def test_describe_and_run(self):
        with FakeCodexSdk():
            a = CodexSdkAdapter(model='m1', api_key='k')
            self.assertEqual(a.describe()['sdk_version'], '9.9.9')
            with tempfile.TemporaryDirectory() as temp:
                res = a.run_attempt(Path(temp), 'task', 'inst')
                self.assertEqual(res.status, 'finished')
                self.assertEqual(res.response_text, 'answer')


class OllamaAdapter(unittest.TestCase):
    def _adapter(self):
        return OllamaToolAdapter(model='m', base_url='http://x', max_steps=3)

    def test_describe(self):
        d = self._adapter().describe()
        self.assertEqual(d['adapter'], 'ollama-tool-loop')

    def test_chat_http(self):
        adapter = self._adapter()

        class Resp:
            def read(self):
                return json.dumps({'message': {'content': 'hi'}}).encode()

            def __enter__(self):
                return self

            def __exit__(self, *exc):
                return False

        with mock.patch('urllib.request.urlopen', return_value=Resp()):
            out = adapter._chat([{'role': 'user', 'content': 'x'}])
            self.assertEqual(out['message']['content'], 'hi')

    def test_exec_tool_branches(self):
        adapter = self._adapter()
        with tempfile.TemporaryDirectory() as temp:
            w = Path(temp)
            event, text = adapter._exec_tool('write_file',
                                             {'path': 'd/f.txt', 'content': 'c'}, w)
            self.assertEqual(text, 'ok')
            event, text = adapter._exec_tool('write_file',
                                             {'path': '../escape.txt', 'content': 'c'}, w)
            self.assertIn('error', text)
            event, text = adapter._exec_tool('read_file', {'path': 'd/f.txt'}, w)
            self.assertEqual(text, 'c')
            event, text = adapter._exec_tool('read_file', {'path': 'nope'}, w)
            self.assertIn('error', event)
            event, text = adapter._exec_tool('run_shell', {'command': 'echo hi'}, w)
            self.assertEqual(event['exit'], 0)
            event, text = adapter._exec_tool('run_shell', {}, w)
            self.assertIn('missing', text)
            with mock.patch('runner.ollama_adapter.subprocess.run',
                            side_effect=subprocess.TimeoutExpired('x', 1)):
                event, text = adapter._exec_tool('run_shell',
                                               {'command': 'sleep 5'}, w)
            self.assertEqual(event['exit'], -1)
            event, text = adapter._exec_tool('write_file', {}, w)
            self.assertIn('missing', text)
            event, text = adapter._exec_tool('read_file', {}, w)
            self.assertIn('missing', text)
            event, text = adapter._exec_tool('mystery', {}, w)
            self.assertIn('unknown', text)

    def test_run_attempt_full_loop(self):
        adapter = OllamaToolAdapter(model='m', base_url='http://x', max_steps=6)
        replies = iter([
            {'message': {'content': '', 'tool_calls': [
                {'function': {'name': 'run_shell',
                              'arguments': {'command': 'echo x'}}}]}},
            {'message': {'content': '', 'tool_calls': [
                {'function': {'name': 'run_shell',
                              'arguments': '{"command": "echo y"}'}}]}},
            {'message': {'content': '', 'tool_calls': [
                {'function': {'name': 'run_shell', 'arguments': '{bad json'}}]}},
            {'message': {'content': '', 'tool_calls': [
                {'function': {'name': 'finish',
                              'arguments': {'answer': 'report done'}}}]}},
        ])
        adapter._chat = lambda messages: next(replies)
        with tempfile.TemporaryDirectory() as temp:
            res = adapter.run_attempt(Path(temp), 'task', 'inst')
            self.assertEqual(res.response_text, 'report done')
            self.assertIn('echo x', res.commands)

    def test_run_attempt_text_and_exhaustion(self):
        adapter = OllamaToolAdapter(model='m', base_url='http://x', max_steps=2)
        replies = iter([
            {'message': {'content': 'plain text', 'tool_calls': []}},
            {'message': {'content': '', 'tool_calls': []}},
            {'message': {'content': 'x', 'tool_calls': []}},
        ])
        adapter._chat = lambda messages: next(replies, {'message': {}})
        with tempfile.TemporaryDirectory() as temp:
            res = adapter.run_attempt(Path(temp), 'task', 'inst')
            self.assertIn('plain text', res.response_text)

    def test_run_attempt_step_limit(self):
        adapter = OllamaToolAdapter(model='m', base_url='http://x', max_steps=1)
        adapter._chat = lambda messages: {'message': {'tool_calls': [
            {'function': {'name': 'run_shell',
                          'arguments': {'command': 'echo z'}}}]}}
        with tempfile.TemporaryDirectory() as temp:
            res = adapter.run_attempt(Path(temp), 'task', 'inst')
            self.assertEqual(res.status, 'finished')
            self.assertIn('echo z', res.commands)


class RunnerPlumbing(unittest.TestCase):
    def test_canonical_instructions(self):
        text = runner.canonical_instructions()
        self.assertIn('AGENTS.md', text)
        self.assertIn('SKILL.md', text)

    def test_canonical_instructions_sparse_root(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / 'skills' / 'x').mkdir(parents=True)
            (root / 'skills' / 'x' / 'SKILL.md').write_text('s')
            with mock.patch.object(runner, 'ROOT', root):
                text = runner.canonical_instructions()
            self.assertIn('SKILL.md', text)
            self.assertNotIn('AGENTS.md', text)

    def test_build_adapter_all(self):
        self.assertIsInstance(runner.build_adapter('stub', {}), RecordingStubAdapter)
        self.assertIsInstance(runner.build_adapter('ollama', {'model': 'x'}),
                              OllamaToolAdapter)
        with mock.patch.dict('os.environ', {'CURSOR_API_KEY': 'k'}):
            self.assertIsInstance(runner.build_adapter('codex-sdk', {'model': 'x'}),
                                  CodexSdkAdapter)
        with self.assertRaises(SystemExit):
            runner.build_adapter('nope', {})

    def test_main_entrypoint_via_runpy(self):
        with tempfile.TemporaryDirectory() as temp:
            dest = Path(temp) / 'out'
            argv = ['x', str(dest), '--split', 'development', '--adapter', 'stub',
                    '--attempts', '1', '--cases', 'H-04']
            with mock.patch.object(sys, 'argv', argv):
                buf = io.StringIO()
                with mock.patch('sys.stdout', buf):
                    runpy.run_module('eval.runner.runner', run_name='__main__')
            report = json.loads((dest / 'result.json').read_text())
            self.assertEqual(len(report['results']), 1)
            self.assertEqual(report['results'][0]['case'], 'H-04')


if __name__ == '__main__':
    unittest.main()

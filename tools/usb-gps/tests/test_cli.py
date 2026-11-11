"""The helper as the app starts it: a child process speaking the protocol on its pipes, with a fake phone."""
import json
import os
import pathlib
import subprocess
import sys
import tempfile
import time
import unittest

from tests.fakes import UDID

ROOT = pathlib.Path(__file__).resolve().parents[1]


class Helper:
    def __init__(self, *args):
        environment = dict(os.environ, PYTHONPATH=str(ROOT))
        self.process = subprocess.Popen([sys.executable, '-m', 'usb_gps_helper', *args], cwd=ROOT,
                                        stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                        env=environment)
        self.hello = self.read()

    def send(self, request_id, op, **fields):
        self.process.stdin.write((json.dumps({'id': request_id, 'op': op, **fields}) + '\n').encode())
        self.process.stdin.flush()

    def read(self):
        line = self.process.stdout.readline()
        if not line:
            raise AssertionError('the helper ended: ' + self.process.stderr.read().decode(errors='replace'))
        return json.loads(line)

    def reply(self, request_id):
        while True:
            line = self.read()
            if line['type'] == 'reply' and line['id'] == request_id:
                return line

    def finish(self, timeout=15):
        self.process.stdin.close()
        try:
            code = self.process.wait(timeout=timeout)
        finally:
            if self.process.poll() is None:
                self.process.kill()
        rest = [json.loads(line) for line in self.process.stdout.read().splitlines()]
        self.process.stdout.close()
        self.process.stderr.close()
        return code, rest


class CliTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.data = pathlib.Path(self.temp.name)
        self.scenario = self.data / 'scenario.json'
        self.scenario.write_text(json.dumps({'devices': [{'udid': UDID}], 'components': 'not-running'}))

    def tearDown(self):
        self.temp.cleanup()

    def test_a_session_streams_and_cleans_up_when_its_input_ends(self):
        helper = Helper('--role', 'session', '--fake', str(self.scenario))
        self.assertEqual(('hello', 2, 'session'), (helper.hello['type'], helper.hello['protocol'], helper.hello['role']))
        helper.send('h', 'hello', protocol=2)
        self.assertTrue(helper.reply('h')['ok'])
        session = {'session': 's1', 'generation': 4}
        helper.send('o', 'open', udid=UDID, **session)
        self.assertTrue(helper.reply('o')['ok'])
        helper.send('a', 'set', latitude=40.758, longitude=-73.9855, **session)
        self.assertEqual({'applied': True, 'acknowledged': 1}, helper.reply('a')['result'])
        helper.send('b', 'set', latitude=1e999, longitude=0, **session)
        self.assertEqual('invalid-coordinates', helper.reply('b')['error']['code'])
        code, rest = helper.finish()
        self.assertEqual(0, code)
        self.assertEqual([('bye', 'eof')], [(e['event'], e['reason']) for e in rest if e['type'] == 'event'])

    def test_close_ends_the_session_helper(self):
        helper = Helper('--role', 'session', '--fake', str(self.scenario))
        helper.send('h', 'hello', protocol=2)
        helper.send('o', 'open', udid=UDID, session='s', generation=1)
        helper.send('c', 'close', session='s', generation=1)
        self.assertEqual({}, helper.reply('c')['result'])
        started = time.monotonic()
        self.assertEqual(0, helper.process.wait(timeout=10))
        self.assertLess(time.monotonic() - started, 10)
        helper.finish()

    def test_the_control_helper_prepares_offline_into_the_named_store(self):
        store = self.data / 'cache' / 'developer-tools'
        helper = Helper('--role', 'control', '--store', str(store), '--fake', str(self.scenario))
        helper.send('h', 'hello', protocol=2)
        helper.send('k', 'components')
        self.assertEqual({'state': 'not-running'}, helper.reply('k')['result'])
        helper.send('p', 'prepare', udid=UDID)
        self.assertTrue(helper.reply('p')['ok'])
        self.assertTrue(any(child.joinpath('manifest.json').exists() for child in store.iterdir()))
        code, _ = helper.finish()
        self.assertEqual(0, code)

    def test_a_silent_stalled_parent_does_not_keep_a_session_alive(self):
        helper = Helper('--role', 'session', '--fake', str(self.scenario), '--idle-timeout', '0.5')
        helper.send('h', 'hello', protocol=2)
        helper.send('o', 'open', udid=UDID, session='s', generation=1)
        self.assertEqual(0, helper.process.wait(timeout=10))
        helper.finish()

    def test_the_self_test_loads_the_device_library_and_touches_no_phone(self):
        result = subprocess.run([sys.executable, '-m', 'usb_gps_helper', '--self-test'], cwd=ROOT, capture_output=True,
                                env=dict(os.environ, PYTHONPATH=str(ROOT)), timeout=60)
        self.assertEqual(0, result.returncode, result.stderr.decode(errors='replace'))
        report = json.loads(result.stdout)
        self.assertEqual(('self-test', True, '11.45.0', 2), (report['type'], report['ok'], report['library'],
                                                              report['protocol']))

    def test_stdout_carries_only_protocol_lines(self):
        helper = Helper('--role', 'session', '--fake', str(self.scenario))
        helper.process.stdin.write(b'\xff\xfe not utf-8\n' + b'x' * 10000 + b'\n')
        helper.send('h', 'hello', protocol=2)
        helper.reply('h')
        _, rest = helper.finish()
        self.assertTrue(all(line['type'] in ('reply', 'event') for line in rest))


if __name__ == '__main__':
    unittest.main()

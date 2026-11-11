import asyncio
import json
import unittest

from tests.fakes import UDID, FakeBackend, Wire
from usb_gps_helper.lines import OVERLONG
from usb_gps_helper.server import Server, SessionHandlers
from usb_gps_helper.session import SimulationSession


class Input:
    def __init__(self, *lines, then_wait=False):
        self.items = list(lines)
        self.then_wait = then_wait

    async def read(self):
        if self.items:
            item = self.items.pop(0)
            await asyncio.sleep(.01)
            return item
        if self.then_wait:
            await asyncio.sleep(3600)
        return None


def hello(request_id='h', version=2):
    return json.dumps({'id': request_id, 'op': 'hello', 'protocol': version})


def op(request_id, name, **fields):
    return json.dumps({'id': request_id, 'op': name, **fields})


SESSION = {'session': 's1', 'generation': 1}


class ServerTests(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        self.backend = FakeBackend()
        self.wire = Wire()

    async def run_server(self, source, idle_timeout=None):
        session = SimulationSession(self.backend, self.wire, rpc_timeout=.2, presence_interval=None)
        server = Server('session', SessionHandlers(session), self.wire, idle_timeout=idle_timeout)
        return await asyncio.wait_for(server.run(source.read), 5)

    async def test_the_helper_says_hello_first_and_wants_one_back(self):
        await self.run_server(Input(op('p', 'ping'), hello(), op('q', 'ping')))
        self.assertEqual('hello', self.wire.lines[0]['type'])
        replies = self.wire.replies()
        self.assertEqual('protocol', replies['p']['error']['code'])
        self.assertTrue(replies['h']['ok'])
        self.assertTrue(replies['q']['ok'])

    async def test_another_protocol_version_is_refused_and_ends_the_helper(self):
        code = await self.run_server(Input(hello(version=1), op('q', 'ping'), then_wait=True))
        self.assertEqual(2, code)
        self.assertEqual('protocol', self.wire.replies()['h']['error']['code'])
        self.assertNotIn('q', self.wire.replies())

    async def test_bad_lines_get_protocol_errors_and_the_helper_goes_on(self):
        await self.run_server(Input(hello(), 'nonsense', OVERLONG, op('q', 'ping')))
        failures = [line for line in self.wire.lines if line['type'] == 'reply' and not line['ok']]
        self.assertEqual([None, None], [line['id'] for line in failures])
        self.assertEqual({'protocol'}, {line['error']['code'] for line in failures})
        self.assertTrue(self.wire.replies()['q']['ok'])

    async def test_a_bad_coordinate_is_refused_with_its_id_before_the_phone(self):
        lines = [hello(), op('o', 'open', udid=UDID, **SESSION),
                 '{"id":"n","op":"set","session":"s1","generation":1,"latitude":NaN,"longitude":0}']
        await self.run_server(Input(*lines))
        self.assertEqual('invalid-coordinates', self.wire.replies()['n']['error']['code'])
        self.assertEqual([], self.backend.driver.calls)

    async def test_an_id_still_waiting_for_its_reply_cannot_be_reused(self):
        self.backend.driver.set_delay = .1
        lines = [hello(), op('o', 'open', udid=UDID, **SESSION),
                 op('a', 'set', latitude=1, longitude=2, **SESSION), op('a', 'close', **SESSION)]
        await self.run_server(Input(*lines))
        a_replies = [line for line in self.wire.lines if line.get('id') == 'a']
        self.assertEqual('protocol', a_replies[0]['error']['code'])

    async def test_end_of_input_closes_and_says_bye(self):
        lines = [hello(), op('o', 'open', udid=UDID, **SESSION), op('a', 'set', latitude=1, longitude=2, **SESSION)]
        code = await self.run_server(Input(*lines))
        self.assertEqual(0, code)
        self.assertEqual([('set', 1.0, 2.0)], self.backend.driver.calls)
        self.assertEqual('eof', self.wire.events('bye')[0]['reason'])
        self.assertEqual(1, self.backend.closed)

    async def test_a_silent_parent_trips_the_watchdog(self):
        lines = [hello(), op('o', 'open', udid=UDID, **SESSION), op('a', 'set', latitude=1, longitude=2, **SESSION)]
        code = await self.run_server(Input(*lines, then_wait=True), idle_timeout=.2)
        self.assertEqual(0, code)
        self.assertEqual('watchdog', self.wire.events('bye')[0]['reason'])
        self.assertEqual(1, self.backend.closed)

    async def test_close_answers_then_ends_the_helper(self):
        lines = [hello(), op('o', 'open', udid=UDID, **SESSION), op('c', 'close', **SESSION), op('q', 'ping')]
        code = await self.run_server(Input(*lines, then_wait=True))
        self.assertEqual(0, code)
        self.assertEqual({}, self.wire.replies()['c']['result'])
        self.assertNotIn('q', self.wire.replies())



if __name__ == '__main__':
    unittest.main()

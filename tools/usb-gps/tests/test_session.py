import asyncio
import unittest

from tests.fakes import UDID, ConnectionTerminatedError, FakeBackend, Wire, request, session_fields, settle
from usb_gps_helper.protocol import HelperError
from usb_gps_helper.session import SimulationSession


class SessionTests(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.backend = FakeBackend()
        self.driver = self.backend.driver
        self.wire = Wire()
        self.session = SimulationSession(self.backend, self.wire, rpc_timeout=.2, presence_interval=None)
        await self.session.open(request('open', 'o', udid=UDID, **session_fields()))

    def set(self, request_id, latitude, longitude=0.0, **fields):
        self.session.offer(request('set', request_id, latitude=latitude, longitude=longitude, **(fields or session_fields())))

    async def test_points_go_out_in_order_and_are_counted(self):
        self.set('a', 1)
        await settle()
        self.set('b', 2)
        await settle()
        self.assertEqual([('set', 1, 0.0), ('set', 2, 0.0)], self.driver.calls)
        replies = self.wire.replies()
        self.assertEqual({'applied': True, 'acknowledged': 2}, replies['b']['result'])

    async def test_only_the_newest_point_waits_while_a_set_is_slow(self):
        self.driver.set_delay = .05
        for index in range(6):
            self.set(f'p{index}', index)
            await asyncio.sleep(0)
        await asyncio.sleep(.3)
        self.assertEqual([('set', 0, 0.0), ('set', 5, 0.0)], self.driver.calls)
        self.assertEqual(1, self.driver.most_active)
        replies = self.wire.replies()
        self.assertEqual([False] * 4, [replies[f'p{i}']['result']['applied'] for i in range(1, 5)])

    async def test_return_goes_before_a_waiting_point_and_ends_the_stream(self):
        self.driver.set_delay = .05
        self.set('a', 1)
        await asyncio.sleep(0)
        self.set('b', 2)
        result = await self.session.return_to(request('return', 'ret', latitude=50, longitude=30, **session_fields()))
        self.assertEqual({}, result)
        self.set('c', 3)
        await settle()
        self.assertEqual([('set', 1, 0.0), ('set', 50.0, 30.0)], self.driver.calls)
        replies = self.wire.replies()
        self.assertFalse(replies['b']['result']['applied'])
        self.assertEqual('cancelled', replies['c']['error']['code'])

    async def test_clear_waits_for_the_set_in_flight_and_drops_the_waiting_one(self):
        self.driver.set_delay = .05
        self.set('a', 1)
        await asyncio.sleep(0)
        self.set('b', 2)
        await self.session.clear(request('clear', 'c', **session_fields()))
        self.assertEqual([('set', 1, 0.0), ('clear',)], self.driver.calls)
        self.assertFalse(self.session.dirty)
        self.set('d', 4)
        await asyncio.sleep(.1)
        self.assertEqual(('set', 4, 0.0), self.driver.calls[-1])
        self.assertTrue(self.session.dirty)

    async def test_a_lost_set_reply_leaves_cleanup_needed_and_close_clears(self):
        self.driver.set_delay = 1
        self.set('a', 1)
        await asyncio.sleep(.3)
        self.assertEqual('timeout', self.wire.replies()['a']['error']['code'])
        self.assertTrue(self.session.dirty)
        self.driver.set_delay = 0
        result = await self.session.close(request('close', 'x', **session_fields()))
        self.assertEqual({'cleanup': 'done'}, result)
        self.assertEqual(('clear',), self.driver.calls[-1])
        self.assertEqual(1, self.backend.closed)

    async def test_close_without_any_set_needs_no_cleanup(self):
        result = await self.session.close(request('close', 'x', **session_fields()))
        self.assertEqual({'cleanup': 'not-needed'}, result)
        self.assertEqual([], self.driver.calls)

    async def test_a_failed_clear_on_close_is_unconfirmed(self):
        self.set('a', 1)
        await settle()
        self.driver.fail_clear = RuntimeError('no reply')
        result = await self.session.close(request('close', 'x', **session_fields()))
        self.assertEqual({'cleanup': 'unconfirmed'}, result)
        self.assertEqual(1, self.backend.closed)

    async def test_requests_for_another_session_or_generation_never_reach_the_phone(self):
        for fields in (session_fields('old', 1), session_fields('s1', 0)):
            with self.assertRaises(HelperError) as caught:
                await self.session.clear(request('clear', 'c', **fields))
            self.assertEqual('protocol', caught.exception.code)
            self.set('z', 9, **fields)
        await settle()
        self.assertEqual([], self.driver.calls)
        self.assertEqual('protocol', self.wire.replies()['z']['error']['code'])

    async def test_a_dropped_link_is_reported_once_and_ends_the_stream(self):
        self.driver.fail_set = ConnectionTerminatedError('cable out')
        self.set('a', 1)
        await settle()
        self.driver.fail_set = None
        self.set('b', 2)
        await settle()
        self.assertEqual(1, len(self.wire.events('lost')))
        self.assertEqual({'session': 's1', 'generation': 1, 'code': 'link-lost'},
                         {k: self.wire.events('lost')[0][k] for k in ('session', 'generation', 'code')})
        self.assertEqual('link-lost', self.wire.replies()['b']['error']['code'])
        with self.assertRaises(HelperError) as caught:
            await self.session.clear(request('clear', 'c', **session_fields()))
        self.assertEqual('link-lost', caught.exception.code)
        result = await self.session.close(request('close', 'x', **session_fields()))
        self.assertEqual({'cleanup': 'unconfirmed'}, result)

    async def test_end_of_input_clears_a_dirty_session_and_closes_it(self):
        self.set('a', 1)
        await settle()
        await self.session.shutdown('eof')
        self.assertEqual(('clear',), self.driver.calls[-1])
        self.assertEqual(1, self.backend.closed)
        self.assertEqual('eof', self.wire.events('bye')[0]['reason'])

    async def test_a_hanging_clear_does_not_hold_the_shutdown(self):
        self.set('a', 1)
        await settle()
        self.driver.clear_delay = 30
        started = asyncio.get_running_loop().time()
        await self.session.shutdown('eof')
        self.assertLess(asyncio.get_running_loop().time() - started, 2)
        self.assertEqual('unconfirmed', self.wire.events('bye')[0]['cleanup'])


class OpenTests(unittest.IsolatedAsyncioTestCase):
    async def open(self, backend, udid=UDID):
        session = SimulationSession(backend, Wire(), rpc_timeout=.2, presence_interval=None)
        with self.assertRaises(HelperError) as caught:
            await session.open(request('open', 'o', udid=udid, **session_fields()))
        return caught.exception.code, backend

    async def test_two_usb_phones_open_nothing(self):
        code, backend = await self.open(FakeBackend([UDID, 'OTHER']))
        self.assertEqual(('several-devices', 0), (code, backend.opened))

    async def test_another_phone_than_the_asked_one_is_no_device(self):
        code, backend = await self.open(FakeBackend(['OTHER']))
        self.assertEqual(('no-device', 0), (code, backend.opened))

    async def test_a_second_open_is_refused(self):
        backend = FakeBackend()
        session = SimulationSession(backend, Wire(), rpc_timeout=.2, presence_interval=None)
        await session.open(request('open', 'o', udid=UDID, **session_fields()))
        with self.assertRaises(HelperError) as caught:
            await session.open(request('open', 'o2', udid=UDID, **session_fields('s2', 2)))
        self.assertEqual(('protocol', 1), (caught.exception.code, backend.opened))

    async def test_a_phone_that_leaves_usb_is_lost_without_a_set(self):
        backend = FakeBackend()
        wire = Wire()
        session = SimulationSession(backend, wire, rpc_timeout=.2, presence_interval=.01)
        await session.open(request('open', 'o', udid=UDID, **session_fields()))
        backend.udids = []
        await asyncio.sleep(.1)
        self.assertEqual(1, len(wire.events('lost')))
        await session.close(request('close', 'x', **session_fields()))


if __name__ == '__main__':
    unittest.main()

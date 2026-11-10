import asyncio
import pathlib
import tempfile
import threading
import time
import unittest

from tests.fakes import UDID, FakeBackend, FakeDevice, Wire, request
from tests.packaging_fakes import FakeRemote
from usb_gps_helper import packages
from usb_gps_helper.control import ControlOps
from usb_gps_helper.download import Downloads
from usb_gps_helper.protocol import HelperError
from usb_gps_helper.store import Store


class ConnectionFailedToUsbmuxdError(Exception):
    pass


class ControlTests(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.store = Store(pathlib.Path(self.temp.name) / 'cache' / 'developer-tools')
        self.remote = FakeRemote()
        self.backend = FakeBackend()
        self.device = self.backend.devices[UDID]
        self.wire = Wire()
        self.ops = ControlOps(self.backend, self.store,
                              Downloads(self.store, head=self.remote.head, fetch=self.remote.fetch, progress_interval=0),
                              components=lambda: 'not-running', write=self.wire)

    def tearDown(self):
        self.temp.cleanup()

    async def call(self, op, request_id='r', **fields):
        return await self.ops.handle(request(op, request_id, **fields))

    async def code(self, op, **fields):
        with self.assertRaises(HelperError) as caught:
            await self.call(op, **fields)
        return caught.exception.code

    async def test_components_come_from_the_detector(self):
        self.assertEqual({'state': 'not-running'}, await self.call('components'))

    async def test_discover_lists_the_one_usb_phone(self):
        self.assertEqual({'devices': [{'udid': UDID, 'name': 'iPhone', 'ios': '27.2'}]}, await self.call('discover'))
        self.backend.udids = []
        self.assertEqual({'devices': []}, await self.call('discover'))

    async def test_discover_asks_the_phone_once_while_it_stays_connected(self):
        asked = []
        describe = self.backend.describe

        async def counting(udid):
            asked.append(udid)
            return await describe(udid)
        self.backend.describe = counting
        for _ in range(3):
            await self.call('discover')
        self.assertEqual([UDID], asked)
        self.backend.udids = []
        await self.call('discover')
        self.backend.udids = [UDID]
        await self.call('discover')
        self.assertEqual([UDID, UDID], asked)

    async def test_discover_is_not_held_behind_a_running_prepare(self):
        self.backend.mount_delay = 1
        preparing = asyncio.ensure_future(self.call('prepare', 'p', udid=UDID))
        await asyncio.sleep(.2)
        started = time.monotonic()
        await self.call('discover')
        self.assertLess(time.monotonic() - started, .5)
        await preparing

    async def test_two_usb_phones_are_several_devices_for_every_device_operation(self):
        self.backend.udids = [UDID, 'OTHER']
        for op, fields in (('discover', {}), ('probe', {'udid': UDID}), ('reveal', {'udid': UDID}),
                           ('tools-status', {'udid': UDID}), ('prepare', {'udid': UDID})):
            self.assertEqual('several-devices', await self.code(op, **fields), op)
        self.assertEqual([], self.backend.calls)

    async def test_usbmux_not_answering_is_usbmux_unavailable(self):
        async def down():
            raise ConnectionFailedToUsbmuxdError('refused')
        self.backend.usb_devices = down
        self.assertEqual('usbmux-unavailable', await self.code('discover'))

    async def test_probe_reports_the_phone(self):
        result = await self.call('probe', udid=UDID)
        self.assertEqual({'udid': UDID, 'trusted': True, 'locked': False, 'developerMode': 'on', 'ios': '27.2',
                          'imageMounted': False, 'serviceReady': False}, result)
        self.assertEqual('no-device', await self.code('probe', udid='SOMEONE-ELSE'))

    async def test_reveal_only_reveals(self):
        self.assertEqual({}, await self.call('reveal', udid=UDID))
        self.assertEqual([('reveal', UDID)], self.backend.calls)

    async def test_tools_status_tells_the_package_from_the_image(self):
        self.assertEqual({'packageCached': False, 'imageMounted': False, 'serviceReady': False},
                         await self.call('tools-status', udid=UDID))
        self.assertFalse(self.store.root.exists())
        await self.call('prepare', udid=UDID)
        self.device.imageMounted = False
        self.assertEqual({'packageCached': True, 'imageMounted': False, 'serviceReady': False},
                         await self.call('tools-status', udid=UDID))

    async def test_prepare_downloads_mounts_and_checks_with_progress(self):
        result = await self.call('prepare', 'p', udid=UDID)
        self.assertEqual({'packageId': f'cryptex-{packages.EXPECTED_BUILD}', 'downloaded': True}, result)
        self.assertEqual([('mount', 'cryptex', f'cryptex-{packages.EXPECTED_BUILD}')], self.backend.calls)
        stages = [event['stage'] for event in self.wire.events('progress')]
        self.assertEqual(['download', 'mount', 'check'], sorted(set(stages), key=stages.index))
        self.assertTrue(all(event['id'] == 'p' for event in self.wire.events('progress')))

    async def test_a_cached_package_mounts_without_a_download(self):
        await self.call('prepare', udid=UDID)
        fetched = len(self.remote.fetched)
        self.device.imageMounted = False
        result = await self.call('prepare', udid=UDID)
        self.assertFalse(result['downloaded'])
        self.assertEqual(fetched, len(self.remote.fetched))
        self.assertEqual(2, len([call for call in self.backend.calls if call[0] == 'mount']))

    async def test_an_image_already_mounted_is_probed_again_not_remounted(self):
        self.device.imageMounted = True
        self.assertEqual({'packageId': None, 'downloaded': False}, await self.call('prepare', udid=UDID))
        self.assertEqual([], self.backend.calls)
        self.assertEqual([], self.remote.fetched)

    async def test_a_mounted_image_without_the_service_is_not_ready(self):
        self.device.imageMounted = True
        self.device.serviceReady = False
        self.assertEqual('service-unavailable', await self.code('prepare', udid=UDID))

    async def test_prepare_needs_trust_and_developer_mode_first(self):
        self.device.developerMode = 'off'
        self.assertEqual('developer-mode-off', await self.code('prepare', udid=UDID))
        self.device.trusted = False
        self.assertEqual('not-trusted', await self.code('prepare', udid=UDID))
        self.device.trusted, self.device.locked = True, True
        self.assertEqual('locked', await self.code('prepare', udid=UDID))
        self.assertEqual([], self.remote.fetched)

    async def test_an_old_ios_is_unsupported(self):
        self.device.ios = '16.7'
        self.assertEqual('unsupported-ios', await self.code('prepare', udid=UDID))
        self.assertEqual('unsupported-ios', await self.code('tools-status', udid=UDID))

    async def test_a_damaged_cached_package_is_downloaded_again(self):
        await self.call('prepare', udid=UDID)
        image = self.store.find(packages.CRYPTEX).path / 'Image.dmg'
        image.write_bytes(b'x' * image.stat().st_size)
        self.device.imageMounted = False
        result = await self.call('prepare', udid=UDID)
        self.assertTrue(result['downloaded'])
        self.store.verify(self.store.find(packages.CRYPTEX))

    async def test_cancel_ends_a_download_and_publishes_nothing(self):
        started = threading.Event()
        self.remote.chunk_hook = lambda name: (started.set(), time.sleep(.005))
        task = asyncio.ensure_future(self.call('prepare', 'p', udid=UDID))
        while not started.is_set():
            await asyncio.sleep(.005)
        self.assertEqual({'cancelled': True}, await self.call('cancel', 'c', target='p'))
        with self.assertRaises(HelperError) as caught:
            await task
        self.assertEqual('cancelled', caught.exception.code)
        self.assertEqual({'cancelled': False}, await self.call('cancel', 'c2', target='p'))
        self.assertIsNone(self.store.find(packages.CRYPTEX))
        self.assertEqual([], [call for call in self.backend.calls if call[0] == 'mount'])

    async def test_a_failed_mount_keeps_the_package(self):
        self.backend.mount_error = RuntimeError('TSS said no')
        self.assertEqual('mount-failed', await self.code('prepare', udid=UDID))
        self.assertIsNotNone(self.store.find(packages.CRYPTEX))

    async def test_a_phone_on_17_gets_the_personalized_image(self):
        self.backend.devices = {UDID: FakeDevice(ios='18.1')}
        self.remote.__init__(packages.PERSONALIZED)
        result = await self.call('prepare', udid=UDID)
        self.assertEqual(f'personalized-{packages.EXPECTED_BUILD}', result['packageId'])


if __name__ == '__main__':
    unittest.main()

import pathlib
import tempfile
import unittest
from types import SimpleNamespace

from tests.fakes import UDID
from tests.library_fakes import AlreadyMountedError, InvalidServiceError, Library, PasswordRequiredError
from usb_gps_helper import packages
from usb_gps_helper.device import UsbBackend
from usb_gps_helper.protocol import HelperError


class DeviceTests(unittest.IsolatedAsyncioTestCase):
    def backend(self, **client):
        self.library = Library(**client)
        self.log = self.library.log
        return UsbBackend(self.library.namespace())

    async def test_only_usb_devices_are_listed(self):
        backend = UsbBackend(Library(devices=(('Network', 'WIFI'), ('USB', UDID))).namespace())
        self.assertEqual([UDID], await backend.usb_devices())

    async def test_lockdown_is_usb_only_never_pairs_and_keeps_no_records(self):
        backend = self.backend()
        await backend.describe(UDID)
        kwargs = self.library.lockdown_kwargs[0]
        self.assertEqual((UDID, 'USB', False), (kwargs['serial'], kwargs['connection_type'], kwargs['autopair']))
        folder = kwargs['pairing_records_cache_folder']
        self.assertNotIn('.pymobiledevice3', str(folder))
        self.assertTrue(folder.is_dir())
        self.assertEqual([], list(folder.iterdir()))
        self.assertIn('lockdown-close', self.log)
        backend.close()
        self.assertFalse(folder.exists())

    async def test_describe_gives_the_name_and_ios(self):
        self.assertEqual({'name': 'Phone', 'ios': '27.2'}, await self.backend().describe(UDID))

    async def test_an_untrusted_phone_is_probed_without_a_tunnel(self):
        result = await self.backend(paired=False).probe(UDID)
        self.assertEqual((False, 'unknown', None), (result['trusted'], result['developerMode'], result['imageMounted']))
        self.assertNotIn('tunnel', self.log)

    async def test_developer_mode_off_stops_the_probe_there(self):
        result = await self.backend(developer=False).probe(UDID)
        self.assertEqual(('off', None), (result['developerMode'], result['imageMounted']))
        self.assertNotIn('tunnel', self.log)

    async def test_a_mounted_image_is_ready_only_when_the_service_answers(self):
        backend = self.backend()
        self.library.mounted = True
        result = await backend.probe(UDID)
        self.assertEqual({'trusted': True, 'locked': False, 'developerMode': 'on', 'ios': '27.2', 'imageMounted': True,
                          'serviceReady': True}, result)
        self.assertIn(('proxy', True, True), self.log)
        self.library.service_error = InvalidServiceError('no instruments')
        result = await backend.probe(UDID)
        self.assertEqual((True, False), (result['imageMounted'], result['serviceReady']))

    async def test_a_cryptex_the_image_mounter_does_not_list_still_counts(self):
        backend = self.backend()
        self.library.cryptex_installed = True
        self.assertTrue((await backend.probe(UDID))['imageMounted'])

    async def test_no_image_is_no_service(self):
        result = await self.backend().probe(UDID)
        self.assertEqual((False, False), (result['imageMounted'], result['serviceReady']))
        self.assertNotIn('location', self.log)

    async def test_a_locked_phone_is_reported_locked(self):
        backend = self.backend()
        self.library.client.locked = True
        result = await backend.probe(UDID)
        self.assertEqual((True, True), (result['trusted'], result['locked']))

    async def test_reveal_never_enables_or_accepts(self):
        await self.backend().reveal(UDID)
        self.assertIn('reveal', self.log)
        self.assertNotIn('ENABLE', self.log)
        self.assertNotIn('ACCEPT', self.log)

    async def test_reveal_needs_trust(self):
        with self.assertRaises(HelperError) as caught:
            await self.backend(paired=False).reveal(UDID)
        self.assertEqual('not-trusted', caught.exception.code)

    async def test_a_cryptex_is_installed_from_the_store_folder(self):
        folder = pathlib.Path(tempfile.gettempdir())
        package = SimpleNamespace(path=folder, id='cryptex-x')
        await self.backend().mount(UDID, packages.CRYPTEX, package)
        self.assertIn(('install-cryptex', folder), self.log)

    async def test_an_image_mounted_meanwhile_is_no_failure(self):
        backend = self.backend()
        self.library.mount_error = AlreadyMountedError()
        await backend.mount(UDID, packages.CRYPTEX, SimpleNamespace(path=pathlib.Path('pkg'), id='c'))

    async def test_the_location_opens_over_the_same_usb_lockdown_and_closes_in_order(self):
        backend = self.backend()
        self.library.mounted = True
        async with backend.location(UDID) as location:
            self.assertIsNotNone(location)
        self.assertEqual(['location-close', 'dvt-close', 'rsd-close', 'dial-close', 'tunnel-close', 'proxy-close',
                          'lockdown-close'], [entry for entry in self.log if str(entry).endswith('-close')])

    async def test_the_location_needs_developer_mode_and_the_image(self):
        for setup, code in ((dict(developer=False), 'developer-mode-off'), (dict(), 'image-missing'),
                            (dict(paired=False), 'not-trusted'), (dict(version='26.4'), 'unsupported-ios')):
            backend = self.backend(**setup)
            with self.assertRaises(HelperError) as caught:
                async with backend.location(UDID):
                    pass
            self.assertEqual(code, caught.exception.code, setup)
            self.assertIn('lockdown-close', self.log)

    async def test_a_failed_tunnel_handshake_closes_what_was_opened(self):
        backend = self.backend()
        self.library.mounted = True
        self.library.handshake_error = ConnectionError('handshake')
        with self.assertRaises(Exception):
            async with backend.location(UDID):
                pass
        for closed in ('dial-close', 'tunnel-close', 'proxy-close', 'lockdown-close'):
            self.assertIn(closed, self.log)

    async def test_a_tunnel_outside_this_process_is_refused(self):
        backend = self.backend()
        self.library.mounted = True
        self.library.tun = object()
        with self.assertRaises(HelperError):
            async with backend.location(UDID):
                pass
        self.assertNotIn('rsd', self.log)


if __name__ == '__main__':
    unittest.main()

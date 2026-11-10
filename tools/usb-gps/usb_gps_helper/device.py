"""The real backend: one iPhone over Apple's usbmux, by its exact UDID and the USB connection only.

The helper never pairs (autopair off: Trust is the phone's and Apple Devices' business) and keeps no pair records:
pymobiledevice3 reads them from usbmuxd, and its record folder is an empty private one, removed on close. Reveal is
AMFI's reveal alone, never enable or the post-restart accept (F-07). An image counts as mounted when the image mounter
lists it or cryptexd has the DDI cryptex installed; ready means the location service channel opens (V26).
"""
import asyncio
import shutil
import tempfile
from contextlib import AsyncExitStack, asynccontextmanager, suppress
from pathlib import Path

from . import library, packages
from .errors import classify
from .protocol import HelperError
from .tunnel import developer_services

CLOSE_TIMEOUT = 3.0


def _named(error, name):
    return any(kind.__name__ == name for kind in type(error).__mro__)


class UsbBackend:
    def __init__(self, deps=None):
        self.deps = deps if deps is not None else library.load()
        self._pairing = None

    def close(self):
        if self._pairing is not None:
            shutil.rmtree(self._pairing, ignore_errors=True)
            self._pairing = None

    def _pairing_folder(self):
        if self._pairing is None:
            self._pairing = Path(tempfile.mkdtemp(prefix='chairplay-usb-'))
        return self._pairing

    async def usb_devices(self):
        devices = await self.deps.list_devices(usbmux_address=library.USBMUX_ADDRESS)
        return [device.serial for device in devices if device.connection_type == 'USB']

    async def present(self, udid):
        return udid in await self.usb_devices()

    @asynccontextmanager
    async def _lockdown(self, udid):
        client = await self.deps.create_lockdown(
            serial=udid, connection_type='USB', autopair=False, usbmux_address=library.USBMUX_ADDRESS,
            pairing_records_cache_folder=self._pairing_folder())
        try:
            yield client
        finally:
            with suppress(Exception):
                await asyncio.wait_for(client.close(), CLOSE_TIMEOUT)

    async def _require(self, client):
        if not client.paired:
            raise HelperError('not-trusted', 'The iPhone does not trust this computer.')
        packages.kind_for(client.product_version)
        if not await client.get_developer_mode_status():
            raise HelperError('developer-mode-off', 'Developer Mode is off.')

    async def _mounted(self, client, rsd):
        if await self.deps.image_mounted(client):
            return True
        return bool(await self.deps.ddi_installed(rsd))

    async def _service(self, rsd):
        try:
            async with self.deps.dvt(rsd) as dvt:
                async with self.deps.location(dvt):
                    return True
        except Exception:
            return False

    async def describe(self, udid):
        async with self._lockdown(udid) as client:
            name = client.all_values.get('DeviceName') or client.display_name or 'iPhone'
            return {'name': name, 'ios': client.product_version}

    async def probe(self, udid):
        result = {'trusted': None, 'locked': None, 'developerMode': 'unknown', 'ios': None, 'imageMounted': None,
                  'serviceReady': None}
        try:
            async with self._lockdown(udid) as client:
                result['ios'] = client.product_version
                result['trusted'] = bool(client.paired)
                if not client.paired:
                    return result
                result['developerMode'] = 'on' if await client.get_developer_mode_status() else 'off'
                result['locked'] = False
                if result['developerMode'] != 'on':
                    return result
                packages.kind_for(client.product_version)
                await self._probe_image(client, result)
        except Exception as error:
            failure = classify(error)
            if failure.code == 'locked':
                result['locked'] = True
            elif failure.code == 'not-trusted':
                result['trusted'] = False
            elif failure.code != 'unsupported-ios':
                raise failure from error
        return result

    async def _probe_image(self, client, result):
        listed = await self.deps.image_mounted(client)
        try:
            async with developer_services(self.deps, client) as rsd:
                mounted = await self._mounted(client, rsd)
                result['imageMounted'] = mounted
                result['serviceReady'] = await self._service(rsd) if mounted else False
        except Exception as error:
            if classify(error).code == 'locked':
                result['locked'] = True
            result['imageMounted'] = True if listed else None

    async def reveal(self, udid):
        async with self._lockdown(udid) as client:
            if not client.paired:
                raise HelperError('not-trusted', 'The iPhone does not trust this computer.')
            await self.deps.amfi(client).reveal_developer_mode_option_in_ui()

    async def mount(self, udid, kind, package):
        async with self._lockdown(udid) as client:
            await self._require(client)
            try:
                async with developer_services(self.deps, client) as rsd:
                    await self.deps.install_cryptex(rsd, package.path)
            except Exception as error:
                if not _named(error, 'AlreadyMountedError'):
                    raise

    async def check_service(self, udid):
        async with self._lockdown(udid) as client:
            await self._require(client)
            async with developer_services(self.deps, client) as rsd:
                return await self._mounted(client, rsd) and await self._service(rsd)

    @asynccontextmanager
    async def location(self, udid):
        async with AsyncExitStack() as stack:
            client = await stack.enter_async_context(self._lockdown(udid))
            await self._require(client)
            rsd = await stack.enter_async_context(developer_services(self.deps, client))
            if not await self._mounted(client, rsd):
                raise HelperError('image-missing', 'The developer image is not mounted.')
            dvt = await stack.enter_async_context(self.deps.dvt(rsd))
            yield await stack.enter_async_context(self.deps.location(dvt))

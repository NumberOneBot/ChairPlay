"""A fake phone backend: the tests' and the dry run's (--fake), so the helper is exercised with no USB device.

A scenario is a JSON object: {"devices": [{"udid":..,"name":..,"ios":..,"trusted":..,"locked":..,
"developerMode":..,"imageMounted":..,"serviceReady":..}], "components": "ready", "setDelay": 0, "downloads": "fake"}.
"""
import asyncio
import json
from contextlib import asynccontextmanager
from dataclasses import dataclass

DEFAULT_UDID = '00008030-001A2D3E0C38802E'


@dataclass
class FakeDevice:
    udid: str = DEFAULT_UDID
    name: str = 'iPhone'
    ios: str = '27.2'
    trusted: bool = True
    locked: bool = False
    developerMode: str = 'on'
    imageMounted: bool = False
    serviceReady: bool = True


class FakeDriver:
    def __init__(self):
        self.calls = []
        self.set_delay = 0
        self.clear_delay = 0
        self.fail_set = None
        self.fail_clear = None
        self.active = 0
        self.most_active = 0

    async def _call(self, entry, delay, failure):
        self.active += 1
        self.most_active = max(self.most_active, self.active)
        try:
            self.calls.append(entry)
            if delay:
                await asyncio.sleep(delay)
            if failure is not None:
                raise failure
        finally:
            self.active -= 1

    async def set(self, latitude, longitude):
        await self._call(('set', latitude, longitude), self.set_delay, self.fail_set)

    async def clear(self):
        await self._call(('clear',), self.clear_delay, self.fail_clear)


class FakeBackend:
    def __init__(self, udids=None, devices=None):
        if devices is None:
            devices = [FakeDevice(udid=udid) for udid in (udids if udids is not None else [DEFAULT_UDID])]
        self.devices = {device.udid: device for device in devices}
        self.driver = FakeDriver()
        self.calls = []
        self.opened = 0
        self.closed = 0
        self.open_error = None
        self.mount_error = None
        self.mount_delay = 0

    @property
    def udids(self):
        return list(self.devices)

    @udids.setter
    def udids(self, udids):
        self.devices = {udid: self.devices.get(udid) or FakeDevice(udid=udid) for udid in udids}

    async def usb_devices(self):
        return list(self.devices)

    async def present(self, udid):
        return udid in self.devices

    async def describe(self, udid):
        device = self.devices[udid]
        return {'name': device.name, 'ios': device.ios}

    async def probe(self, udid):
        device = self.devices[udid]
        known = device.trusted and not device.locked
        on = known and device.developerMode == 'on'
        return {'trusted': device.trusted, 'locked': device.locked,
                'developerMode': device.developerMode if known else 'unknown', 'ios': device.ios,
                'imageMounted': device.imageMounted if on else None,
                'serviceReady': (device.imageMounted and device.serviceReady) if on else None}

    async def reveal(self, udid):
        self.calls.append(('reveal', udid))

    async def mount(self, udid, kind, package):
        self.calls.append(('mount', kind, package.id))
        if self.mount_delay:
            await asyncio.sleep(self.mount_delay)
        if self.mount_error is not None:
            raise self.mount_error
        self.devices[udid].imageMounted = True

    async def check_service(self, udid):
        device = self.devices[udid]
        return device.imageMounted and device.serviceReady

    @asynccontextmanager
    async def location(self, udid):
        if self.open_error is not None:
            raise self.open_error
        self.opened += 1
        try:
            yield self.driver
        finally:
            self.closed += 1


def from_scenario(text):
    """A FakeBackend and its components state from a scenario's JSON text."""
    scenario = json.loads(text) if text else {}
    devices = [FakeDevice(**item) for item in scenario.get('devices', [{}])]
    backend = FakeBackend(devices=devices)
    backend.driver.set_delay = float(scenario.get('setDelay', 0))
    return backend, scenario.get('components', 'ready')

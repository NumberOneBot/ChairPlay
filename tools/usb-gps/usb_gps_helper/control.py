"""The control helper's operations: Apple's components, the USB phone, its probe, Developer Mode's reveal, and the
developer tools. None of them sends a position.

prepare: probe; an image already mounted is probed again and never unmounted; otherwise a verified cached package is
mounted without a download (a damaged one is replaced), or one is downloaded first; then the location service must
answer. Progress events name the request: stage download (with bytes), mount, check. cancel ends a download at once,
and other stages at their end, as a mount half done on the phone is not ours to break.
"""
import asyncio

from . import packages, protocol
from .errors import classify, describe
from .protocol import HelperError
from .selection import select_device

DEVICE_TIMEOUT = 15.0
MOUNT_TIMEOUT = 150.0
LIST_TIMEOUT = 3.0
_KEPT_ON_MOUNT = {'locked', 'not-trusted', 'developer-mode-off', 'unsupported-ios', 'timeout', 'no-device',
                  'image-missing', 'service-unavailable', 'cancelled'}


def _cancelled():
    return HelperError('cancelled', 'Preparing the developer tools was cancelled.')


def _mount_failure(error):
    if isinstance(error, HelperError):
        return error
    if type(error).__module__.split('.')[0] in ('requests', 'urllib3'):
        return HelperError('mount-failed', describe(error))
    failure = classify(error)
    return failure if failure.code in _KEPT_ON_MOUNT else HelperError('mount-failed', failure.detail)


class ControlOps:
    def __init__(self, backend, store, downloads, components, write, device_timeout=DEVICE_TIMEOUT,
                 mount_timeout=MOUNT_TIMEOUT):
        self.backend = backend
        self.store = store
        self.downloads = downloads
        self.components = components
        self.write = write
        self.device_timeout = device_timeout
        self.mount_timeout = mount_timeout
        self._device = asyncio.Lock()
        self._cancels = {}
        self._described = {}
        self.finished = False

    def holds(self, request_id):
        return False

    def handle(self, request):
        operation = {'components': self._components, 'discover': self._discover, 'probe': self._probe,
                     'reveal': self._reveal, 'tools-status': self._tools_status, 'prepare': self._prepare,
                     'cancel': self._cancel}[request.op]
        return operation(request)

    async def shutdown(self, reason):
        for cancel in self._cancels.values():
            cancel.set()
        self.write(protocol.event('bye', reason=reason))

    async def _bounded(self, work, timeout=None):
        try:
            return await asyncio.wait_for(work, timeout or self.device_timeout)
        except Exception as error:
            raise classify(error) from error

    async def _select(self, udid):
        return select_device(await self._bounded(self.backend.usb_devices()), udid)

    async def _components(self, request):
        return {'state': await asyncio.to_thread(self.components)}

    async def _discover(self, request):
        udids = await self._bounded(self.backend.usb_devices(), LIST_TIMEOUT)
        if len(udids) > 1:
            raise HelperError('several-devices', 'More than one iPhone is connected by USB.')
        # Polled about once a second: a phone is asked its name and iOS once while it stays connected.
        self._described = {udid: info for udid, info in self._described.items() if udid in udids}
        devices = []
        for udid in udids:
            info = self._described.get(udid)
            if info is None:
                info = self._described[udid] = await self._bounded(self.backend.describe(udid))
            devices.append({'udid': udid, 'name': info.get('name'), 'ios': info.get('ios')})
        return {'devices': devices}

    async def _probe(self, request):
        udid = request.fields['udid']
        async with self._device:
            await self._select(udid)
            return {'udid': udid, **await self._bounded(self.backend.probe(udid))}

    async def _reveal(self, request):
        udid = request.fields['udid']
        async with self._device:
            await self._select(udid)
            await self._bounded(self.backend.reveal(udid))
        return {}

    async def _tools_status(self, request):
        udid = request.fields['udid']
        async with self._device:
            await self._select(udid)
            info = await self._bounded(self.backend.probe(udid))
        kind = packages.kind_for(info['ios'])
        return {'packageCached': self.store.find(kind) is not None, 'imageMounted': bool(info['imageMounted']),
                'serviceReady': bool(info['serviceReady'])}

    async def _cancel(self, request):
        cancel = self._cancels.get(request.fields['target'])
        if cancel is None or cancel.is_set():
            return {'cancelled': False}
        cancel.set()
        return {'cancelled': True}

    async def _prepare(self, request):
        udid = request.fields['udid']
        cancel = self._cancels[request.id] = asyncio.Event()
        try:
            async with self._device:
                await self._select(udid)
                info = await self._bounded(self.backend.probe(udid))
            _require_developer_access(info)
            kind = packages.kind_for(info['ios'])
            if info['imageMounted']:
                await self._check(request, udid)
                return {'packageId': None, 'downloaded': False}
            package, downloaded = await self._package(request, kind, cancel)
            if cancel.is_set():
                raise _cancelled()
            self._progress(request, 'mount')
            async with self._device:
                await self._select(udid)
                try:
                    await asyncio.wait_for(self.backend.mount(udid, kind, package), self.mount_timeout)
                except Exception as error:
                    raise _mount_failure(error) from error
            await self._check(request, udid)
            return {'packageId': package.id, 'downloaded': downloaded}
        finally:
            self._cancels.pop(request.id, None)

    async def _package(self, request, kind, cancel):
        package = self.store.find(kind)
        if package is not None:
            try:
                await asyncio.to_thread(self.store.verify, package)
                return package, False
            except HelperError:
                self.store.remove(package)
        self._progress(request, 'download', 0, None)
        package = await self.downloads.obtain(
            kind, lambda received, total: self._progress(request, 'download', received, total), cancel)
        return package, True

    async def _check(self, request, udid):
        self._progress(request, 'check')
        async with self._device:
            ready = await self._bounded(self.backend.check_service(udid))
        if not ready:
            raise HelperError('service-unavailable', 'The location service does not answer.')

    def _progress(self, request, stage, received=None, total=None):
        self.write(protocol.event('progress', id=request.id, stage=stage, receivedBytes=received, totalBytes=total))


def _require_developer_access(info):
    if info.get('locked'):
        raise HelperError('locked', 'Unlock the iPhone.')
    if info.get('trusted') is False:
        raise HelperError('not-trusted', 'The iPhone does not trust this computer.')
    if info.get('developerMode') != 'on':
        raise HelperError('developer-mode-off', 'Developer Mode is off.')

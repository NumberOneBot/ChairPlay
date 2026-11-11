"""A stand-in for the parts of pymobiledevice3 the real backend uses, recording every call. No device is touched."""
from contextlib import asynccontextmanager
from types import SimpleNamespace

from tests.fakes import UDID


class PasswordRequiredError(Exception):
    pass


class AlreadyMountedError(Exception):
    pass


class InvalidServiceError(Exception):
    pass


class Tun:
    def __init__(self, log):
        self.log = log

    def set_peer(self, address):
        self.log.append('peer')


class Client:
    def __init__(self, log, paired=True, version='27.2', developer=True, name='Phone'):
        self.log = log
        self.paired = paired
        self.product_version = version
        self.all_values = {'DeviceName': name, 'ProductVersion': version}
        self.display_name = 'iPhone 15'
        self.developer = developer
        self.locked = False

    async def get_developer_mode_status(self):
        if self.locked:
            raise PasswordRequiredError('locked')
        return self.developer

    async def close(self):
        self.log.append('lockdown-close')


class Library:
    def __init__(self, devices=(('USB', UDID),), **client):
        self.log = []
        self.lockdown_kwargs = []
        self.devices = [SimpleNamespace(connection_type=kind, serial=serial) for kind, serial in devices]
        self.client = Client(self.log, **client)
        self.mounted = False
        self.cryptex_installed = False
        self.service_error = None
        self.handshake_error = None
        self.mount_error = None
        self.tun_type = Tun
        self.tun = Tun(self.log)
        self.tunnel = SimpleNamespace(USE_USERSPACE_TUNNEL=False, CoreDeviceTunnelProxy=self._proxy_type())

    def namespace(self):
        return SimpleNamespace(list_devices=self.list_devices, create_lockdown=self.create_lockdown, tunnel=self.tunnel,
                               tun_type=self.tun_type, dial_plane=self.dial_plane, rsd=self.rsd, dvt=self.dvt,
                               location=self.location, amfi=self.amfi, image_mounted=self.image_mounted,
                               ddi_installed=self.ddi_installed,
                               install_cryptex=self.install_cryptex)

    async def list_devices(self, usbmux_address=None):
        return list(self.devices)

    async def create_lockdown(self, **kwargs):
        self.lockdown_kwargs.append(kwargs)
        self.log.append('lockdown')
        return self.client

    def _proxy_type(self):
        library = self

        class Proxy:
            @classmethod
            async def create(cls, lockdown):
                library.log.append(('proxy', lockdown is library.client, library.tunnel.USE_USERSPACE_TUNNEL))
                return cls()

            async def close(self):
                library.log.append('proxy-close')

            @asynccontextmanager
            async def start_tcp_tunnel(self):
                library.log.append('tunnel')
                try:
                    yield SimpleNamespace(client=SimpleNamespace(tun=library.tun), address='fd00::1', port=58783,
                                          auxiliary_metadata=None)
                finally:
                    library.log.append('tunnel-close')
        return Proxy

    @asynccontextmanager
    async def dial_plane(self, tun, address):
        self.log.append('dial')
        try:
            yield SimpleNamespace(dial=None)
        finally:
            self.log.append('dial-close')

    def rsd(self, address, open_connection=None, auxiliary_metadata=None):
        library = self

        class Rsd:
            product_version = library.client.product_version

            async def connect(self):
                library.log.append('rsd')
                if library.handshake_error is not None:
                    raise library.handshake_error

            async def close(self):
                library.log.append('rsd-close')
        return Rsd()

    @asynccontextmanager
    async def dvt(self, rsd):
        self.log.append('dvt')
        try:
            yield 'dvt'
        finally:
            self.log.append('dvt-close')

    @asynccontextmanager
    async def location(self, dvt):
        if self.service_error is not None:
            raise self.service_error
        self.log.append('location')
        try:
            yield SimpleNamespace(set=None)
        finally:
            self.log.append('location-close')

    def amfi(self, client):
        library = self

        class Amfi:
            async def reveal_developer_mode_option_in_ui(self):
                library.log.append('reveal')

            async def enable_developer_mode(self, *args):
                library.log.append('ENABLE')

            async def enable_developer_mode_post_restart(self):
                library.log.append('ACCEPT')
        return Amfi()

    async def image_mounted(self, client):
        return self.mounted

    async def ddi_installed(self, rsd):
        return self.cryptex_installed

    async def install_cryptex(self, rsd, restore_dir):
        self.log.append(('install-cryptex', restore_dir))
        if self.mount_error is not None:
            raise self.mount_error

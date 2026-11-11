"""The pinned pymobiledevice3 parts the real backend uses, gathered in one namespace the tests replace.

Every folder is explicit: pair records come from Apple's usbmuxd (the helper never pairs), and developer-tools files
come from the store the app names. Nothing here falls back to pymobiledevice3's home folder (~/.pymobiledevice3).
"""
import os
from importlib.metadata import PackageNotFoundError, version
from types import SimpleNamespace

from .protocol import HelperError

PMD_VERSION = '11.45.0'
USBMUX_ADDRESS = '127.0.0.1:27015' if os.name == 'nt' else None


def load():
    try:
        installed = version('pymobiledevice3')
    except PackageNotFoundError:
        installed = None
    if installed != PMD_VERSION:
        raise HelperError('helper-failed', f'The helper needs pymobiledevice3 {PMD_VERSION}, not {installed}.')
    from pymobiledevice3.lockdown import create_using_usbmux
    from pymobiledevice3.remote import tunnel_service
    from pymobiledevice3.remote.remote_service_discovery import RemoteServiceDiscoveryService
    from pymobiledevice3.remote.userspace_tunnel import UserspaceDialPlane, UserspaceTun
    from pymobiledevice3.services.amfi import AmfiService
    from pymobiledevice3.services.cryptexd import DDI_CRYPTEX_IDENTIFIER, CryptexdService
    from pymobiledevice3.services.dvt.instruments.dvt_provider import DvtProvider
    from pymobiledevice3.services.dvt.instruments.location_simulation import LocationSimulation
    from pymobiledevice3.services.mobile_image_mounter import (
        MobileImageMounterService, PersonalizedImageMounter, image_type_for_device,
    )
    from pymobiledevice3.usbmux import list_devices

    async def image_mounted(client):
        async with MobileImageMounterService(lockdown=client) as mounter:
            return await mounter.is_image_mounted(image_type_for_device(client))

    async def ddi_installed(rsd):
        installed = await CryptexdService(rsd).copy_installed()
        return any(item.identifier == DDI_CRYPTEX_IDENTIFIER for item in installed)

    async def mount_personalized(client, image, manifest, trust_cache):
        async with PersonalizedImageMounter(lockdown=client) as mounter:
            await mounter.mount(image, manifest, trust_cache)

    async def install_cryptex(rsd, restore_dir):
        await CryptexdService(rsd).auto_install_ddi(restore_dir=restore_dir)

    return SimpleNamespace(
        list_devices=list_devices, create_lockdown=create_using_usbmux, tunnel=tunnel_service, tun_type=UserspaceTun,
        dial_plane=UserspaceDialPlane, rsd=RemoteServiceDiscoveryService, dvt=DvtProvider, location=LocationSimulation,
        amfi=AmfiService, image_mounted=image_mounted, ddi_installed=ddi_installed,
        mount_personalized=mount_personalized, install_cryptex=install_cryptex)

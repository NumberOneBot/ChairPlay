"""Which phone a request may reach: USB only, the exact UDID, and only while it is the one USB phone (D6)."""
from .protocol import HelperError


def select_device(udids, udid):
    """[udids] are the USB-attached devices usbmux lists; raises unless [udid] is the only one."""
    if len(udids) > 1:
        raise HelperError('several-devices', 'More than one iPhone is connected by USB.')
    if udid not in udids:
        raise HelperError('no-device', 'The iPhone is not connected by USB.')
    return udid

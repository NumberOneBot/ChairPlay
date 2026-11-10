"""Which phone a request may reach: one USB-attached by its exact UDID. Of several, the app takes the first (D11)."""
from .protocol import HelperError


def select_device(udids, udid):
    """[udids] are the USB-attached devices usbmux lists; raises unless [udid] is one of them."""
    if udid not in udids:
        raise HelperError('no-device', 'The iPhone is not connected by USB.')
    return udid

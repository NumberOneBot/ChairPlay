"""ChairPlay's USB GPS helper: a long-lived JSON-lines process between the app and pymobiledevice3 (see protocol)."""
from importlib.metadata import PackageNotFoundError, version

VERSION = '2.0.0'


def library_version():
    try:
        return version('pymobiledevice3')
    except PackageNotFoundError:
        return ''

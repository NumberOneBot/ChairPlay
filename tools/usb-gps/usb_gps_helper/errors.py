"""Turns pymobiledevice3's and the OS's exceptions into the protocol's stable codes.

Matched by class name along the exception's MRO, so the table needs no import of the library and the tests can raise
look-alikes. The first name found wins: PasswordRequiredError is a PairingError, and is "locked", not "not-trusted".
"""
import errno
import re

from .protocol import HelperError

_BY_NAME = {
    'TimeoutError': 'timeout',
    'CancelledError': 'cancelled',
    'ConnectionFailedToUsbmuxdError': 'usbmux-unavailable',
    'PasswordRequiredError': 'locked',
    'PasscodeRequiredError': 'locked',
    'NotPairedError': 'not-trusted',
    'NotTrustedError': 'not-trusted',
    'PairingError': 'not-trusted',
    'FatalPairingError': 'not-trusted',
    'InvalidHostIDError': 'not-trusted',
    'DeveloperModeIsNotEnabledError': 'developer-mode-off',
    'DeveloperModeError': 'developer-mode-off',
    'NotMountedError': 'image-missing',
    'DeviceNotFoundError': 'no-device',
    'NoDeviceConnectedError': 'no-device',
    'DeviceFeatureNotSupportedError': 'unsupported-ios',
    'DeviceVersionNotSupportedError': 'unsupported-ios',
    'OSNotSupportedError': 'unsupported-ios',
    'RSDRequiredError': 'unsupported-ios',
    'InvalidServiceError': 'service-unavailable',
    'StartServiceError': 'service-unavailable',
    'UnrecognizedSelectorError': 'service-unavailable',
    'CryptexdError': 'mount-failed',
    'TSSError': 'mount-failed',
    'MissingManifestError': 'mount-failed',
    'NoSuchBuildIdentityError': 'mount-failed',
    'ConnectionTerminatedError': 'link-lost',
    'ChannelClosedError': 'link-lost',
    'IncompleteReadError': 'link-lost',
    'MuxException': 'link-lost',
    'ConnectionError': 'link-lost',
}

_UDID = re.compile(r'\b[0-9A-Fa-f]{8}-[0-9A-Fa-f]{16}\b|\b[0-9A-Fa-f]{40}\b')


def redact(text):
    """Hides UDIDs: logs and details must not carry them."""
    return _UDID.sub('<udid>', text)


def classify(error, default='helper-failed'):
    """The HelperError for [error]; [default] when nothing in the table names it."""
    if isinstance(error, HelperError):
        return error
    if isinstance(error, OSError) and error.errno == errno.ENOSPC:
        return HelperError('disk-full', 'The disk is full.')
    for kind in type(error).__mro__:
        code = _BY_NAME.get(kind.__name__)
        if code is not None:
            return HelperError(code, describe(error))
    return HelperError(default, describe(error))


def describe(error):
    message = redact(str(error))[:200]
    return f'{type(error).__name__}: {message}' if message else type(error).__name__

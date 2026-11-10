"""The helper's command line, as the app starts it:

    helper --role control --store <data>/cache/developer-tools     discovery, probe, reveal, developer tools
    helper --role session [--idle-timeout 30]                      one location simulation session

--fake <scenario.json> runs either role against a fake phone and a fake package server (a dry run; see fake.py).
The process ends at the end of its input, a session's close, or the session watchdog, after a bounded cleanup.
"""
import argparse
import asyncio
import logging
import math
import os
import sys
from pathlib import Path

from . import logs

log = logging.getLogger('usb_gps_helper')


def _seconds(value):
    number = float(value)
    if not math.isfinite(number) or not 0 <= number <= 3600:
        raise argparse.ArgumentTypeError('use 0 to 3600 seconds')
    return number


def parser():
    root = argparse.ArgumentParser(prog='helper', description="ChairPlay's USB GPS helper (protocol 2).")
    root.add_argument('--role', choices=('control', 'session'))
    root.add_argument('--self-test', action='store_true',
                      help='Load every part the real backend needs, print one JSON line and end; no device is opened.')
    root.add_argument('--store', type=Path, help='The developer tools folder (control role).')
    root.add_argument('--fake', type=Path, help='A fake phone scenario, for dry runs.')
    root.add_argument('--idle-timeout', type=_seconds, default=30.0,
                      help='Session role: end with a cleanup when the app is silent this long (0: never).')
    return root


class _Broken:
    """Stands for the backend when pymobiledevice3 cannot load: every call fails with the same error."""

    def __init__(self, error):
        self.error = error

    def __getattr__(self, name):
        async def fail(*_args, **_kwargs):
            raise self.error
        return fail


def _backend(args):
    if args.fake is not None:
        from .fake import from_scenario
        backend, components = from_scenario(args.fake.read_text(encoding='utf-8'))
        return backend, (lambda: components)
    from .components import detect
    from .device import UsbBackend
    from .protocol import HelperError
    try:
        return UsbBackend(), detect
    except HelperError as error:
        log.error('the device library is unusable: %s', error.detail)
        return _Broken(error), (lambda: 'unknown')


async def serve(args):
    from .lines import StdinLines, StdoutLines
    from .server import Server, SessionHandlers
    write = StdoutLines()
    backend, components = _backend(args)
    try:
        if args.role == 'control':
            handlers, idle = _control(args, backend, components, write), None
        else:
            from .session import SimulationSession
            handlers, idle = SessionHandlers(SimulationSession(backend, write)), (args.idle_timeout or None)
        return await Server(args.role, handlers, write, idle_timeout=idle).run(StdinLines().read)
    finally:
        close = getattr(backend, 'close', None)
        if callable(close) and not isinstance(backend, _Broken):
            close()


def _control(args, backend, components, write):
    from .control import ControlOps
    from .download import Downloads
    from .store import Store
    store = Store(args.store)
    if args.fake is not None:
        from .fake_remote import FakeRemote
        from .packages import PERSONALIZED
        remote = FakeRemote()
        remote.serve(PERSONALIZED)
        downloads = Downloads(store, head=remote.head, fetch=remote.fetch)
    else:
        downloads = Downloads(store)
    return ControlOps(backend, store, downloads, components, write)


def self_test():
    """For a frozen build: every module the real backend imports loads, and the pinned library is the one inside."""
    import json
    from . import VERSION, components, control, device, download, library, library_version, packages, protocol
    library.load()
    components.detect(usbmux=lambda: False, installed=lambda: [], running=lambda: [])
    assert device and control and download and packages.EXPECTED_BUILD
    print(json.dumps({'type': 'self-test', 'ok': True, 'protocol': protocol.PROTOCOL, 'helper': VERSION,
                      'library': library_version(), 'ddiBuild': packages.EXPECTED_BUILD}), flush=True)
    return 0


def run(argv=None):
    args = parser().parse_args(argv)
    if args.self_test:
        return self_test()
    if args.role is None:
        parser().error('give --role control or --role session')
    if args.role == 'control' and args.store is None:
        parser().error('the control role needs --store')
    logs.configure()
    try:
        return asyncio.run(serve(args))
    except KeyboardInterrupt:
        return 130


def main(argv=None):
    code = run(argv)
    # Daemon threads (stdin, downloads) may still sit in a blocking call; the protocol work is done and flushed.
    sys.stdout.flush()
    sys.stderr.flush()
    os._exit(code)

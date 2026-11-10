"""Helpers for the tests; the fake phone itself is usb_gps_helper.fake. Nothing here opens a USB device."""
import asyncio
import json

from usb_gps_helper.fake import DEFAULT_UDID as UDID, FakeBackend, FakeDevice, FakeDriver  # noqa: F401
from usb_gps_helper.protocol import Request


class ConnectionTerminatedError(Exception):
    """Named like pymobiledevice3's, which errors.classify recognises by name."""


class Wire:
    """Collects the lines a helper writes, parsed."""

    def __init__(self):
        self.lines = []

    def __call__(self, text):
        self.lines.append(json.loads(text))

    def replies(self):
        return {line['id']: line for line in self.lines if line['type'] == 'reply'}

    def events(self, name):
        return [line for line in self.lines if line['type'] == 'event' and line['event'] == name]


def request(op, request_id='r', **fields):
    return Request(request_id, op, fields)


def session_fields(session='s1', generation=1):
    return {'session': session, 'generation': generation}


async def settle(times=20):
    for _ in range(times):
        await asyncio.sleep(0)

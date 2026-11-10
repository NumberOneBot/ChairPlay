"""Diagnostics on stderr only, bounded and without UDIDs; stdout stays the protocol's alone.

The helper logs operations and outcomes, never coordinates, pair records or device-info dumps; pymobiledevice3's own
loggers are held at ERROR. Once LIMIT bytes went out, one last line says so and the rest is dropped.
"""
import logging
import sys

from .errors import redact

LIMIT = 256 * 1024


class BoundedHandler(logging.StreamHandler):
    def __init__(self, stream=None, limit=LIMIT):
        super().__init__(stream if stream is not None else sys.stderr)
        self.limit = limit
        self.written = 0
        self.stopped = False

    def emit(self, record):
        if self.stopped:
            return
        try:
            text = redact(self.format(record))
        except Exception:
            return
        if self.written + len(text) > self.limit:
            self.stopped = True
            text = 'helper: diagnostics limit reached; later lines are dropped'
        self.written += len(text) + 1
        try:
            self.stream.write(text + '\n')
            self.flush()
        except (OSError, ValueError):
            self.stopped = True


def configure(level=logging.WARNING, stream=None, limit=LIMIT):
    handler = BoundedHandler(stream, limit)
    handler.setFormatter(logging.Formatter('%(asctime)s %(levelname)s %(name)s: %(message)s'))
    root = logging.getLogger()
    for old in list(root.handlers):
        root.removeHandler(old)
    root.addHandler(handler)
    root.setLevel(level)
    for name in ('pymobiledevice3', 'developer_disk_image', 'urllib3', 'asyncio', 'pytcp', 'qh3'):
        logging.getLogger(name).setLevel(logging.ERROR)
    return handler

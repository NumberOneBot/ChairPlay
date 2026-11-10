"""stdin and stdout as bounded protocol lines, without an executor thread holding up the interpreter's exit.

A daemon thread reads stdin into a small queue and wakes the event loop. A line over MAX_LINE bytes is read to its end
and dropped, and the reader gets OVERLONG in its place, so its tail is never taken as another request. End of input,
also what a dead parent leaves, is None.
"""
import asyncio
import queue
import sys
import threading

from .protocol import MAX_LINE

OVERLONG = object()
UNREADABLE = object()


class StdinLines:
    def __init__(self, stream=None, loop=None):
        self.stream = stream if stream is not None else sys.stdin.buffer
        self.loop = loop or asyncio.get_running_loop()
        self.items = queue.Queue(maxsize=64)
        self.ready = asyncio.Event()
        threading.Thread(target=self._pump, name='helper-stdin', daemon=True).start()

    def _put(self, item):
        self.items.put(item)
        try:
            self.loop.call_soon_threadsafe(self.ready.set)
        except RuntimeError:
            pass  # the loop has ended

    def _pump(self):
        try:
            while True:
                raw = self.stream.readline(MAX_LINE + 1)
                if not raw:
                    break
                if len(raw) > MAX_LINE and not raw.endswith(b'\n'):
                    while raw and not raw.endswith(b'\n'):
                        raw = self.stream.readline(MAX_LINE + 1)
                    self._put(OVERLONG)
                    continue
                try:
                    self._put(raw.decode('utf-8').rstrip('\r\n'))
                except UnicodeDecodeError:
                    self._put(UNREADABLE)
        except (OSError, ValueError):
            pass
        self._put(None)

    async def read(self):
        while True:
            try:
                return self.items.get_nowait()
            except queue.Empty:
                self.ready.clear()
                await self.ready.wait()


class StdoutLines:
    """Writes one protocol line at a time; a closed pipe is ignored, as the end of input follows it."""

    def __init__(self, stream=None):
        self.stream = stream if stream is not None else sys.stdout.buffer
        self.lock = threading.Lock()

    def __call__(self, text):
        with self.lock:
            try:
                self.stream.write(text.encode('ascii') + b'\n')
                self.stream.flush()
            except (OSError, ValueError):
                pass

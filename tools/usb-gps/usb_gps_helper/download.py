"""Downloads the developer-tools package into the store.

The transfer runs in a daemon thread (requests is blocking; a daemon thread never holds up the helper's exit), into a
partial folder the store then publishes whole; a failed or cancelled one deletes its folder. Errors: download-failed,
disk-full, verification-failed (not a package), store-unavailable, cancelled.
"""
import asyncio
import errno
import threading
import time

from pymobiledevice3.safe_paths import resolve_device_path

from . import packages
from .errors import describe
from .protocol import HelperError

CONNECT_TIMEOUT = 10
READ_TIMEOUT = 30
_HEADERS = {'Accept-Encoding': 'identity'}


def http_head(url):
    import requests
    try:
        response = requests.head(url, allow_redirects=True, timeout=CONNECT_TIMEOUT, headers=_HEADERS)
        length = response.headers.get('Content-Length') if response.status_code == 200 else None
        return int(length) if length is not None else None
    except (requests.RequestException, ValueError):
        return None


def http_fetch(url, destination, on_bytes, cancelled):
    import requests
    destination.parent.mkdir(parents=True, exist_ok=True)
    received = 0
    try:
        with requests.get(url, stream=True, timeout=(CONNECT_TIMEOUT, READ_TIMEOUT), headers=_HEADERS) as response:
            if response.status_code != 200:
                raise HelperError('download-failed', f'The server answered {response.status_code}.')
            expected = response.headers.get('Content-Length')
            with open(destination, 'wb') as out:
                for chunk in response.iter_content(1 << 16):
                    if cancelled():
                        raise HelperError('cancelled', 'The download was cancelled.')
                    out.write(chunk)
                    received += len(chunk)
                    on_bytes(len(chunk))
    except requests.RequestException as error:
        raise HelperError('download-failed', describe(error)) from error
    if received == 0 or (expected is not None and expected.isdigit() and int(expected) != received):
        raise HelperError('download-failed', 'The download was cut short.')
    return received


def _failure(error):
    if isinstance(error, HelperError):
        return error
    if isinstance(error, OSError) and error.errno == errno.ENOSPC:
        return HelperError('disk-full', 'The disk is full.')
    return HelperError('download-failed', describe(error))


class _Job:
    def __init__(self, loop, progress):
        self.loop = loop
        self.progress = progress
        self.future = loop.create_future()
        self.future.add_done_callback(lambda future: future.cancelled() or future.exception())
        self.cancel = threading.Event()
        self.received = 0
        self.total = None
        self.reported = 0.0


class Downloads:
    def __init__(self, store, head=http_head, fetch=http_fetch, progress_interval=.25):
        self.store = store
        self.head = head
        self.fetch = fetch
        self.progress_interval = progress_interval
        self._running = 0

    def running(self):
        return self._running > 0

    async def obtain(self, kind, progress, cancel):
        """The published package of [kind]; [progress](received, total or None); [cancel] an asyncio.Event."""
        job = _Job(asyncio.get_running_loop(), progress)
        self._running += 1
        job.future.add_done_callback(lambda _: self._finished())
        threading.Thread(target=self._run, args=(kind, job), name=f'download-{kind}', daemon=True).start()
        cancelled = asyncio.ensure_future(cancel.wait())
        try:
            done, _ = await asyncio.wait({job.future, cancelled}, return_when=asyncio.FIRST_COMPLETED)
            if job.future in done:
                return job.future.result()
            raise HelperError('cancelled', 'The download was cancelled.')
        finally:
            cancelled.cancel()
            if not job.future.done():
                job.cancel.set()

    def _finished(self):
        self._running -= 1

    def _run(self, kind, job):
        partial = None
        try:
            self.store.ensure_root()
            partial = self.store.begin(kind)
            manifest_path = partial / packages.MANIFEST_NAME
            self._get(job, packages.remote_url(kind, packages.MANIFEST_NAME), manifest_path)
            manifest = manifest_path.read_bytes()
            plan = packages.layout(kind, manifest)
            urls = [(packages.remote_url(kind, name), path) for name, path in plan]
            sizes = [self.head(url) for url, _ in urls]
            job.total = None if None in sizes else len(manifest) + sum(sizes)
            for url, path in urls:
                self._get(job, url, resolve_device_path(partial, path))
            self._report(job, force=True)
            result = self.store.publish(partial, kind)
            partial = None
            job.loop.call_soon_threadsafe(_settle, job.future, result, None)
        except BaseException as error:
            if partial is not None:
                self.store.discard(partial)
            job.loop.call_soon_threadsafe(_settle, job.future, None, _failure(error))

    def _get(self, job, url, destination):
        def on_bytes(count):
            job.received += count
            self._report(job)
        self.fetch(url, destination, on_bytes, job.cancel.is_set)
        if job.cancel.is_set():
            raise HelperError('cancelled', 'The download was cancelled.')

    def _report(self, job, force=False):
        now = time.monotonic()
        if force or now - job.reported >= self.progress_interval:
            job.reported = now
            job.loop.call_soon_threadsafe(job.progress, job.received, job.total)


def _settle(future, result, error):
    if not future.done():
        if error is None:
            future.set_result(result)
        else:
            future.set_exception(error)

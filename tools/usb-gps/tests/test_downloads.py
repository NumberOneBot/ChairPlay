import asyncio
import errno
import pathlib
import tempfile
import threading
import unittest

from tests.packaging_fakes import FakeRemote
from usb_gps_helper import packages
from usb_gps_helper.download import Downloads
from usb_gps_helper.protocol import HelperError
from usb_gps_helper.store import Store


class DownloadTests(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = pathlib.Path(self.temp.name) / 'cache' / 'developer-tools'
        self.store = Store(self.root)
        self.remote = FakeRemote()
        self.downloads = Downloads(self.store, head=self.remote.head, fetch=self.remote.fetch, progress_interval=0)

    def tearDown(self):
        self.temp.cleanup()

    async def idle(self):
        for _ in range(200):
            if not self.downloads.running():
                return
            await asyncio.sleep(.01)
        self.fail('the download thread did not end')

    def leftovers(self):
        return [child.name for child in self.root.iterdir() if child.name.startswith('.')] if self.root.exists() else []

    async def obtain(self, progress=None, cancel=None):
        return await self.downloads.obtain(packages.CRYPTEX, progress or (lambda *_: None), cancel or asyncio.Event())

    async def failure(self, **kwargs):
        with self.assertRaises(HelperError) as caught:
            await self.obtain(**kwargs)
        await self.idle()
        return caught.exception.code

    async def test_a_download_is_published_with_its_progress(self):
        seen = []
        package = await self.obtain(progress=lambda received, total: seen.append((received, total)))
        self.assertEqual(self.store.find(packages.CRYPTEX).path, package.path)
        self.store.verify(package)
        total = sum(len(data) for data in self.remote.files.values())
        self.assertEqual((total, total), seen[-1])
        self.assertEqual(sorted(received for received, _ in seen), [received for received, _ in seen])
        self.assertEqual([], self.leftovers())

    async def test_two_requests_for_one_package_share_one_download(self):
        first, second = await asyncio.gather(self.obtain(), self.obtain())
        self.assertEqual(first.path, second.path)
        self.assertEqual(len(self.remote.files), len(self.remote.fetched))

    async def test_a_cancelled_download_publishes_nothing(self):
        cancel = asyncio.Event()
        started = threading.Event()
        self.remote.chunk_hook = lambda name: (started.set(), __import__('time').sleep(.005))
        task = asyncio.ensure_future(self.obtain(cancel=cancel))
        while not started.is_set():
            await asyncio.sleep(.005)
        cancel.set()
        with self.assertRaises(HelperError) as caught:
            await task
        self.assertEqual('cancelled', caught.exception.code)
        await self.idle()
        self.assertIsNone(self.store.find(packages.CRYPTEX))
        self.assertEqual([], self.leftovers())

    async def test_when_one_of_two_cancels_the_other_still_gets_the_package(self):
        cancel = asyncio.Event()
        self.remote.chunk_hook = lambda name: __import__('time').sleep(.002)
        leaving = asyncio.ensure_future(self.obtain(cancel=cancel))
        staying = asyncio.ensure_future(self.obtain())
        await asyncio.sleep(.02)
        cancel.set()
        with self.assertRaises(HelperError):
            await leaving
        package = await staying
        self.store.verify(package)

    async def test_a_failed_fetch_publishes_nothing(self):
        self.remote.fail['Image.dmg'] = RuntimeError('connection reset')
        self.assertEqual('download-failed', await self.failure())
        self.assertIsNone(self.store.find(packages.CRYPTEX))
        self.assertEqual([], self.leftovers())

    async def test_a_full_disk_is_disk_full(self):
        self.remote.fail['Image.dmg'] = OSError(errno.ENOSPC, 'No space left on device')
        self.assertEqual('disk-full', await self.failure())
        self.assertEqual([], self.leftovers())

    async def test_a_manifest_that_is_not_a_package_fails_verification(self):
        self.remote.files['BuildManifest.plist'] = b'<html>rate limited</html>'
        self.assertEqual('verification-failed', await self.failure())
        self.assertEqual(['BuildManifest.plist'], self.remote.fetched)

    async def test_an_unreachable_store_downloads_nothing(self):
        self.downloads = Downloads(Store(pathlib.Path(self.temp.name) / 'a' / 'b' / 'c' / 'd'),
                                   head=self.remote.head, fetch=self.remote.fetch)
        self.assertEqual('store-unavailable', await self.failure())
        self.assertEqual([], self.remote.fetched)

    async def test_an_unknown_size_reports_no_total(self):
        self.remote.head = lambda url: None
        self.downloads = Downloads(self.store, head=self.remote.head, fetch=self.remote.fetch, progress_interval=0)
        seen = []
        await self.obtain(progress=lambda received, total: seen.append(total))
        self.assertEqual({None}, set(seen))


if __name__ == '__main__':
    unittest.main()

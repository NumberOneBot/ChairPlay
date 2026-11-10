import json
import os
import pathlib
import tempfile
import time
import unittest

from tests.packaging_fakes import cryptex_manifest
from usb_gps_helper import packages
from usb_gps_helper.protocol import HelperError
from usb_gps_helper.store import MANIFEST, Store


class StoreTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.data = pathlib.Path(self.temp.name)
        self.root = self.data / 'cache' / 'developer-tools'
        self.store = Store(self.root)

    def tearDown(self):
        self.temp.cleanup()

    def write_partial(self, build=packages.EXPECTED_BUILD):
        self.store.ensure_root()
        partial = self.store.begin(packages.CRYPTEX)
        manifest = cryptex_manifest(build)
        (partial / 'BuildManifest.plist').write_bytes(manifest)
        for name, relative in packages.layout(packages.CRYPTEX, manifest):
            (partial / relative).write_bytes(name.encode() * 10)
        return partial

    def publish(self, build=packages.EXPECTED_BUILD):
        return self.store.publish(self.write_partial(build), packages.CRYPTEX)

    def test_looking_into_a_missing_store_creates_nothing(self):
        self.assertIsNone(self.store.find(packages.CRYPTEX))
        self.assertFalse((self.data / 'cache').exists())

    def test_a_published_package_is_found_and_verifies(self):
        package = self.publish()
        self.assertEqual((packages.CRYPTEX, packages.EXPECTED_BUILD), (package.kind, package.build))
        found = self.store.find(packages.CRYPTEX)
        self.assertEqual(package.path, found.path)
        self.store.verify(found)
        self.assertIsNone(self.store.find(packages.PERSONALIZED))

    def test_the_manifest_is_written_last_and_holds_no_device_data(self):
        package = self.publish()
        manifest = json.loads((package.path / MANIFEST).read_text())
        self.assertEqual({'format', 'kind', 'build', 'files', 'source', 'library', 'helper', 'created'}, manifest.keys())
        self.assertEqual(5, len(manifest['files']))
        newest = max(package.path.iterdir(), key=lambda item: item.stat().st_mtime_ns)
        self.assertEqual(MANIFEST, newest.name)

    def test_files_without_a_manifest_are_no_package(self):
        partial = self.write_partial()
        os.rename(partial, self.root / f'{packages.CRYPTEX}-{packages.EXPECTED_BUILD}')
        self.assertIsNone(self.store.find(packages.CRYPTEX))

    def test_a_changed_file_fails_verification(self):
        package = self.publish()
        image = package.path / 'Image.dmg'
        image.write_bytes(b'x' * image.stat().st_size)
        found = self.store.find(packages.CRYPTEX)
        with self.assertRaises(HelperError) as caught:
            self.store.verify(found)
        self.assertEqual('verification-failed', caught.exception.code)
        image.write_bytes(b'short')
        self.assertIsNone(self.store.find(packages.CRYPTEX))

    def test_a_manifest_of_another_format_or_kind_is_ignored(self):
        package = self.publish()
        manifest = json.loads((package.path / MANIFEST).read_text())
        for change in ({'format': 99}, {'kind': packages.PERSONALIZED}):
            (package.path / MANIFEST).write_text(json.dumps({**manifest, **change}))
            self.assertIsNone(self.store.find(packages.CRYPTEX), change)

    def test_a_package_published_meanwhile_is_kept_and_the_partial_dropped(self):
        first = self.publish()
        partial = self.write_partial()
        second = self.store.publish(partial, packages.CRYPTEX)
        self.assertEqual(first.path, second.path)
        self.assertFalse(partial.exists())

    def test_a_broken_package_is_removed_whole(self):
        package = self.publish()
        self.store.remove(package)
        self.assertFalse(package.path.exists())
        self.assertEqual([], [p for p in self.root.iterdir()])

    def test_the_expected_build_wins_over_others(self):
        self.publish('26Z9999z')
        expected = self.publish()
        self.assertEqual(expected.path, self.store.find(packages.CRYPTEX).path)

    def test_old_partials_are_swept_and_fresh_ones_kept(self):
        old = self.write_partial()
        fresh = self.write_partial()
        stale = time.time() - 3600
        os.utime(old, (stale, stale))
        self.store.sweep()
        self.assertFalse(old.exists())
        self.assertTrue(fresh.exists())

    def test_an_unreachable_store_is_store_unavailable(self):
        for root in (self.data / 'gone' / 'deeper' / 'cache' / 'developer-tools', self.data / 'file'):
            (self.data / 'file').write_text('not a folder')
            with self.assertRaises(HelperError) as caught:
                Store(root).ensure_root()
            self.assertEqual('store-unavailable', caught.exception.code, root)
        self.assertFalse((self.data / 'gone').exists())

    def test_the_store_is_created_inside_the_data_folder(self):
        self.store.ensure_root()
        self.assertTrue(self.root.is_dir())


if __name__ == '__main__':
    unittest.main()

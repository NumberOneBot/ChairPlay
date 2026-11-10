import pathlib
import plistlib
import tempfile
import unittest

from tests.packaging_fakes import CRYPTEX_PATHS, cryptex_manifest
from usb_gps_helper import packages
from usb_gps_helper.protocol import HelperError


class KindTests(unittest.TestCase):
    def test_ios_27_and_later_take_the_cryptex(self):
        self.assertEqual(packages.CRYPTEX, packages.kind_for('27.2'))
        self.assertEqual(packages.CRYPTEX, packages.kind_for('27.0'))
        self.assertEqual(packages.CRYPTEX, packages.kind_for('28.1'))

    def test_older_or_unknown_versions_are_unsupported(self):
        for version in ('26.4.1', '17.4', '16.7', '1.0', 'garbage', None):
            with self.assertRaises(HelperError) as caught:
                packages.kind_for(version)
            self.assertEqual('unsupported-ios', caught.exception.code, version)


class LayoutTests(unittest.TestCase):
    def test_a_cryptex_package_puts_each_file_where_its_manifest_says(self):
        paths = dict(CRYPTEX_PATHS, **{'Cryptex1,GenericDmg': 'sub/Image.dmg'})
        layout = packages.layout(packages.CRYPTEX, cryptex_manifest(paths=paths))
        self.assertIn(('Image.dmg', 'sub/Image.dmg'), layout)
        self.assertEqual(4, len(layout))

    def test_a_manifest_path_that_leaves_the_package_is_refused(self):
        for bad in ('../escape', 'C:\\Windows\\x', '/abs'):
            paths = dict(CRYPTEX_PATHS, **{'Cryptex1,GenericDmg': bad})
            with self.assertRaises(HelperError) as caught:
                packages.layout(packages.CRYPTEX, cryptex_manifest(paths=paths))
            self.assertEqual('verification-failed', caught.exception.code, bad)

    def test_a_manifest_without_a_cryptex_identity_or_not_a_plist_is_refused(self):
        no_identity = plistlib.dumps({'ProductBuildVersion': packages.EXPECTED_BUILD, 'BuildIdentities': []})
        for manifest in (no_identity, b'not a plist'):
            with self.assertRaises(HelperError) as caught:
                packages.layout(packages.CRYPTEX, manifest)
            self.assertEqual('verification-failed', caught.exception.code)

    def test_check_finds_a_missing_or_empty_file(self):
        with tempfile.TemporaryDirectory() as name:
            folder = pathlib.Path(name)
            manifest = cryptex_manifest()
            (folder / 'BuildManifest.plist').write_bytes(manifest)
            for _, relative in packages.layout(packages.CRYPTEX, manifest):
                (folder / relative).write_bytes(b'data')
            packages.check(packages.CRYPTEX, folder)
            (folder / 'Image.dmg').write_bytes(b'')
            with self.assertRaises(HelperError):
                packages.check(packages.CRYPTEX, folder)

    def test_urls_name_the_published_files(self):
        url = packages.remote_url(packages.CRYPTEX, 'Image.dmg')
        self.assertTrue(url.startswith('https://raw.githubusercontent.com/doronz88/DeveloperDiskImage/'))
        self.assertTrue(url.endswith('/Xcode_iOS_DDI_Cryptex/Image.dmg'))


if __name__ == '__main__':
    unittest.main()

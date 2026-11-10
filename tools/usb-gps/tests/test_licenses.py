import pathlib
import subprocess
import sys
import tempfile
import unittest
from importlib.metadata import version

ROOT = pathlib.Path(__file__).resolve().parents[1]


class LicenseTests(unittest.TestCase):
    def run_script(self, lock_text, *extra):
        self.temp = tempfile.TemporaryDirectory()
        folder = pathlib.Path(self.temp.name)
        lock = folder / 'lock.txt'
        lock.write_text(lock_text)
        out = folder / 'out'
        result = subprocess.run([sys.executable, str(ROOT / 'licenses.py'), '--lock', str(lock), '--out', str(out), *extra],
                                capture_output=True, text=True, timeout=60)
        return result, out

    def tearDown(self):
        self.temp.cleanup()

    def test_each_locked_package_gets_its_licence_and_texts(self):
        result, out = self.run_script(f'# comment\npackaging=={version("packaging")}\n')
        self.assertEqual(0, result.returncode, result.stderr)
        listing = (out / 'packages.txt').read_text(encoding='utf-8')
        self.assertIn(f'packaging {version("packaging")}', ' '.join(listing.split()))
        texts = [path for path in (out / f'packaging-{version("packaging")}').iterdir()]
        self.assertTrue(texts)
        self.assertTrue((out / f'Python-{sys.version.split()[0]}' / 'LICENSE.txt').is_file())

    def test_a_package_at_another_version_than_locked_fails(self):
        result, _ = self.run_script('packaging==0.0.1\n')
        self.assertNotEqual(0, result.returncode)
        self.assertIn('packaging', result.stderr)

    def test_extra_packages_are_listed_too(self):
        result, out = self.run_script('', '--extra', 'packaging')
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn('packaging', (out / 'packages.txt').read_text(encoding='utf-8'))


if __name__ == '__main__':
    unittest.main()

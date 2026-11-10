"""A fake package server: small made-up files laid out like the real package, for the tests and the dry run."""
import plistlib

from . import packages
from .protocol import HelperError

CRYPTEX_PATHS = {
    'Cryptex1,GenericDmg': 'Image.dmg',
    'Cryptex1,GenericTrustCache': 'Image.dmg.trustcache',
    'Cryptex1,CryptexInfoPlist': 'Image.dmg.cryptex_info',
    'Cryptex1,GenericVolume': 'Image.dmg.root_hash',
}


def cryptex_manifest(build=packages.EXPECTED_BUILD, paths=CRYPTEX_PATHS):
    identity = {'Info': {'Variant': 'iOS Developer Disk Image Cryptex'},
                'Manifest': {key: {'Info': {'Path': path}} for key, path in paths.items()}}
    other = {'Info': {'Variant': 'Something else'}, 'Manifest': {}}
    return plistlib.dumps({'ProductBuildVersion': build, 'BuildIdentities': [other, identity]})


class FakeRemote:
    """Serves a package's files by their published name, as raw.githubusercontent.com would."""

    def __init__(self, build=packages.EXPECTED_BUILD):
        manifest = cryptex_manifest(build)
        self.files = {packages.MANIFEST_NAME: manifest}
        for name, _ in packages.layout(packages.CRYPTEX, manifest):
            self.files[name] = (name * 50).encode()
        self.fetched = []
        self.fail = {}
        self.chunk_hook = None

    @staticmethod
    def name(url):
        return url.rsplit('/', 1)[-1]

    def head(self, url):
        return len(self.files[self.name(url)])

    def fetch(self, url, destination, on_bytes, cancelled):
        name = self.name(url)
        self.fetched.append(name)
        if name in self.fail:
            raise self.fail[name]
        data = self.files[name]
        destination.parent.mkdir(parents=True, exist_ok=True)
        with open(destination, 'wb') as out:
            for start in range(0, len(data), 64):
                if cancelled():
                    raise HelperError('cancelled', 'The download was cancelled.')
                if self.chunk_hook is not None:
                    self.chunk_hook(name)
                out.write(data[start:start + 64])
                on_bytes(len(data[start:start + 64]))
        return len(data)

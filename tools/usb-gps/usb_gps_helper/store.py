"""The developer-tools store: the folder the app names (<data>/cache/developer-tools), never a profile folder.

A package is <store>/<kind>-<build>/ with its files and manifest.json, which is written last, inside a partial folder
that is then renamed into place: a package is published whole or not at all, and a failed download's partial folder
is deleted. The manifest lists each file's size and holds nothing about any phone: the package is shared by every
phone, while personalization happens on the phone at each mount.
"""
import json
import os
import shutil
import tempfile
import time
import uuid
from dataclasses import dataclass
from pathlib import Path

from . import VERSION, library_version, packages
from .protocol import HelperError

MANIFEST = 'manifest.json'
FORMAT = 1
PARTIAL = '.partial-'
REMOVING = '.removing-'


@dataclass(frozen=True)
class Package:
    kind: str
    build: str
    path: Path
    files: dict

    @property
    def id(self):
        return self.path.name


def _unavailable(detail):
    return HelperError('store-unavailable', detail)


class Store:
    def __init__(self, root):
        self.root = Path(root)

    def ensure_root(self):
        """Creates the store inside an existing data folder (never a whole new tree) and checks it is writable."""
        root = self.root
        if not root.is_dir():
            if root.exists():
                raise _unavailable('The developer tools path is not a folder.')
            if not root.parent.parent.is_dir():
                raise _unavailable('The app data folder is missing.')
            try:
                root.mkdir(parents=True, exist_ok=True)
            except OSError as error:
                raise _unavailable('The developer tools folder cannot be created.') from error
        try:
            handle, name = tempfile.mkstemp(dir=root, prefix='.write-check-')
            os.close(handle)
            os.remove(name)
        except OSError as error:
            raise _unavailable('The developer tools folder is not writable.') from error

    def begin(self, kind):
        partial = self.root / f'{PARTIAL}{kind}-{uuid.uuid4().hex[:8]}'
        partial.mkdir()
        return partial

    def find(self, kind):
        """The published package of [kind] whose files are all there; creates nothing."""
        if not self.root.is_dir():
            return None
        found = [package for child in self.root.iterdir() if child.name.startswith(f'{kind}-')
                 for package in [self._load(child, kind)] if package is not None]
        if not found:
            return None
        return max(found, key=lambda package: (package.build == packages.EXPECTED_BUILD, package.path.stat().st_mtime))

    def publish(self, partial, kind):
        manifest_bytes = (partial / packages.MANIFEST_NAME).read_bytes()
        build = packages.build_of(manifest_bytes)
        packages.check(kind, partial)
        files = {path: {'size': (partial / path).stat().st_size} for path in packages.files(kind, manifest_bytes)}
        target = self.root / f'{kind}-{build}'
        existing = self._load(target, kind)
        if existing is not None:
            self.discard(partial)
            return existing
        if target.exists():
            self.remove(target)
        record = {'format': FORMAT, 'kind': kind, 'build': build, 'files': files, 'source': packages.SOURCE,
                  'library': library_version(), 'helper': VERSION,
                  'created': time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime())}
        with open(partial / MANIFEST, 'w', encoding='utf-8') as out:
            json.dump(record, out, indent=1)
            out.flush()
            os.fsync(out.fileno())
        try:
            os.rename(partial, target)
        except OSError:
            existing = self._load(target, kind)
            self.discard(partial)
            if existing is None:
                raise
            return existing
        return Package(kind, build, target, files)

    def discard(self, partial):
        shutil.rmtree(partial, ignore_errors=True)

    def remove(self, path):
        """Moves a package folder out of sight first, so a half-deleted one is never taken for a package."""
        aside = self.root / f'{REMOVING}{uuid.uuid4().hex[:8]}'
        try:
            os.rename(path, aside)
        except OSError:
            aside = path
        shutil.rmtree(aside, ignore_errors=True)

    def _load(self, folder, kind):
        try:
            record = json.loads((folder / MANIFEST).read_text(encoding='utf-8'))
            if record.get('format') != FORMAT or record.get('kind') != kind:
                return None
            files = record['files']
            for path, entry in files.items():
                if (folder / path).stat().st_size != entry['size']:
                    return None
            return Package(kind, str(record['build']), folder, files)
        except (OSError, ValueError, KeyError, TypeError, AttributeError):
            return None

"""The developer-tools package the helper downloads, and where its files go.

USB GPS supports iOS 27 and later (D12), which installs the Cryptex1 DeveloperDiskImage over cryptexd; an older iOS
is unsupported-ios. The package is the files pymobiledevice3 would fetch into its home folder, from the same
repository; the helper fetches them itself, into the store the app names.
"""
import plistlib
from pathlib import Path

from developer_disk_image.repo import (
    CRYPTEX_IMAGE_PAYLOADS, CRYPTEX_IMAGE_ROOT, DEFAULT_REF, DEVELOPER_DISK_IMAGE_REPO_RAW_URL_FORMAT,
)
from packaging.version import InvalidVersion, Version
from pymobiledevice3.safe_paths import resolve_device_path
from pymobiledevice3.services.cryptexd import CRYPTEX1_PAYLOAD_KEYS, CRYPTEX_VARIANT_SUFFIX
from pymobiledevice3.services.mobile_image_mounter import LATEST_DDI_BUILD_ID

from .protocol import HelperError

CRYPTEX = 'cryptex'
EXPECTED_BUILD = LATEST_DDI_BUILD_ID
MANIFEST_NAME = 'BuildManifest.plist'
SOURCE = f'doronz88/DeveloperDiskImage@{DEFAULT_REF}'

MIN_IOS = Version('27.0')
_ROOTS = {CRYPTEX: CRYPTEX_IMAGE_ROOT}


def kind_for(ios_version):
    try:
        version = Version(ios_version)
    except (InvalidVersion, TypeError):
        raise HelperError('unsupported-ios', 'The iOS version is unknown.') from None
    if version < MIN_IOS:
        raise HelperError('unsupported-ios', 'USB GPS needs iOS 27 or later.')
    return CRYPTEX


def remote_url(kind, name):
    return DEVELOPER_DISK_IMAGE_REPO_RAW_URL_FORMAT.format(ref=DEFAULT_REF, path=f'{_ROOTS[kind]}/{name}')


def _parse(manifest_bytes):
    try:
        manifest = plistlib.loads(manifest_bytes)
    except Exception as error:
        raise HelperError('verification-failed', 'The package manifest is not a property list.') from error
    if not isinstance(manifest, dict) or not isinstance(manifest.get('BuildIdentities'), list):
        raise HelperError('verification-failed', 'The package manifest has no build identities.')
    return manifest


def build_of(manifest_bytes):
    build = _parse(manifest_bytes).get('ProductBuildVersion')
    if not isinstance(build, str) or not build.isalnum() or len(build) > 20:
        raise HelperError('verification-failed', 'The package manifest names no build.')
    return build


def layout(kind, manifest_bytes):
    """(published file name, path inside the package) for every file but the manifest itself."""
    manifest = _parse(manifest_bytes)
    identity = next((item for item in manifest['BuildIdentities'] if isinstance(item, dict)
                     and str(item.get('Info', {}).get('Variant', '')).endswith(CRYPTEX_VARIANT_SUFFIX)), None)
    if identity is None:
        raise HelperError('verification-failed', 'The package manifest has no cryptex identity.')
    result = []
    for key, field in CRYPTEX1_PAYLOAD_KEYS.items():
        try:
            path = identity['Manifest'][key]['Info']['Path']
            resolve_device_path(Path("."), path)
        except Exception as error:
            raise HelperError('verification-failed', f'The package manifest has no safe path for {key}.') from error
        result.append((CRYPTEX_IMAGE_PAYLOADS[field], path))
    return result


def files(kind, manifest_bytes):
    """Every path inside a package of [kind], the manifest first."""
    return [MANIFEST_NAME] + [path for _, path in layout(kind, manifest_bytes)]


def check(kind, folder):
    """The package in [folder] has its manifest and a non-empty file wherever the manifest says."""
    try:
        manifest_bytes = (folder / MANIFEST_NAME).read_bytes()
    except OSError as error:
        raise HelperError('verification-failed', 'The package has no manifest.') from error
    for path in files(kind, manifest_bytes):
        target = folder / path
        if not target.is_file() or target.stat().st_size == 0:
            raise HelperError('verification-failed', f'The package misses {path}.')

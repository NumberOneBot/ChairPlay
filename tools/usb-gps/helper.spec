# PyInstaller spec of the frozen USB GPS helper (D7): a --onedir folder, helper\helper.exe, that the app image
# carries and the app starts with --role ... (no Python, pip or network needed to run it).
#   python -m PyInstaller --noconfirm --clean --distpath <out> --workpath <work> tools\usb-gps\helper.spec
# Check the result with  <out>\helper\helper.exe --self-test  (one JSON line, ok true; no device is opened).
# A console program: the app reads its stdout and stderr. The JVM starts children with CREATE_NO_WINDOW, so no
# console window should open from the GUI app; A8 checks that in the packaged app.
from pathlib import Path

from PyInstaller.utils.hooks import collect_data_files, collect_dynamic_libs, collect_submodules, copy_metadata

here = Path(SPECPATH)

# What the real backend reaches at run time. usb_gps_helper.library imports every library module it uses by name, also
# those pymobiledevice3 itself would import only inside a function (the userspace tunnel, cryptexd), so the analysis
# follows them; a blanket collection of the library's subpackages would add its media tools (PyAV, 65 MB).
hidden = collect_submodules('usb_gps_helper') + ['psutil', 'winreg']

# Packages that read their own metadata (importlib.metadata.version) when imported, or that the helper asks for.
metadata = [entry for name in ('pymobiledevice3', 'pyimg4', 'developer_disk_image', 'prompt_toolkit',
                               'opentelemetry-api')
            for entry in copy_metadata(name)]

# What the helper never runs. Each is imported only inside a function, a try with a fallback, or a path the helper
# does not take, so the self-test cannot see a mistake here; a change to this list is tested on the phone.
#   IPython (with jedi, parso, sqlite3): only the library's REPL and rich's Jupyter output import it.
#   PIL: pygments' image formatter, inside a try.   chardet: requests falls back to charset_normalizer.
#   tzdata: arrow's parser of time zone names.   sslpsk_pmd3: the Wi-Fi pairing tunnel; ours goes through
#   CoreDeviceTunnelProxy. It also dragged in OpenSSL 1.1 DLLs from whatever folder PATH offered (Git's).
excluded = ['tkinter', 'pytest', 'IPython', 'PIL', 'tzdata', 'chardet', 'sslpsk_pmd3']

analysis = Analysis(
    [str(here / 'helper_main.py')],
    pathex=[str(here)],
    hiddenimports=hidden,
    # pytun_pmd3 loads its wintun.dll when imported, though the helper uses the userspace tunnel only.
    binaries=collect_dynamic_libs('pytun_pmd3'),
    datas=collect_data_files('pymobiledevice3') + collect_data_files('developer_disk_image') + metadata,
    excludes=excluded,
    noarchive=False,
)
archive = PYZ(analysis.pure)
executable = EXE(
    archive,
    analysis.scripts,
    [],
    exclude_binaries=True,
    name='helper',
    console=True,
    upx=False,
)
COLLECT(executable, analysis.binaries, analysis.datas, name='helper', upx=False)

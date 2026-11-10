"""Lists the licences of the Python packages the frozen helper is built from, with their licence texts.

Run with the build venv's Python: python licenses.py --lock requirements-lock.txt --extra pyinstaller --out <dir>.
Writes <dir>/packages.txt (package, version, licence) and copies each package's LICENSE, LICENCE, COPYING and NOTICE
files into <dir>/<package>-<version>/, and Python's own licence into <dir>/Python-<version>/. The helper freezes a
subset of these packages; the list errs on the side of more. Fails when a package's installed version is not the
locked one, or a package names no licence at all.
"""
import argparse
import pathlib
import shutil
import sys
from importlib.metadata import PackageNotFoundError, distribution

_TEXTS = ('LICENSE', 'LICENCE', 'COPYING', 'NOTICE')


def locked(path):
    for line in pathlib.Path(path).read_text(encoding='utf-8').splitlines():
        line = line.split('#', 1)[0].strip()
        if line:
            name, _, wanted = line.partition('==')
            yield name.strip(), wanted.strip()


def licence(meta):
    expression = meta.get('License-Expression')
    if expression:
        return expression
    text = (meta.get('License') or '').strip()
    if text and '\n' not in text and len(text) <= 60:
        return text
    classifiers = [item.split('::')[-1].strip() for item in meta.get_all('Classifier') or [] if item.startswith('License ::')]
    if classifiers:
        return ' / '.join(classifiers)
    return 'see licence files' if text else ''


def main(argv=None):
    parser = argparse.ArgumentParser()
    parser.add_argument('--lock', required=True)
    parser.add_argument('--extra', action='append', default=[], help='A package beyond the lock (PyInstaller).')
    parser.add_argument('--out', required=True, type=pathlib.Path)
    args = parser.parse_args(argv)
    shutil.rmtree(args.out, ignore_errors=True)
    args.out.mkdir(parents=True)
    rows, problems = [], []
    for name, wanted in list(locked(args.lock)) + [(name, None) for name in args.extra]:
        try:
            package = distribution(name)
        except PackageNotFoundError:
            problems.append(f'{name} is not installed')
            continue
        if wanted is not None and package.version != wanted:
            problems.append(f'{name} is {package.version}, the lock says {wanted}')
            continue
        folder = args.out / f'{package.metadata["Name"]}-{package.version}'
        copied = 0
        for file in package.files or []:
            if file.name.upper().startswith(_TEXTS):
                folder.mkdir(exist_ok=True)
                shutil.copyfile(file.locate(), folder / '-'.join(file.parts[-2:]))
                copied += 1
        name_of_licence = licence(package.metadata)
        if not name_of_licence and not copied:
            problems.append(f'{name} names no licence')
        rows.append((package.metadata['Name'], package.version, name_of_licence or 'see licence files'))
    python = sys.version.split()[0]
    python_folder = args.out / f'Python-{python}'
    python_folder.mkdir()
    shutil.copyfile(pathlib.Path(sys.base_prefix) / 'LICENSE.txt', python_folder / 'LICENSE.txt')
    rows.insert(0, ('Python', python, 'PSF-2.0'))
    width = max(len(row[0]) for row in rows) + 2
    lines = ['Python packages in the USB GPS helper (helper\\ in the app folder) and their licences.',
             'Each package\'s licence texts are in the folder of its name and version next to this file.', '']
    lines += [f'{row[0]:<{width}}{row[1]:<16}{row[2]}' for row in sorted(rows, key=lambda row: row[0].lower())]
    (args.out / 'packages.txt').write_text('\n'.join(lines) + '\n', encoding='utf-8')
    for problem in problems:
        print(f'licenses: {problem}', file=sys.stderr)
    return 1 if problems else 0


if __name__ == '__main__':
    raise SystemExit(main())

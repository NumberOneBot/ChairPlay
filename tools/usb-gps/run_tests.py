"""Runs the helper's tests in a child interpreter with a time limit, so a hang ends the run instead of the shell.

Usage: python run_tests.py [--timeout SECONDS] [unittest names...]. Exit code 0 when every test passed.
"""
import argparse
import os
import pathlib
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parent


def main(argv=None):
    parser = argparse.ArgumentParser()
    parser.add_argument('--timeout', type=float, default=300)
    parser.add_argument('names', nargs='*')
    args = parser.parse_args(argv)
    command = [sys.executable, '-X', 'faulthandler', '-m', 'unittest']
    command += args.names if args.names else ['discover', '-s', 'tests', '-t', '.']
    environment = dict(os.environ, PYTHONPATH=str(ROOT), PYTHONDONTWRITEBYTECODE='1')
    with subprocess.Popen(command, cwd=ROOT, env=environment) as child:
        try:
            return child.wait(timeout=args.timeout)
        except subprocess.TimeoutExpired:
            child.kill()
            child.wait()
            print(f'run_tests: the tests did not finish in {args.timeout:g} s and were stopped', file=sys.stderr)
            return 124


if __name__ == '__main__':
    raise SystemExit(main())

#!/usr/bin/env python3
"""Bundles the Kotlin standard library into app/classes5.dex.

The base APK is a web/React build and does not necessarily ship kotlin-stdlib.
The native UI compiled into classes4.dex references Intrinsics, StringsKt,
FilesKt, Pair and Unit, so the library must travel with the overlay.

Usage: python3 tools/bundle_kotlin_stdlib.py [--stdlib PATH] [--out PATH]
"""
import argparse
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))
import rebuild

DEFAULT_OUT = ROOT / 'app' / 'classes5.dex'
CANDIDATES = [
    '/data/data/com.termux/files/usr/share/kotlin/lib/kotlin-stdlib.jar',
    '/data/data/com.termux/files/usr/opt/kotlin/lib/kotlin-stdlib.jar',
    '/usr/share/kotlin/lib/kotlin-stdlib.jar',
    '/usr/local/share/kotlin/lib/kotlin-stdlib.jar',
    '/snap/kotlin/current/lib/kotlin-stdlib.jar',
]


def find_stdlib():
    found = shutil.which('kotlin-stdlib.jar')
    if found:
        return Path(found)
    for candidate in CANDIDATES:
        path = Path(candidate)
        if path.is_file():
            return path
    # kotlinc usually sits next to a lib/ directory.
    kotlinc = shutil.which('kotlinc')
    if kotlinc:
        for parent in Path(kotlinc).resolve().parents:
            candidate = parent / 'lib' / 'kotlin-stdlib.jar'
            if candidate.is_file():
                return candidate
    return None


def bundle(stdlib=None, out=DEFAULT_OUT):
    d8 = shutil.which('d8')
    if not d8:
        raise SystemExit('d8 is not on PATH')
    stdlib_path = Path(stdlib) if stdlib else find_stdlib()
    if not stdlib_path or not stdlib_path.is_file():
        raise SystemExit('kotlin-stdlib.jar not found; pass --stdlib')
    stubs = ROOT / 'tools' / 'android-stubs.jar'
    if not stubs.is_file():
        subprocess.run([sys.executable, str(ROOT / 'tools' / 'make_android_stubs.py')], check=True)

    with tempfile.TemporaryDirectory() as folder:
        output = Path(folder) / 'dex'
        output.mkdir()
        subprocess.run([
            d8, '--output', str(output), '--min-api', '26',
            '--lib', str(stubs), str(stdlib_path),
        ], check=True)
        produced = output / 'classes.dex'
        if not produced.is_file():
            raise SystemExit('d8 did not produce a dex file')
        payload = produced.read_bytes()
    rebuild.validate_dex(payload, 'classes5.dex')
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_bytes(payload)
    print(f'Wrote {out} ({len(payload)} bytes) from {stdlib_path}')
    return out


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--stdlib')
    parser.add_argument('--out', default=str(DEFAULT_OUT))
    args = parser.parse_args(argv)
    bundle(args.stdlib, Path(args.out))
    return 0


if __name__ == '__main__':
    sys.exit(main())

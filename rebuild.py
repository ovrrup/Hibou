#!/usr/bin/env python3
"""Rebuild Hibou from pinned release APKs plus this repository's repair payload.

Usage: python3 rebuild.py [both|standard|bypass] --fetch-base
Or supply --standard-apk /path/base.apk and --bypass-apk /path/base.apk.
Input APKs are SHA-256 pinned. No missing brand.py or private signing.p12 import.
The app/ payload contains only overrides, not a complete source tree.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import struct
import subprocess
import sys
import tempfile
import urllib.request
import zipfile
import zlib

ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT/'tools'))
from patch_web_bundle import patch_bundle
import patch_manifest

NATIVE_UI = os.environ.get('HIBOU_NATIVE_UI', '1') != '0'
REQUIRE_KOTLIN = NATIVE_UI or os.environ.get('HIBOU_REQUIRE_KOTLIN', '0') == '1'
NATIVE_LAUNCHER = 'Lcom/agy/ru/HibouChatActivity;'

VERSION = '1.0.0-beta.1'
VERSION_CODE = 1
BASE = {
    'standard': ('Leostryx-3.0.5-standard.apk', (
        '3311cb0bae7e3d7c0f9370d04d08c7db44c9dec5e6ebfb38555a4877ddc5dc4b',
        'c7a4ee1d85edbdb2fd05b6bb5cf54b4e3867527ea6c1c192d0219efab9d1f5d1',
    )),
    'bypass': ('Leostryx-3.0.5-bypass.apk', (
        'f3cc34860963cc6902ed8393301eef2b8f13fabd39c872e8aa554efa5a7539ae',
        'd612eaeb2b592c37d2f5636be363669ff351ed431769ce55990bebe5723f7546',
    )),
}
OVERRIDES = [
    'AndroidManifest.xml', 'classes.dex', 'classes4.dex', 'resources.arsc',
    'assets/web/index.html',
    'assets/web/agy_tools.js', 'assets/web/agy_tools.sh',
    'assets/web/agy_providers.js',
    'assets/web/agy_firstrun.html',
    'assets/web/hibou.css', 'assets/web/hibou_mobile.css', 'assets/web/hibou.js',
    'assets/web/hibou_aura.js', 'assets/web/hibou_ui.js', 'assets/web/hibou_login_bg.jpg',
    'assets/web/hibouActionRequired.mp3', 'assets/web/hibouCascadeDone.mp3',
]

def sha256(path):
    digest = hashlib.sha256()
    with Path(path).open('rb') as source:
        for chunk in iter(lambda: source.read(1024*1024), b''):
            digest.update(chunk)
    return digest.hexdigest()

def validate_dex(data, name):
    if len(data) < 112 or not data.startswith(b'dex\n'):
        raise ValueError('Invalid DEX header: '+name)
    if struct.unpack_from('<I', data, 32)[0] != len(data):
        raise ValueError('Invalid DEX length: '+name)
    if hashlib.sha1(data[32:]).digest() != data[12:32]:
        raise ValueError('Invalid DEX SHA-1: '+name)
    if zlib.adler32(data[12:]) & 0xffffffff != struct.unpack_from('<I', data, 8)[0]:
        raise ValueError('Invalid DEX Adler-32: '+name)
    string_ids_size = struct.unpack_from('<I', data, 56)[0]
    string_ids_off = struct.unpack_from('<I', data, 60)[0]
    prev = None
    for i in range(string_ids_size):
        soff = struct.unpack_from('<I', data, string_ids_off + i * 4)[0]
        pos = soff
        while data[pos] & 0x80:
            pos += 1
        pos += 1
        s = data[pos:data.find(b'\x00', pos)]
        curr = tuple(s.replace(b'\xc0\x80', b'\x00').decode('utf-8', errors='surrogatepass'))
        if prev is not None and prev > curr:
            raise ValueError(f'Out-of-order DEX string_ids in {name} at index {i}: {prev!r} > {curr!r}')
        prev = curr

def dex_defines_class(data, descriptor):
    """Returns True when the DEX class_defs table defines `descriptor`."""
    if len(data) < 112 or not data.startswith(b'dex\n'):
        return False
    string_ids_size, string_ids_off = struct.unpack_from('<II', data, 56)
    type_ids_size, type_ids_off = struct.unpack_from('<II', data, 64)
    class_defs_size, class_defs_off = struct.unpack_from('<II', data, 96)
    if class_defs_off + class_defs_size * 32 > len(data):
        return False

    def read_string(index):
        offset = struct.unpack_from('<I', data, string_ids_off + index * 4)[0]
        position = offset
        while data[position] & 0x80:
            position += 1
        position += 1
        end = data.find(b'\x00', position)
        return data[position:end].decode('utf-8', errors='replace')

    for index in range(class_defs_size):
        class_index = struct.unpack_from('<I', data, class_defs_off + index * 32)[0]
        if class_index >= type_ids_size:
            continue
        string_index = struct.unpack_from('<I', data, type_ids_off + class_index * 4)[0]
        if read_string(string_index) == descriptor:
            return True
    return False


def validate_arsc(data, name='resources.arsc'):
    if len(data) < 12 or struct.unpack_from('<H', data, 0)[0] != 0x0002:
        raise ValueError('Invalid ARSC header: '+name)
    sp_size = struct.unpack_from('<I', data, 16)[0]
    pkg_off = 12 + sp_size
    if pkg_off + 288 > len(data):
        raise ValueError('Truncated ARSC package chunk: '+name)
    ctype = struct.unpack_from('<H', data, pkg_off)[0]
    if ctype != 0x0200:
        raise ValueError(f'Invalid ARSC package chunk type in {name}: {ctype:#06x}')
    pkg_id = struct.unpack_from('<I', data, pkg_off + 8)[0]
    if pkg_id != 0x7f:
        raise ValueError(f'Invalid ARSC package ID in {name}: {pkg_id:#010x} (must be 0x0000007f)')
    pkg_name = data[pkg_off+12:pkg_off+12+256].decode('utf-16le', errors='ignore').split('\x00')[0]
    if pkg_name != 'agy.hibou':
        raise ValueError(f'Unexpected ARSC package name in {name}: {pkg_name!r}')

def validate_apk(path):
    with zipfile.ZipFile(path) as apk:
        if apk.testzip() is not None:
            raise ValueError('ZIP integrity check failed')
        names = apk.namelist()
        if len(names) != len(set(names)):
            raise ValueError('Duplicate ZIP entries')
        for name in names:
            if name.endswith('.dex'):
                validate_dex(apk.read(name), name)
            elif name == 'resources.arsc':
                validate_arsc(apk.read(name), name)
        manifest = json.loads(apk.read('assets/runtime/manifest.json'))
        for name, expected in manifest['files'].items():
            if hashlib.sha256(apk.read('assets/runtime/'+name)).hexdigest() != expected:
                raise ValueError('Runtime checksum mismatch: '+name)
    aapt = shutil.which('aapt')
    if aapt:
        res = subprocess.run([aapt, 'dump', 'badging', str(path)], stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, text=True)
        if res.returncode != 0:
            raise ValueError(f'aapt dump badging failed on {path} (exit code {res.returncode}): {res.stderr}')
    if NATIVE_UI:
        with zipfile.ZipFile(path) as apk:
            if not patch_manifest.has_native_launcher(apk.read('AndroidManifest.xml')):
                raise ValueError('Native UI enabled but the packaged manifest does not launch ' + patch_manifest.DEFAULT_ACTIVITY)
            dexes = [name for name in apk.namelist() if name.endswith('.dex')]
            if not any(dex_defines_class(apk.read(name), NATIVE_LAUNCHER) for name in dexes):
                raise ValueError('Native UI enabled but no packaged DEX defines ' + NATIVE_LAUNCHER)



def base_apk(variant, supplied, fetch):
    filename, expected = BASE[variant]
    path = Path(supplied) if supplied else ROOT/'.cache/rebuild'/filename
    if not path.exists() and fetch:
        path.parent.mkdir(parents=True, exist_ok=True)
        partial = path.with_suffix('.part')
        try:
            url = 'https://github.com/Leostrange/Leostryx/releases/download/v3.0.5/'+filename
            with urllib.request.urlopen(url, timeout=120) as response, partial.open('wb') as output:
                shutil.copyfileobj(response, output, 1024*1024)
            valid = sha256(partial) in expected if isinstance(expected, (tuple, set, list)) else sha256(partial) == expected
            if not valid:
                raise ValueError('Downloaded base APK checksum mismatch')
            partial.replace(path)
        finally:
            partial.unlink(missing_ok=True)
    if not path.is_file():
        raise FileNotFoundError('Provide --'+variant+'-apk or use --fetch-base: '+str(path))
    valid = sha256(path) in expected if isinstance(expected, (tuple, set, list)) else sha256(path) == expected
    if not valid:
        raise ValueError('Unexpected base APK: '+str(path))
    return path


def pack(source, variant, destination):
    compile_kt = ROOT / 'tools' / 'compile_kotlin.py'
    have_toolchain = bool(shutil.which('kotlinc') and shutil.which('d8'))
    if compile_kt.is_file() and have_toolchain:
        subprocess.run([sys.executable, str(compile_kt)], check=True)
        stdlib = ROOT / 'tools' / 'bundle_kotlin_stdlib.py'
        if stdlib.is_file():
            subprocess.run([sys.executable, str(stdlib)], check=True)
    elif REQUIRE_KOTLIN:
        raise RuntimeError(
            'Native UI requires kotlinc and d8. Install the Kotlin toolchain or '
            'set HIBOU_NATIVE_UI=0 to package the web launcher.')
    else:
        print('Notice: Kotlin toolchain unavailable; using the checked-in DEX.')

    dex4 = ROOT / 'app' / 'classes4.dex'
    if NATIVE_UI:
        if not dex4.is_file() or not dex_defines_class(dex4.read_bytes(), NATIVE_LAUNCHER):
            raise RuntimeError(
                'Native UI is enabled but classes4.dex does not define ' + NATIVE_LAUNCHER +
                '. Compile the Kotlin sources first.')

    overrides = {name: ROOT/'app'/name for name in OVERRIDES}
    stdlib_dex = ROOT / 'app' / 'classes5.dex'
    if NATIVE_UI and not stdlib_dex.is_file():
        raise RuntimeError(
            'Native UI requires app/classes5.dex (Kotlin stdlib). Run '
            'tools/bundle_kotlin_stdlib.py with kotlinc/d8 installed.')
    if stdlib_dex.is_file():
        overrides['classes5.dex'] = stdlib_dex
    for name, path in overrides.items():
        if not path.is_file():
            raise FileNotFoundError(str(path))
    manifest_payload = overrides['AndroidManifest.xml'].read_bytes()
    if NATIVE_UI:
        manifest_payload, manifest_changed = patch_manifest.patch_manifest(manifest_payload)
        print(('Patched launcher -> ' if manifest_changed else 'Launcher already native: ')
              + patch_manifest.DEFAULT_ACTIVITY)
    node = shutil.which('node')
    if node:
        for name in OVERRIDES:
            if name.endswith('.js'):
                subprocess.run([node, '--check', str(overrides[name])], check=True)
    else:
        print('Notice: Node.js is not present in local PATH; skipping JS syntax pre-check')
    subprocess.run(['bash', '-n', str(overrides['assets/web/agy_tools.sh'])], check=True)
    info = {'versionName': VERSION, 'versionCode': VERSION_CODE, 'variant': variant,
            'packageName': 'agy.hibou', 'sourceRelease': 'v3.0.5', 'sourceSha256': sha256(source)}
    metadata_path = 'assets/web/build-info.json'
    with zipfile.ZipFile(source) as base:
        bundle = patch_bundle(base.read('assets/web/generated/main.js').decode('utf-8')).encode('utf-8')
    with tempfile.TemporaryDirectory() as folder:
        check = Path(folder)/'main.js'
        check.write_bytes(bundle)
        if node:
            subprocess.run([node, '--check', str(check)], check=True)
    with zipfile.ZipFile(source) as old, zipfile.ZipFile(destination, 'w') as new:
        seen = set()
        for entry in old.infolist():
            name = entry.filename
            if name.startswith('META-INF/') or name in ('stamp-cert-sha256', metadata_path):
                continue
            payload = bundle if name == 'assets/web/generated/main.js' else (overrides[name].read_bytes() if name in overrides else old.read(name))
            if name == 'AndroidManifest.xml':
                payload = manifest_payload
            if name.endswith('.dex'):
                validate_dex(payload, name)
            new.writestr(entry, payload)
            seen.add(name)
        for name, path in overrides.items():
            if name not in seen:
                data = manifest_payload if name == 'AndroidManifest.xml' else path.read_bytes()
                new.writestr(name, data, compress_type=zipfile.ZIP_STORED)
        new.writestr(metadata_path, json.dumps(info, ensure_ascii=False, indent=2)+'\n', compress_type=zipfile.ZIP_DEFLATED)
    validate_apk(destination)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('variant', choices=['both','standard','bypass'], nargs='?', default='both')
    parser.add_argument('--standard-apk')
    parser.add_argument('--bypass-apk')
    parser.add_argument('--fetch-base', action='store_true')
    args = parser.parse_args()
    variants = ['standard','bypass'] if args.variant == 'both' else [args.variant]
    jar = ROOT/'tools/uber-apk-signer.jar'
    keystore = Path(os.environ.get('HIBOU_KEYSTORE', os.environ.get('LEOSTRYX_KEYSTORE', str(ROOT/'dist/hibou-debug.keystore'))))
    if not jar.is_file() or not keystore.is_file():
        parser.error('Missing signer JAR or keystore; see docs/BUILD.md')
    signed = ROOT/'dist/signed'
    signed.mkdir(parents=True, exist_ok=True)
    records = []
    for variant in variants:
        source = base_apk(variant, getattr(args, variant+'_apk'), args.fetch_base)
        basename = 'Hibou-'+VERSION+'-'+variant
        unsigned = ROOT/'dist'/(basename+'-unsigned.apk')
        pack(source, variant, unsigned)
        for suffix in ['-aligned-signed.apk','-unsigned-aligned-signed.apk','-unsigned-signed.apk']:
            (signed/(basename+suffix)).unlink(missing_ok=True)
        subprocess.run(['java','-jar',str(jar),'--apks',str(unsigned),'--ks',str(keystore),
            '--ksAlias',os.environ.get('HIBOU_KEY_ALIAS', os.environ.get('LEOSTRYX_KEY_ALIAS','debug')),
            '--ksPass',os.environ.get('HIBOU_STORE_PASSWORD', os.environ.get('LEOSTRYX_STORE_PASSWORD','android')),
            '--ksKeyPass',os.environ.get('HIBOU_KEY_PASSWORD', os.environ.get('LEOSTRYX_KEY_PASSWORD','android')),
            '--out',str(signed)], check=True)
        candidates = [signed/(basename+suffix) for suffix in ['-aligned-signed.apk','-unsigned-aligned-signed.apk','-unsigned-signed.apk']]
        generated = next((p for p in candidates if p.is_file()), None)
        if generated is None:
            raise RuntimeError('Signer did not produce the expected APK')
        target = signed/(basename+'.apk')
        generated.replace(target)
        validate_apk(target)
        records.append((target.name, sha256(target)))
        unsigned.unlink()
    sums = ''.join(digest+'  '+name+'\n' for name,digest in records)
    (signed/'SHA256SUMS.txt').write_text(sums)
    print(sums, end='')
    return 0

if __name__ == '__main__':
    sys.exit(main())

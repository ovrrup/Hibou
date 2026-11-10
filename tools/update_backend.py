"""Leostryx release checks/downloads. Embedded into agy_tools.sh at build time."""
import hashlib
import json
import os
from pathlib import Path
import re
import ssl
import struct
import sys
import urllib.parse
import urllib.request
import zipfile
import zlib

API = 'https://api.github.com/repos/Leostrange/Leostryx/releases/latest'
DOWNLOAD_PREFIX = 'https://github.com/Leostrange/Leostryx/releases/download/'

def version_tuple(value):
    match = re.fullmatch(r'v?(\d+)\.(\d+)\.(\d+)', str(value))
    if not match:
        raise ValueError('Invalid release version')
    return tuple(int(p) for p in match.groups())

def tls_context():
    prefix = Path(os.environ.get('PREFIX', '/nonexistent'))
    home = Path(os.environ.get('HOME', '/nonexistent'))
    candidates = [prefix/'etc/tls/cert.pem', prefix/'etc/ssl/certs/ca-certificates.crt', home/'runtime/certs/ca-certificates.crt']
    for path in candidates:
        if path.is_file():
            return ssl.create_default_context(cafile=str(path))
    return ssl.create_default_context()

def request(url):
    return urllib.request.Request(url, headers={'Accept': 'application/vnd.github+json', 'User-Agent': 'Leostryx-Updater'})

def release_info(current, variant, open_url=urllib.request.urlopen):
    if variant not in ('standard', 'bypass'):
        raise ValueError('Unknown build variant')
    with open_url(request(API), timeout=25, context=tls_context()) as response:
        release = json.load(response)
    tag = release.get('tag_name', '')
    if version_tuple(tag) <= version_tuple(current):
        return {'available': False, 'tag': tag, 'variant': variant}
    asset = next((a for a in release.get('assets', []) if a.get('name', '').endswith('-'+variant+'.apk')), None)
    if not asset:
        raise ValueError('Release has no matching APK')
    url = asset.get('browser_download_url', '')
    if not url.startswith(DOWNLOAD_PREFIX):
        raise ValueError('Unexpected download URL')
    digest = asset.get('digest') or ''
    expected = digest[7:] if digest.startswith('sha256:') else ''
    if not re.fullmatch('[a-fA-F0-9]{64}', expected):
        sums = next((a for a in release.get('assets', []) if a.get('name') == 'SHA256SUMS.txt'), None)
        if not sums or not sums.get('browser_download_url', '').startswith(DOWNLOAD_PREFIX):
            raise ValueError('Release has no SHA-256 checksums')
        with open_url(request(sums['browser_download_url']), timeout=25, context=tls_context()) as response:
            text = response.read().decode('utf-8')
        for line in text.splitlines():
            parts = line.split()
            if len(parts) == 2 and parts[1].lstrip('*') == asset['name']:
                expected = parts[0]
                break
    if not re.fullmatch('[a-fA-F0-9]{64}', expected):
        raise ValueError('APK checksum missing')
    size = int(asset.get('size', 0))
    if size <= 0:
        raise ValueError('Invalid APK size')
    return {'available': True, 'tag': tag, 'variant': variant, 'url': url,
            'sha256': expected.lower(), 'size': size, 'name': asset['name']}

def validate_apk(path):
    with zipfile.ZipFile(path) as archive:
        if archive.testzip() is not None:
            raise ValueError('Corrupt ZIP entry')
        if not {'AndroidManifest.xml', 'classes.dex'}.issubset(archive.namelist()):
            raise ValueError('Not an APK')
        names = archive.namelist()
        if len(names) != len(set(names)):
            raise ValueError('Duplicate APK entries')
        for name in names:
            if not name.endswith('.dex'):
                continue
            data = archive.read(name)
            if len(data) < 112 or not data.startswith(b'dex\n'):
                raise ValueError('Invalid DEX: '+name)
            if struct.unpack_from('<I', data, 32)[0] != len(data):
                raise ValueError('Invalid DEX length: '+name)
            if data[12:32] != hashlib.sha1(data[32:]).digest():
                raise ValueError('Invalid DEX SHA-1: '+name)
            if struct.unpack_from('<I', data, 8)[0] != zlib.adler32(data[12:]) & 0xffffffff:
                raise ValueError('Invalid DEX Adler-32: '+name)

def download(info, open_url=urllib.request.urlopen):
    url = info.get('url', '')
    expected = info.get('sha256', '')
    size = int(info.get('size', 0))
    if not url.startswith(DOWNLOAD_PREFIX) or not re.fullmatch('[a-f0-9]{64}', expected) or size <= 0:
        raise ValueError('Invalid download metadata')
    folder = Path(os.environ['HOME'])/'updates'
    folder.mkdir(parents=True, exist_ok=True)
    partial = folder/'leostryx-update.apk.part'
    destination = folder/'leostryx-update.apk'
    checksum = folder/'leostryx-update.sha256'
    checksum.unlink(missing_ok=True)
    try:
        digest = hashlib.sha256()
        done = 0
        with open_url(request(url), timeout=120, context=tls_context()) as response, partial.open('wb') as output:
            while True:
                chunk = response.read(262144)
                if not chunk:
                    break
                done += len(chunk)
                if done > size:
                    raise ValueError('APK exceeds expected size')
                digest.update(chunk)
                output.write(chunk)
                print('@@UPDPROGRESS', done, size, flush=True)
            output.flush()
            os.fsync(output.fileno())
        if done != size or digest.hexdigest() != expected:
            raise ValueError('Download size or SHA-256 mismatch')
        validate_apk(partial)
        os.replace(partial, destination)
        checksum.write_text(expected+'\n')
        return {'ready': True}
    finally:
        partial.unlink(missing_ok=True)

def main():
    try:
        args = json.loads(os.environ.get('AGY_UPDATE_ARG', '{}'))
        action = os.environ.get('AGY_UPDATE_ACTION', '')
        if action == 'check':
            result = release_info(args['versionName'], args['variant'])
            print('@@UPDATE_JSON '+json.dumps(result, ensure_ascii=False), flush=True)
        elif action == 'download':
            # Resolve the authenticated HTTPS release metadata again; do not trust
            # a stale or modified URL/checksum passed through the WebView.
            info = release_info(args['versionName'], args['variant'])
            if not info.get('available') or info.get('tag') != args.get('tag'):
                raise ValueError('Release changed; check for updates again')
            print('@@UPDATE_DOWNLOAD '+json.dumps(download(info)), flush=True)
        else:
            raise ValueError('Unknown updater operation')
    except Exception as exc:
        print('@@UPDATE_ERROR '+str(exc), flush=True)
        return 1
    return 0

if __name__ == '__main__':
    sys.exit(main())

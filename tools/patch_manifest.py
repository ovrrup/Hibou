#!/usr/bin/env python3
"""Patches the compiled AndroidManifest.xml without an apktool round-trip.

The launcher activity name is rewritten in the binary XML string pool.  Because
the replacement is shorter than the original, the string can be rewritten in
place: no chunk offsets change and every attribute/resource reference stays
valid.  The operation is idempotent.

Usage:
  python3 tools/patch_manifest.py --check
  python3 tools/patch_manifest.py [--input PATH] [--output PATH]
                                  [--activity com.agy.ru.HibouChatActivity]
"""
import argparse
import struct
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DEFAULT_INPUT = ROOT / 'app' / 'AndroidManifest.xml'
DEFAULT_ACTIVITY = 'com.agy.ru.HibouChatActivity'
ORIGINAL_LAUNCHER = 'com.agy.ui.launcher.LauncherDarkActivity'

RES_STRING_POOL_TYPE = 0x0001
UTF8_FLAG = 0x00000100
SORTED_FLAG = 0x00000001


class ManifestError(ValueError):
    pass


def _read_uleb(data, offset):
    """Not used for AXML; kept for clarity of the encoding rules."""
    result = 0
    shift = 0
    while True:
        byte = data[offset]
        offset += 1
        result |= (byte & 0x7f) << shift
        if not byte & 0x80:
            return result, offset
        shift += 7


def _string_pool(data):
    if len(data) < 16 or struct.unpack_from('<H', data, 0)[0] != 0x0003:
        raise ManifestError('Not a binary AndroidManifest.xml')
    offset = 8
    while offset + 8 <= len(data):
        chunk_type, header_size, chunk_size = struct.unpack_from('<HHI', data, offset)
        if chunk_type == RES_STRING_POOL_TYPE:
            string_count, style_count, flags, strings_start, styles_start = struct.unpack_from(
                '<IIIII', data, offset + 8)
            return offset, header_size, chunk_size, string_count, flags, strings_start
        if chunk_size <= 0:
            break
        offset += chunk_size
    raise ManifestError('String pool chunk not found')


def _read_string(data, base, offset, utf8):
    if utf8:
        length = data[offset]
        offset += 1
        if length & 0x80:
            length = ((length & 0x7f) << 8) | data[offset]
            offset += 1
        raw = data[offset:offset + length]
        return raw.decode('utf-8', 'replace'), offset + length + 1
    length = struct.unpack_from('<H', data, offset)[0]
    offset += 2
    if length & 0x8000:
        length = ((length & 0x7fff) << 16) | struct.unpack_from('<H', data, offset)[0]
        offset += 2
    raw = data[offset:offset + length * 2]
    return raw.decode('utf-16le', 'replace'), offset + length * 2 + 2


def iter_strings(data):
    pool_offset, header_size, _, count, flags, strings_start = _string_pool(data)
    base = pool_offset + strings_start
    utf8 = bool(flags & UTF8_FLAG)
    index_offset = pool_offset + header_size
    for index in range(count):
        offset = struct.unpack_from('<I', data, index_offset + index * 4)[0]
        value, end = _read_string(data, base, base + offset, utf8)
        yield index, base + offset, value, end


def find_string(data, value):
    for index, offset, current, end in iter_strings(data):
        if current == value:
            return index, offset, end
    return None


def patch_manifest(data, activity=DEFAULT_ACTIVITY):
    """Returns (patched_bytes, changed)."""
    if find_string(data, activity) is not None:
        return data, False

    found = find_string(data, ORIGINAL_LAUNCHER)
    if found is None:
        raise ManifestError(
            f'Launcher activity {ORIGINAL_LAUNCHER!r} not found; '
            'the base manifest layout changed')
    _, offset, end = found

    pool_offset, _, _, _, flags, _ = _string_pool(data)
    utf8 = bool(flags & UTF8_FLAG)
    if utf8:
        raise ManifestError('UTF-8 AXML string pools are not supported by the in-place patcher')

    old_length = struct.unpack_from('<H', data, offset)[0]
    if old_length & 0x8000:
        raise ManifestError('Extended UTF-16 length prefix is not supported')
    slot = end - offset  # length prefix + characters + terminator

    encoded = activity.encode('utf-16le')
    needed = 2 + len(encoded) + 2
    if needed > slot:
        raise ManifestError(
            f'Replacement activity name is too long: need {needed} bytes, slot is {slot}')

    patched = bytearray(data)
    struct.pack_into('<H', patched, offset, len(activity))
    patched[offset + 2:offset + 2 + len(encoded)] = encoded
    patched[offset + 2 + len(encoded):offset + slot] = b'\x00' * (slot - 2 - len(encoded))

    # The replacement may no longer be in sorted order.  Clear the flag so
    # consumers do not assume binary search.
    if flags & SORTED_FLAG:
        struct.pack_into('<I', patched, pool_offset + 16, flags & ~SORTED_FLAG)

    return bytes(patched), True


def has_native_launcher(data, activity=DEFAULT_ACTIVITY):
    return find_string(data, activity) is not None


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--input', default=str(DEFAULT_INPUT))
    parser.add_argument('--output')
    parser.add_argument('--activity', default=DEFAULT_ACTIVITY)
    parser.add_argument('--check', action='store_true',
                        help='Only verify that the manifest is patched')
    args = parser.parse_args(argv)

    source = Path(args.input)
    data = source.read_bytes()
    if args.check:
        ok = has_native_launcher(data, args.activity)
        print(('patched: ' if ok else 'not patched: ') + str(source))
        return 0 if ok else 1

    patched, changed = patch_manifest(data, args.activity)
    target = Path(args.output) if args.output else source
    target.write_bytes(patched)
    print(('Patched ' if changed else 'Already patched ') + str(target))
    return 0


if __name__ == '__main__':
    sys.exit(main())

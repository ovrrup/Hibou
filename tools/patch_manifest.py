#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 ovrrup <ofeducationc@gmail.com>
# SPDX-License-Identifier: Apache-2.0
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


START_ELEMENT = 0x0102
END_ELEMENT = 0x0103
LAUNCHER_CATEGORY = 'android.intent.category.LAUNCHER'
DEAD_LAUNCHER = 'com.agy.ui.launcher.LauncherLightActivity'


def _repoint_category(data, category_index, dead_activity_index, replacement_index):
    """Rewrites one category element's value at the attribute level.

    The port ships a single classes.dex, but the original manifest still
    declares a second launcher activity (`LauncherLightActivity`, disabled)
    whose class does not exist.  Rewriting the whole string pool would also
    strip the real launcher's category, so instead the dead activity's
    category attribute is repointed at a harmless pool string in place: only
    two 4-byte fields change and every chunk offset stays valid.
    """
    offset = 8
    while offset + 8 <= len(data):
        chunk_type, header_size, chunk_size = struct.unpack_from('<HHI', data, offset)
        if chunk_type == RES_STRING_POOL_TYPE:
            offset += chunk_size
            continue
        if chunk_type == START_ELEMENT:
            name_index = struct.unpack_from('<I', data, offset + 20)[0]
            if name_index != _element_name_category(data):
                offset += chunk_size
                continue
            attribute_start, attribute_size, attribute_count = struct.unpack_from(
                '<HHH', data, offset + 24)
            attrs = offset + 16 + attribute_start
            for i in range(attribute_count):
                attr = attrs + i * attribute_size
                data_type = data[attr + 15]
                if data_type != 0x03:  # STRING
                    continue
                raw_index = struct.unpack_from('<I', data, attr + 8)[0]
                data_index = struct.unpack_from('<I', data, attr + 16)[0]
                if category_index not in (raw_index, data_index):
                    continue
                # Verify the ancestor activity before touching anything.
                if _category_owner(data, offset, dead_activity_index):
                    struct.pack_into('<I', data, attr + 8, replacement_index)
                    struct.pack_into('<I', data, attr + 16, replacement_index)
                    return True
        if chunk_size <= 0:
            break
        offset += chunk_size
    return False


def _element_name_category(data):
    found = find_string(data, 'category')
    return found[0] if found else -1


def _category_owner(data, node_offset, dead_activity_index):
    """True when the category node at `node_offset` sits under the dead activity."""
    # Walk backwards over the chunk stream, tracking element nesting depth.
    # A forward pass recording parentage for the single dead activity node
    # is simpler: rescan the XML from the start.
    offset = 8
    stack = []
    while offset + 8 <= len(data):
        chunk_type, header_size, chunk_size = struct.unpack_from('<HHI', data, offset)
        if chunk_type == START_ELEMENT:
            name_index = struct.unpack_from('<I', data, offset + 20)[0]
            attrs = _activity_name(data, offset)
            if name_index == _element_name_activity(data) and attrs == dead_activity_index:
                # The dead activity: is node_offset inside its subtree?
                return _contains(data, offset, node_offset)
        if chunk_type == END_ELEMENT:
            pass
        if chunk_size <= 0:
            break
        offset += chunk_size
    return False


def _element_name_activity(data):
    found = find_string(data, 'activity')
    return found[0] if found else -1


def _activity_name(data, node_offset):
    """String index of android:name on the START_ELEMENT at node_offset, or -1."""
    attribute_start, attribute_size, attribute_count = struct.unpack_from(
        '<HHH', data, node_offset + 24)
    attrs = node_offset + 16 + attribute_start
    name_value = find_string(data, 'name')
    if name_value is None:
        return -1
    for i in range(attribute_count):
        attr = attrs + i * attribute_size
        attr_name = struct.unpack_from('<I', data, attr + 4)[0]
        if attr_name != name_value[0] or data[attr + 15] != 0x03:
            continue
        raw_index = struct.unpack_from('<I', data, attr + 8)[0]
        data_index = struct.unpack_from('<I', data, attr + 16)[0]
        if name_value[0] != -1 and data_index != 0xFFFFFFFF:
            return data_index
        return raw_index
    return -1


def _contains(data, activity_offset, node_offset):
    """True when node_offset lies within the element opened at activity_offset."""
    offset = activity_offset
    depth = 0
    while offset + 8 <= len(data):
        chunk_type, header_size, chunk_size = struct.unpack_from('<HHI', data, offset)
        if chunk_type == START_ELEMENT:
            depth += 1
        elif chunk_type == END_ELEMENT:
            depth -= 1
            if depth == 0:
                return False
        if chunk_type == START_ELEMENT and offset == node_offset and depth > 1:
            return True
        if chunk_size <= 0:
            break
        offset += chunk_size
    return False


def strip_dead_launchers(data):
    """Returns (patched_bytes, changed): neutralises the dead second launcher."""
    launcher = find_string(data, LAUNCHER_CATEGORY)
    dead = find_string(data, DEAD_LAUNCHER)
    if launcher is None or dead is None:
        return data, False  # nothing declared to strip
    replacement = find_string(data, 'android.intent.category.BROWSABLE')
    if replacement is None:
        replacement = find_string(data, 'android.intent.category.DEFAULT')
    if replacement is None:
        replacement = (dead[0],)  # any string that is not a launcher category
    patched = bytearray(data)
    changed = _repoint_category(
        patched, launcher[0], dead[0], replacement[0])
    return bytes(patched), changed


def dead_launcher_present(data):
    launcher = find_string(data, LAUNCHER_CATEGORY)
    dead = find_string(data, DEAD_LAUNCHER)
    if launcher is None or dead is None:
        return False
    patched = bytearray(data)
    return _repoint_category(patched, launcher[0], dead[0], launcher[0])


def has_native_launcher(data, activity=DEFAULT_ACTIVITY):
    ok = find_string(data, activity) is not None
    return ok and not dead_launcher_present(data)


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
    stripped, stripped_changed = strip_dead_launchers(patched)
    target = Path(args.output) if args.output else source
    target.write_bytes(stripped)
    print(('Patched ' if changed else 'Already patched ') + str(target))
    print(('Stripped dead launchers ' if stripped_changed
           else 'Dead launchers already stripped ') + str(target))
    return 0


if __name__ == '__main__':
    sys.exit(main())

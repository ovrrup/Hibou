#!/usr/bin/env python3
"""Shared ResStringPool reader/rebuilder.

The compiled string pool (used by both binary AndroidManifest.xml and
resources.arsc) stores strings in slots referenced by index; a rebuild that
preserves count and order keeps every reference valid.  The chunk may grow —
the caller is responsible for splicing and for fixing enclosing size fields.
"""
import struct

RES_STRING_POOL_TYPE = 0x0001
SORTED_FLAG = 0x00000001
UTF8_FLAG = 0x00000100

POOL_HEADER_SIZE = 28


def read_pool(data, pool_off):
    """Return (count, flags, [str...]).  Raises on unsupported layouts."""
    chunk_type, header_size, chunk_size, count, style_count, flags, strings_start, styles_start = \
        struct.unpack_from('<HHIIIIII', data, pool_off)
    if chunk_type != RES_STRING_POOL_TYPE:
        raise ValueError(f'not a string pool chunk at {pool_off}')
    if style_count != 0 or styles_start != 0:
        raise ValueError('string pools with style spans are not supported')
    utf8 = bool(flags & UTF8_FLAG)
    index_off = pool_off + header_size
    base = pool_off + strings_start
    strings = []
    for i in range(count):
        off = struct.unpack_from('<I', data, index_off + 4 * i)[0]
        strings.append(_decode(data, base + off, utf8))
    return count, flags, strings


def _decode(data, pos, utf8):
    if utf8:
        n = data[pos]
        pos += 1
        if n & 0x80:
            n = ((n & 0x7f) << 8) | data[pos]
            pos += 1
        n2 = data[pos]
        pos += 1
        if n2 & 0x80:
            n2 = ((n2 & 0x7f) << 8) | data[pos]
            pos += 1
        return data[pos:pos + n2].decode('utf-8', 'replace')
    n = struct.unpack_from('<H', data, pos)[0]
    pos += 2
    if n & 0x8000:
        n = ((n & 0x7fff) << 16) | struct.unpack_from('<H', data, pos)[0]
        pos += 4
    return data[pos:pos + 2 * n].decode('utf-16-le', 'replace')


def _encode_utf8(value):
    raw = value.encode('utf-8')
    out = bytearray()
    out += _prefix(len(value))  # character count
    out += _prefix(len(raw))    # byte count
    out += raw
    out += b'\x00'
    return bytes(out)


def _encode_utf16(value):
    raw = value.encode('utf-16-le')
    out = bytearray()
    if len(value) < 0x8000:
        out += struct.pack('<H', len(value))
    else:
        out += struct.pack('<HH', (len(value) >> 16) | 0x8000, len(value) & 0xffff)
    out += raw
    out += b'\x00\x00'
    return bytes(out)


def _prefix(length):
    if length < 128:
        return bytes([length])
    return bytes([0x80 | (length >> 8), length & 0xff])


def rebuild_pool(data, pool_off, mapping=None, prefix_rules=None):
    """Rebuild the pool chunk at pool_off with remapped strings.

    mapping: {old_exact: new} replacements.
    prefix_rules: [(old_prefix, new_prefix)] — applied when the string starts
    with old_prefix (and no exact mapping matched).

    Returns (new_data, pool_chunk_size_before) — new_data has the pool chunk
    replaced in place and everything after it shifted; the caller must fix
    enclosing size fields (they are NOT adjusted here).
    """
    count, flags, strings = read_pool(data, pool_off)
    utf8 = bool(flags & UTF8_FLAG)
    old_chunk_size = struct.unpack_from('<I', data, pool_off + 4)[0]

    def remap(value):
        if mapping and value in mapping:
            return mapping[value]
        if prefix_rules:
            for old_prefix, new_prefix in prefix_rules:
                if value.startswith(old_prefix):
                    return new_prefix + value[len(old_prefix):]
        return value

    new_strings = [remap(s) for s in strings]

    flags = flags & ~SORTED_FLAG  # replacements may break sort order
    encode = _encode_utf8 if utf8 else _encode_utf16

    header = bytearray(struct.pack('<HHIIIIII',
                                   RES_STRING_POOL_TYPE, POOL_HEADER_SIZE, 0,
                                   count, 0, flags, 0, 0))
    strings_start = POOL_HEADER_SIZE + count * 4
    offsets = []
    blob = bytearray()
    for value in new_strings:
        offsets.append(len(blob))  # offsets are relative to stringsStart
        blob += encode(value)
    while len(blob) % 4:
        blob += b'\x00'
    chunk_size = strings_start + len(blob)
    struct.pack_into('<I', header, 4, chunk_size)      # chunk size
    struct.pack_into('<I', header, 20, strings_start)  # stringsStart
    packed = header + struct.pack('<%dI' % count, *offsets) + bytes(blob)

    out = bytearray(data[:pool_off] + packed + data[pool_off + old_chunk_size:])
    return out, old_chunk_size

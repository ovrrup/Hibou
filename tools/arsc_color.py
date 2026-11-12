#!/usr/bin/env python3
"""Get or set a color resource value inside compiled resources.arsc.

Colors are stored as Res_value entries with dataType 0x1C (ARGB8) and a
4-byte payload, so a value change is an in-place byte patch: no chunk sizes
or offsets move.  Every config (default + night) of the matched color is
reported; --set patches them all.

File layout notes (this arsc):
  - table header (0x0002) is NOT a sub-chunk; sub-chunks start after it
  - package chunk (0x0200): header is 288 bytes (id + 256-byte name + pool
    offsets), typeStrings at +268, keyStrings at +276
  - inner chunks: type string pool, key string pool, then 0x0202 spec and
    0x0201 config chunks; offsets array sits at chunk start + headerSize,
    entries at chunk start + entriesStart

Usage:
  python3 tools/arsc_color.py --input app/resources.arsc --res 0x7f070001
  python3 tools/arsc_color.py --input app/resources.arsc \\
      --name ic_launcher_background --set '#FF14171C' --output out.arsc
"""
import argparse
import struct
import sys

RES_TABLE_TYPE = 0x0002
RES_STRING_POOL_TYPE = 0x0001
RES_TABLE_PACKAGE_TYPE = 0x0200
RES_TABLE_TYPE_TYPE = 0x0201
COLOR_TYPE_ID = 7  # "color" in this package (type pool order is fixed here)
ENTRY_FLAG_COMPLEX = 0x0001
UTF8_FLAG = 0x00000100


def parse_color(argb: int) -> str:
    return "#{:08X}".format(argb & 0xFFFFFFFF)


def parse_color_input(text: str) -> int:
    t = text.strip().lstrip("#")
    if len(t) == 6:
        t = "FF" + t
    if len(t) != 8:
        raise ValueError(f"expected #AARRGGBB or #RRGGBB, got {text!r}")
    return int(t, 16)


def _read_pool_strings(data: bytes, pool_off: int):
    """Decode a string pool chunk into a list of python strings."""
    _t, header_size, _sz, count, _styles, flags, strings_start = struct.unpack_from(
        "<HHIIIII", data, pool_off)
    utf8 = bool(flags & UTF8_FLAG)
    index_off = pool_off + header_size
    base = pool_off + strings_start
    out = []
    for i in range(count):
        off = struct.unpack_from("<I", data, index_off + 4 * i)[0]
        p = base + off
        if utf8:
            n = data[p]
            p2 = p + 1
            if n & 0x80:
                n = ((n & 0x7F) << 8) | data[p2]
                p2 += 1
            n2 = data[p2]
            p2 += 1
            if n2 & 0x80:
                n2 = ((n2 & 0x7F) << 8) | data[p2]
                p2 += 1
            out.append(data[p2:p2 + n2].decode("utf-8", "replace"))
        else:
            n = struct.unpack_from("<H", data, p)[0]
            if n & 0x8000:
                n = ((n & 0x7FFF) << 16) | struct.unpack_from("<H", data, p + 2)[0]
                p += 4
            else:
                p += 2
            out.append(data[p:p + 2 * n].decode("utf-16-le", "replace"))
    return out


def iter_color_entries(data):
    """Yield (res_id, key_name, value_offset, value_data_type) for every
    color-typed entry in every config."""
    pos = 0
    end = len(data)
    if end >= 8 and struct.unpack_from("<H", data, 0)[0] == RES_TABLE_TYPE:
        pos = struct.unpack_from("<H", data, 2)[0]
    while pos + 8 <= end:
        chunk_type, _header_size, chunk_size = struct.unpack_from("<HHI", data, pos)
        if chunk_size <= 0 or pos + chunk_size > end:
            break
        if chunk_type == RES_TABLE_PACKAGE_TYPE:
            pkg_start = pos
            pkg_end = pos + chunk_size
            pkg_id = data[pkg_start + 8]
            pkg_header_size = struct.unpack_from("<H", data, pkg_start + 2)[0]
            key_strings = None
            if pkg_start + 280 <= pkg_end:
                key_pool_off = struct.unpack_from("<I", data, pkg_start + 276)[0]
                try:
                    key_strings = _read_pool_strings(data, pkg_start + key_pool_off)
                except Exception:
                    key_strings = None
            p = pkg_start + pkg_header_size
            while p + 8 <= pkg_end:
                ctype, _hsize, csize = struct.unpack_from("<HHI", data, p)
                if csize <= 0 or p + csize > pkg_end:
                    break
                if ctype == RES_TABLE_TYPE_TYPE and data[p + 8] == COLOR_TYPE_ID:
                    entry_count = struct.unpack_from("<I", data, p + 12)[0]
                    entries_start = struct.unpack_from("<I", data, p + 16)[0]
                    chunk_header_size = struct.unpack_from("<H", data, p + 2)[0]
                    offsets_pos = p + chunk_header_size
                    for i in range(entry_count):
                        entry_off = struct.unpack_from("<I", data, offsets_pos + 4 * i)[0]
                        if entry_off == 0xFFFFFFFF:
                            continue
                        entry_pos = p + entries_start + entry_off
                        if entry_pos + 8 > pkg_end:
                            continue
                        entry_size, entry_flags, entry_key = struct.unpack_from(
                            "<HHI", data, entry_pos)
                        if entry_flags & ENTRY_FLAG_COMPLEX:
                            continue
                        value_pos = entry_pos + entry_size
                        if value_pos + 8 > pkg_end:
                            continue
                        data_type = data[value_pos + 3]
                        if data_type not in (0x1C, 0x1D, 0x1E, 0x1F):
                            continue
                        res_id = (pkg_id << 24) | (COLOR_TYPE_ID << 16) | i
                        name = ""
                        if key_strings is not None and entry_key < len(key_strings):
                            name = key_strings[entry_key]
                        yield res_id, name, value_pos + 4, data_type
                p += csize
        pos += chunk_size


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--input", required=True)
    group = ap.add_mutually_exclusive_group(required=True)
    group.add_argument("--res", help="resource id, e.g. 0x7f070001")
    group.add_argument("--name", help="color resource key name, e.g. ic_launcher_background")
    ap.add_argument("--set", dest="set_value", help="new color (#AARRGGBB or #RRGGBB)")
    ap.add_argument("--output", help="write patched arsc here (default: in place)")
    args = ap.parse_args()

    data = bytearray(open(args.input, "rb").read())
    hits = []
    for res_id, name, offset, dtype in iter_color_entries(data):
        if args.res is not None:
            if int(args.res, 16) != res_id:
                continue
        else:
            if name != args.name:
                continue
        hits.append((res_id, name, offset, dtype))
    if not hits:
        target = args.res or args.name
        print(f"no color value found for {target}", file=sys.stderr)
        return 1
    for res_id, name, offset, dtype in hits:
        old = struct.unpack_from("<I", data, offset)[0]
        print(f"@0x{res_id:08X} {name} dataType=0x{dtype:02X} "
              f"value={parse_color(old)} at offset {offset}")
    if args.set_value:
        new = parse_color_input(args.set_value)
        for _res_id, _name, offset, _dtype in hits:
            struct.pack_into("<I", data, offset, new)
        out = args.output or args.input
        with open(out, "wb") as f:
            f.write(bytes(data))
        print(f"wrote {out}: {len(hits)} value(s) -> {parse_color(new)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())

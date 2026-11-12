#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 ovrrup <ofeducationc@gmail.com>
# SPDX-License-Identifier: Apache-2.0
"""Rebrand user-visible strings in compiled resources.arsc (global pool).

Rebuilds the global string pool with exact replacements; every other string
is preserved and all references (which are index-based) stay valid.  The
pool may grow: the following package chunk shifts, and the table header's
total-size field is fixed up here.

Usage:
  python3 tools/arsc_strings.py --input app/resources.arsc \
      --output out/resources.arsc --map Hibou=Kestrel
"""
import argparse
import struct
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from pool_rebuild import read_pool, rebuild_pool

RES_STRING_POOL_TYPE = 0x0001
RES_TABLE_TYPE = 0x0002


def find_global_pool(data):
    pos = struct.unpack_from('<H', data, 2)[0]  # after table header
    chunk_type, _header_size, chunk_size = struct.unpack_from('<HHI', data, pos)
    if chunk_type != RES_STRING_POOL_TYPE:
        raise ValueError(f'expected global string pool at {pos}, got type 0x{chunk_type:04X}')
    return pos


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--input', required=True)
    parser.add_argument('--output')
    parser.add_argument('--map', action='append', default=[],
                        metavar='OLD=NEW', help='exact string replacement (repeatable)')
    args = parser.parse_args(argv)

    mapping = {}
    for pair in args.map:
        old, _, new = pair.partition('=')
        mapping[old] = new
    if not mapping:
        parser.error('nothing to do: pass at least one --map OLD=NEW')

    data = Path(args.input).read_bytes()
    pool_off = find_global_pool(data)
    _count, _flags, strings = read_pool(data, pool_off)
    missing = [k for k in mapping if k not in strings]
    if missing:
        print(f'strings not found in global pool: {missing}', file=sys.stderr)
        return 1

    new_data, _old_size = rebuild_pool(data, pool_off, mapping=mapping)
    struct.pack_into('<I', new_data, 4, len(new_data))  # table header: total size

    # verify: every original string either mapped or unchanged
    pool_off2 = find_global_pool(new_data)
    _c, _f, new_strings = read_pool(new_data, pool_off2)
    if new_strings != [mapping.get(s, s) for s in strings]:
        print('internal error: pool rebuild mismatch', file=sys.stderr)
        return 1

    target = Path(args.output) if args.output else Path(args.input)
    target.write_bytes(new_data)
    changed = [f'{k!r} -> {v!r}' for k, v in mapping.items()]
    print(f"wrote {target} ({len(data)} -> {len(new_data)} bytes); applied: {', '.join(changed)}")
    return 0


if __name__ == '__main__':
    sys.exit(main())

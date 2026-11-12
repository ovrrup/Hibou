#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 ovrrup <ofeducationc@gmail.com>
# SPDX-License-Identifier: Apache-2.0
"""Rebrand the compiled binary AndroidManifest.xml for the Kestrel identity.

Moves the app namespace `agy.hibou` to `com.kestrel.app`:
  - manifest package attribute            agy.hibou -> com.kestrel.app
  - FileProvider authority                agy.hibou.fileprovider
                                     -> com.kestrel.app.fileprovider
  - INTERNAL_BROADCAST permission         agy.hibou.permission.INTERNAL_BROADCAST
                                     -> com.kestrel.app.permission.INTERNAL_BROADCAST

The manifest string pool is rebuilt with a prefix rule; every other string
is preserved and all attribute references (which are index-based) stay
valid.  The pool may grow: the XML chunks after it shift and the file
header's total-size field is fixed up here.  Idempotent.

The launcher-activity selection is done separately by patch_manifest.py
(its in-place slot rewrite works on the rebuilt pool because it locates
strings by value).
"""
import argparse
import struct
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from pool_rebuild import read_pool, rebuild_pool

OLD_NAMESPACE = 'agy.hibou'
NEW_NAMESPACE = 'com.kestrel.app'


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--input', required=True)
    parser.add_argument('--output')
    parser.add_argument('--check', action='store_true',
                        help='only verify the rebrand state')
    args = parser.parse_args(argv)

    data = Path(args.input).read_bytes()
    if len(data) < 16 or struct.unpack_from('<H', data, 0)[0] != 0x0003:
        print('not a binary AndroidManifest.xml', file=sys.stderr)
        return 1

    pool_off = struct.unpack_from('<H', data, 2)[0]  # XML header size
    _count, _flags, strings = read_pool(data, pool_off)
    rebranded = [s for s in strings if s.startswith(NEW_NAMESPACE)]

    if args.check:
        ok = any(s == NEW_NAMESPACE for s in strings) and not any(
            s.startswith(OLD_NAMESPACE) for s in strings)
        print(('rebranded: ' if ok else 'not rebranded: ') + args.input)
        return 0 if ok else 1

    if rebranded and not any(s.startswith(OLD_NAMESPACE) for s in strings):
        print(f'already rebranded: {args.input}')
        return 0

    new_data, _old_size = rebuild_pool(data, pool_off,
                                       prefix_rules=[(OLD_NAMESPACE, NEW_NAMESPACE)])
    struct.pack_into('<I', new_data, 4, len(new_data))  # XML header: total size

    # verify
    _c, _f, out_strings = read_pool(new_data, struct.unpack_from('<H', new_data, 2)[0])
    if out_strings != [s if not s.startswith(OLD_NAMESPACE)
                       else NEW_NAMESPACE + s[len(OLD_NAMESPACE):] for s in strings]:
        print('internal error: pool rebuild mismatch', file=sys.stderr)
        return 1
    if any(s.startswith(OLD_NAMESPACE) for s in out_strings):
        print('internal error: old namespace survived', file=sys.stderr)
        return 1

    target = Path(args.output) if args.output else Path(args.input)
    target.write_bytes(new_data)
    moved = [s for s in strings if s.startswith(OLD_NAMESPACE)]
    print(f'wrote {target} ({len(data)} -> {len(new_data)} bytes); moved: {moved}')
    return 0


if __name__ == '__main__':
    sys.exit(main())

#!/usr/bin/env python3
"""Minimal DEX reader used by the build and by tests.

The repository ships precompiled DEX files and has no decompiler in the normal
build path.  This module exposes just enough of the format to answer the
questions the native UI migration needs:

* does a DEX define a class?
* which fields and methods does a class declare, and with what prototypes?
* which classes does a DEX reference but not define?

Usage:
  python3 tools/dex_inspect.py app/classes.dex --class com.agy.bridge.AndroidBridge
  python3 tools/dex_inspect.py app/classes.dex --defines com.agy.ru.HibouChatActivity
  python3 tools/dex_inspect.py app/classes.dex --methods com.agy.bridge.AndroidBridge
"""
import argparse
import struct
import sys
from pathlib import Path

NO_INDEX = 0xFFFFFFFF


class DexError(ValueError):
    pass


class Dex:
    def __init__(self, data):
        if len(data) < 112 or not data.startswith(b'dex\n'):
            raise DexError('Not a DEX file')
        self.data = data
        (self.string_ids_size, self.string_ids_off,
         self.type_ids_size, self.type_ids_off,
         self.proto_ids_size, self.proto_ids_off,
         self.field_ids_size, self.field_ids_off,
         self.method_ids_size, self.method_ids_off,
         self.class_defs_size, self.class_defs_off) = struct.unpack_from('<12I', data, 56)

    # -- primitives -----------------------------------------------------

    def _uleb(self, offset):
        result = 0
        shift = 0
        while True:
            byte = self.data[offset]
            offset += 1
            result |= (byte & 0x7f) << shift
            if not byte & 0x80:
                return result, offset
            shift += 7

    def string(self, index):
        offset = struct.unpack_from('<I', self.data, self.string_ids_off + index * 4)[0]
        _, offset = self._uleb(offset)  # utf-16 length, unused
        end = self.data.find(b'\x00', offset)
        return self.data[offset:end].decode('utf-8', errors='replace')

    def type_name(self, index):
        return self.string(struct.unpack_from('<I', self.data, self.type_ids_off + index * 4)[0])

    def proto(self, index):
        base = self.proto_ids_off + index * 12
        shorty_idx, return_idx, params_off = struct.unpack_from('<III', self.data, base)
        return {
            'shorty': self.string(shorty_idx),
            'returns': self.type_name(return_idx),
            'parameters': self.type_list(params_off),
        }

    def type_list(self, offset):
        if offset == 0:
            return []
        size = struct.unpack_from('<I', self.data, offset)[0]
        return [self.type_name(struct.unpack_from('<H', self.data, offset + 4 + index * 2)[0])
                for index in range(size)]

    def field_id(self, index):
        class_idx, type_idx, name_idx = struct.unpack_from('<HHI', self.data, self.field_ids_off + index * 8)
        return {'class': self.type_name(class_idx), 'type': self.type_name(type_idx),
                'name': self.string(name_idx)}

    def method_id(self, index):
        class_idx, proto_idx, name_idx = struct.unpack_from('<HHI', self.data, self.method_ids_off + index * 8)
        return {'class': self.type_name(class_idx), 'name': self.string(name_idx),
                'proto': self.proto(proto_idx)}

    # -- class table ----------------------------------------------------

    def classes(self):
        """Yields (descriptor, access_flags, superclass, class_data_off)."""
        for index in range(self.class_defs_size):
            base = self.class_defs_off + index * 32
            (class_idx, access, super_idx, interfaces_off, source_idx,
             annotations_off, class_data_off, static_values_off) = struct.unpack_from('<8I', self.data, base)
            yield {
                'descriptor': self.type_name(class_idx),
                'access': access,
                'super': self.type_name(super_idx) if super_idx != NO_INDEX else None,
                'interfaces': self.type_list(interfaces_off),
                'class_data_off': class_data_off,
            }

    def class_data(self, class_data_off):
        if not class_data_off:
            return {'fields': [], 'methods': []}
        offset = class_data_off
        static_fields, offset = self._uleb(offset)
        instance_fields, offset = self._uleb(offset)
        direct_methods, offset = self._uleb(offset)
        virtual_methods, offset = self._uleb(offset)

        fields = []
        # Each list (static, instance) restarts its index delta at zero.
        for count in (static_fields, instance_fields):
            index = 0
            for _ in range(count):
                diff, offset = self._uleb(offset)
                index += diff
                access, offset = self._uleb(offset)
                record = self.field_id(index)
                record['access'] = access
                fields.append(record)

        methods = []
        index = 0
        for count in (direct_methods, virtual_methods):
            index = 0
            for _ in range(count):
                diff, offset = self._uleb(offset)
                index += diff
                access, offset = self._uleb(offset)
                code_off, offset = self._uleb(offset)
                record = self.method_id(index)
                record['access'] = access
                record['code_off'] = code_off
                methods.append(record)
        return {'fields': fields, 'methods': methods}

    # -- queries --------------------------------------------------------

    def defines(self, descriptor):
        return any(item['descriptor'] == descriptor for item in self.classes())

    def find_class(self, descriptor):
        for item in self.classes():
            if item['descriptor'] == descriptor:
                return item
        return None

    def declared_methods(self, descriptor):
        item = self.find_class(descriptor)
        if item is None:
            return []
        return self.class_data(item['class_data_off'])['methods']

    def declared_fields(self, descriptor):
        item = self.find_class(descriptor)
        if item is None:
            return []
        return self.class_data(item['class_data_off'])['fields']


def _descriptor(name):
    if name.startswith('L') or name.startswith('['):
        return name
    return 'L' + name.replace('.', '/') + ';'


def _signature(method):
    proto = method['proto']
    return '%s(%s)%s' % (method['name'], ''.join(proto['parameters']), proto['returns'])


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('dex', nargs='+', help='DEX file(s) to read')
    parser.add_argument('--class', dest='klass', help='Print a class summary')
    parser.add_argument('--methods', help='Print declared method signatures of a class')
    parser.add_argument('--defines', help='Exit 0 when any DEX defines the class')
    parser.add_argument('--list', action='store_true', help='List defined classes')
    args = parser.parse_args(argv)

    dexes = [(path, Dex(Path(path).read_bytes())) for path in args.dex]

    if args.defines:
        target = _descriptor(args.defines)
        found = any(dex.defines(target) for _, dex in dexes)
        print(('defined: ' if found else 'missing: ') + target)
        return 0 if found else 1

    if args.list:
        for path, dex in dexes:
            print('==', path)
            for item in dex.classes():
                print(' ', item['descriptor'])
        return 0

    target = _descriptor(args.klass or args.methods or '')
    for path, dex in dexes:
        item = dex.find_class(target)
        if item is None:
            continue
        print('==', path, target)
        print('  access 0x%x super %s' % (item['access'], item['super']))
        if item['interfaces']:
            print('  interfaces', ', '.join(item['interfaces']))
        data = dex.class_data(item['class_data_off'])
        for field in data['fields']:
            print('  field 0x%x %s %s' % (field['access'], field['type'], field['name']))
        for method in data['methods']:
            print('  method 0x%x %s' % (method['access'], _signature(method)))
    return 0


if __name__ == '__main__':
    sys.exit(main())

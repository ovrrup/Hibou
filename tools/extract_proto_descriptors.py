#!/usr/bin/env python3
"""Extracts the protobuf descriptors embedded in the pinned web bundle.

The React bundle carries the compiled FileDescriptorSet for the local core as
base64 strings passed to its descriptor loader.  This tool decodes them and
writes a compact JSON index used by the native Kotlin client and its tests.

Usage: python3 tools/extract_proto_descriptors.py [--bundle PATH] [--out PATH]
"""
import argparse
import base64
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DEFAULT_BUNDLE = ROOT / 'app' / 'assets' / 'web' / 'generated' / 'main.js'
DEFAULT_OUT = ROOT / 'native-src' / 'proto' / 'descriptors.json'

DESCRIPTOR_RE = re.compile(r'Bc\("([A-Za-z0-9+/=]+)"')

TYPE_NAMES = {
    1: 'double', 2: 'float', 3: 'int64', 4: 'uint64', 5: 'int32',
    6: 'fixed64', 7: 'fixed32', 8: 'bool', 9: 'string', 10: 'group',
    11: 'message', 12: 'bytes', 13: 'uint32', 14: 'enum', 15: 'sfixed32',
    16: 'sfixed64', 17: 'sint32', 18: 'sint64',
}
LABEL_NAMES = {1: 'optional', 2: 'required', 3: 'repeated'}


def decode_descriptors(bundle_text):
    from google.protobuf import descriptor_pb2

    files = []
    seen = set()
    for encoded in DESCRIPTOR_RE.findall(bundle_text):
        padded = encoded + '=' * ((4 - len(encoded) % 4) % 4)
        try:
            raw = base64.b64decode(padded)
        except Exception:
            continue
        descriptor = descriptor_pb2.FileDescriptorProto()
        try:
            descriptor.ParseFromString(raw)
        except Exception:
            continue
        if descriptor.name in seen:
            continue
        seen.add(descriptor.name)
        files.append(descriptor)
    return files


def field_record(field):
    record = {
        'name': field.name,
        'number': field.number,
        'type': TYPE_NAMES.get(field.type, str(field.type)),
        'label': LABEL_NAMES.get(field.label, str(field.label)),
    }
    if field.type_name:
        record['type_name'] = field.type_name
    if field.default_value:
        record['default'] = field.default_value
    if field.oneof_index:
        record['oneof'] = field.oneof_index
    if field.proto3_optional:
        record['proto3_optional'] = True
    return record


def message_record(message, prefix):
    full = prefix + message.name
    record = {
        'full_name': full,
        'fields': [field_record(field) for field in message.field],
        'oneofs': [oneof.name for oneof in message.oneof_decl],
        'nested': [message_record(nested, full + '.') for nested in message.nested_type],
    }
    if message.enum_type:
        record['enums'] = [enum_record(enum, full + '.') for enum in message.enum_type]
    if message.options.map_entry:
        record['map_entry'] = True
    return record


def enum_record(enum, prefix):
    return {
        'full_name': prefix + enum.name,
        'values': [{'name': value.name, 'number': value.number} for value in enum.value],
    }


DEFAULT_ROOTS = [
    'exa.language_server_pb.GetAvailableModelsRequest',
    'exa.language_server_pb.GetAvailableModelsResponse',
    'exa.language_server_pb.GetAllCascadeTrajectoriesRequest',
    'exa.language_server_pb.GetAllCascadeTrajectoriesResponse',
    'exa.language_server_pb.GetCascadeTrajectoryRequest',
    'exa.language_server_pb.GetCascadeTrajectoryResponse',
    'exa.language_server_pb.GetCascadeTrajectoryStepsRequest',
    'exa.language_server_pb.GetCascadeTrajectoryStepsResponse',
    'exa.language_server_pb.SendUserCascadeMessageRequest',
    'exa.language_server_pb.SendUserCascadeMessageResponse',
    'exa.language_server_pb.GetUserSettingsRequest',
    'exa.language_server_pb.GetUserSettingsResponse',
    'exa.language_server_pb.SetUserSettingsRequest',
    'exa.language_server_pb.SetUserSettingsResponse',
    'exa.language_server_pb.GetAuthStatusRequest',
    'exa.language_server_pb.GetAuthStatusResponse',
    'exa.language_server_pb.GetMcpServerStatesRequest',
    'exa.language_server_pb.GetMcpServerStatesResponse',
    'exa.language_server_pb.GetAllSkillsRequest',
    'exa.language_server_pb.GetAllSkillsResponse',
    'exa.language_server_pb.GetConversationMetadataRequest',
    'exa.language_server_pb.GetConversationMetadataResponse',
    'exa.language_server_pb.StartCascadeRequest',
    'exa.language_server_pb.StartCascadeResponse',
    'exa.language_server_pb.CancelCascadeInvocationRequest',
    'exa.language_server_pb.CancelCascadeInvocationResponse',
    'exa.language_server_pb.DeleteCascadeTrajectoryRequest',
    'exa.language_server_pb.DeleteCascadeTrajectoryResponse',
    'exa.language_server_pb.ForkConversationRequest',
    'exa.language_server_pb.ForkConversationResponse',
    'exa.language_server_pb.HandleCascadeUserInteractionRequest',
    'exa.language_server_pb.HandleCascadeUserInteractionResponse',
    'exa.language_server_pb.SendAllQueuedMessagesRequest',
    'exa.language_server_pb.SendAllQueuedMessagesResponse',
    'exa.language_server_pb.WaitForConversationFullyIdleRequest',
    'exa.language_server_pb.WaitForConversationFullyIdleResponse',
    'exa.language_server_pb.GetCascadeTrajectoryGeneratorMetadataRequest',
    'exa.language_server_pb.GetCascadeTrajectoryGeneratorMetadataResponse',
    'exa.jetski_cortex_pb.StreamAgentStateUpdatesRequest',
    'exa.jetski_cortex_pb.StreamAgentStateUpdatesResponse',
]


def reachable(messages, enums, roots):
    """Returns the set of message/enum names reachable from the root messages."""
    keep_messages = set()
    keep_enums = set()
    pending = [root for root in roots if root in messages]
    while pending:
        name = pending.pop()
        if name in keep_messages or name not in messages:
            continue
        keep_messages.add(name)
        record = messages[name]
        for field in record['fields']:
            type_name = field.get('type_name', '').lstrip('.')
            if not type_name:
                continue
            if field['type'] == 'message' and type_name in messages:
                pending.append(type_name)
            elif field['type'] == 'enum' and type_name in enums:
                keep_enums.add(type_name)
    return keep_messages, keep_enums


def build_index(files, roots=None):
    services = {}
    messages = {}
    enums = {}
    file_list = []

    def collect_message(message, prefix):
        record = message_record(message, prefix)
        messages[record['full_name']] = record
        for nested in message.nested_type:
            collect_message(nested, record['full_name'] + '.')
        for enum in message.enum_type:
            enum_record_value = enum_record(enum, record['full_name'] + '.')
            enums[enum_record_value['full_name']] = enum_record_value

    for descriptor in files:
        file_list.append({
            'name': descriptor.name,
            'package': descriptor.package,
            'dependencies': list(descriptor.dependency),
        })
        for message in descriptor.message_type:
            collect_message(message, (descriptor.package + '.') if descriptor.package else '')
        for enum in descriptor.enum_type:
            enum_record_value = enum_record(enum, (descriptor.package + '.') if descriptor.package else '')
            enums[enum_record_value['full_name']] = enum_record_value
        for service in descriptor.service:
            full_service = ((descriptor.package + '.') if descriptor.package else '') + service.name
            services[full_service] = {
                'file': descriptor.name,
                'methods': [{
                    'name': method.name,
                    'input': method.input_type.lstrip('.'),
                    'output': method.output_type.lstrip('.'),
                    'client_streaming': bool(method.client_streaming),
                    'server_streaming': bool(method.server_streaming),
                } for method in service.method],
            }

    if roots:
        keep_messages, keep_enums = reachable(messages, enums, roots)
        messages = {name: record for name, record in messages.items() if name in keep_messages}
        enums = {name: record for name, record in enums.items() if name in keep_enums}

    return {
        'source': 'app/assets/web/generated/main.js',
        'roots': roots or [],
        'files': file_list,
        'services': services,
        'messages': messages,
        'enums': enums,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--bundle', default=str(DEFAULT_BUNDLE))
    parser.add_argument('--out', default=str(DEFAULT_OUT))
    parser.add_argument('--all', action='store_true',
                        help='Keep every message instead of the chat root closure')
    args = parser.parse_args()

    bundle = Path(args.bundle)
    if not bundle.is_file():
        raise SystemExit('Web bundle not found: ' + str(bundle))
    try:
        import google.protobuf  # noqa: F401
    except Exception:
        raise SystemExit('Install protobuf first: pip install protobuf')

    files = decode_descriptors(bundle.read_text(errors='ignore'))
    if not files:
        raise SystemExit('No protobuf descriptors found in ' + str(bundle))

    index = build_index(files, roots=None if args.all else DEFAULT_ROOTS)
    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(index, ensure_ascii=False, separators=(',', ':')) + '\n')
    print(f'Wrote {out} ({out.stat().st_size} bytes): '
          f'{len(index["files"])} files, {len(index["services"])} services, '
          f'{len(index["messages"])} messages')
    return 0


if __name__ == '__main__':
    sys.exit(main())

#!/usr/bin/env python3
"""Reference implementation of the Connect streaming envelope.

`HibouConnect.kt` speaks ConnectRPC with the JSON codec because the shipped web
bundle does the same (`useBinaryFormat:false`).  Streaming responses are framed
as repeated envelopes:

    byte 0      flags (0x01 gzip, 0x02 end-of-stream)
    byte 1..4   payload length, big-endian uint32
    byte 5..    payload (JSON for application/connect+json)

This module is the executable specification used by the tests.  It also lets a
developer replay a captured stream without an Android device:

    python3 tools/connect_frames.py capture.bin
"""
import argparse
import json
import sys
from pathlib import Path

FLAG_COMPRESSED = 0x01
FLAG_END_STREAM = 0x02
HEADER_BYTES = 5
MAX_PAYLOAD = 64 * 1024 * 1024


class EnvelopeError(ValueError):
    pass


def encode_envelope(payload, flags=0):
    """Returns the framed bytes for one message."""
    if isinstance(payload, str):
        payload = payload.encode('utf-8')
    if len(payload) > MAX_PAYLOAD:
        raise EnvelopeError('payload too large')
    header = bytes([flags & 0xff]) + len(payload).to_bytes(4, 'big')
    return header + payload


def iter_envelopes(data):
    """Yields (flags, payload_bytes) tuples; raises on truncation."""
    offset = 0
    total = len(data)
    while offset < total:
        if offset + HEADER_BYTES > total:
            raise EnvelopeError('truncated envelope header at offset %d' % offset)
        flags = data[offset]
        length = int.from_bytes(data[offset + 1:offset + HEADER_BYTES], 'big')
        offset += HEADER_BYTES
        if length > MAX_PAYLOAD:
            raise EnvelopeError('envelope length %d exceeds limit' % length)
        if offset + length > total:
            raise EnvelopeError('truncated envelope payload at offset %d' % offset)
        yield flags, data[offset:offset + length]
        offset += length


def decode_stream(data):
    """Decodes a JSON stream into (messages, trailer) where trailer is the
    end-stream object (`{"metadata": ...}` or `{"error": ...}`)."""
    messages = []
    trailer = None
    for flags, payload in iter_envelopes(data):
        if flags & FLAG_COMPRESSED:
            raise EnvelopeError('compressed envelopes are not supported')
        text = payload.decode('utf-8')
        if flags & FLAG_END_STREAM:
            trailer = json.loads(text) if text.strip() else {}
            break
        if text.strip():
            messages.append(json.loads(text))
    return messages, trailer


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('capture', nargs='?', help='file with a captured stream')
    parser.add_argument('--self-test', action='store_true')
    args = parser.parse_args(argv)

    if args.self_test:
        payloads = ['{"update":{"conversationId":"c1"}}', '{"update":{"fullyIdle":true}}']
        stream = b''.join(encode_envelope(item) for item in payloads)
        stream += encode_envelope('{"metadata":{}}', FLAG_END_STREAM)
        messages, trailer = decode_stream(stream)
        assert messages == [json.loads(item) for item in payloads], messages
        assert trailer == {'metadata': {}}, trailer
        print('connect frames self-test OK (%d messages)' % len(messages))
        return 0

    if not args.capture:
        parser.error('capture file or --self-test is required')
    data = Path(args.capture).read_bytes()
    messages, trailer = decode_stream(data)
    for message in messages:
        print(json.dumps(message, indent=2, ensure_ascii=False))
    if trailer:
        print('trailer:', json.dumps(trailer, ensure_ascii=False))
    return 0


if __name__ == '__main__':
    sys.exit(main())

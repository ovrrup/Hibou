#!/usr/bin/env python3
"""Contract tests for the M2 core client (Connect JSON + DEX reflection)."""
import json
import re
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / 'tools'))

import connect_frames
from dex_inspect import Dex

KT = ROOT / 'native-src' / 'com' / 'agy' / 'ru'
DESCRIPTORS = json.loads((ROOT / 'native-src' / 'proto' / 'descriptors.json').read_text())
MESSAGES = DESCRIPTORS['messages']


def camel(field):
    """proto snake_case -> Connect JSON lowerCamelCase."""
    head, *tail = field.split('_')
    return head + ''.join(part[:1].upper() + part[1:] for part in tail)


def source(name):
    return (KT / name).read_text(encoding='utf-8')


class TestConnectConstants(unittest.TestCase):
    def setUp(self):
        self.source = source('HibouConnect.kt')

    def test_service_and_headers_match_the_bundle(self):
        self.assertIn('const val SERVICE = "exa.language_server_pb.LanguageServerService"', self.source)
        self.assertIn('const val CSRF_HEADER = "x-codeium-csrf-token"', self.source)
        self.assertIn('const val PROTOCOL_HEADER = "Connect-Protocol-Version"', self.source)
        self.assertIn('const val PROTOCOL_VERSION = "1"', self.source)

    def test_uses_the_json_codec_like_the_web_bundle(self):
        self.assertIn('const val UNARY_TYPE = "application/json"', self.source)
        self.assertIn('const val STREAM_TYPE = "application/connect+json"', self.source)
        self.assertNotIn('"application/proto"', self.source)
        self.assertNotIn('"application/connect+proto"', self.source)

    def test_path_is_service_slash_method(self):
        self.assertIn('fun path(method: String): String = "/$SERVICE/$method"', self.source)

    def test_envelope_constants_match_reference(self):
        self.assertIn('const val FLAG_COMPRESSED = 0x01', self.source)
        self.assertIn('const val FLAG_END_STREAM = 0x02', self.source)
        self.assertIn('const val ENVELOPE_HEADER_BYTES = 5', self.source)
        self.assertEqual(connect_frames.FLAG_COMPRESSED, 0x01)
        self.assertEqual(connect_frames.FLAG_END_STREAM, 0x02)
        self.assertEqual(connect_frames.HEADER_BYTES, 5)


class TestLoopbackGuard(unittest.TestCase):
    def setUp(self):
        self.source = source('HibouConnect.kt')

    def test_refuses_non_loopback_hosts(self):
        self.assertIn('private fun isLoopback(url: URL)', self.source)
        for host in ['127.0.0.1', 'localhost', '::1']:
            self.assertIn('"%s"' % host, self.source)
        self.assertIn('refusing to send core credentials', self.source)

    def test_core_url_is_loopback_https(self):
        core = source('HibouCore.kt')
        self.assertIn('"https://127.0.0.1:$port"', core)

    def test_client_never_uses_a_hardcoded_port(self):
        for name in ['HibouChatClient.kt', 'HibouConnect.kt']:
            text = source(name)
            self.assertNotRegex(text, r'127\.0\.0\.1:\d{2,5}(?!\$)',
                                f'{name} must not hardcode a core port')


class TestDescriptorContract(unittest.TestCase):
    """Every JSON key the Kotlin client writes or reads must exist in the
    descriptor index, so a schema change breaks the test instead of the app."""

    # message -> JSON keys used by the native client
    KEYS = {
        'exa.codeium_common_pb.Metadata': [
            'ideName', 'ideVersion', 'extensionName', 'extensionVersion',
            'locale', 'os', 'productName', 'disableTelemetry', 'sessionId',
        ],
        'exa.language_server_pb.StartCascadeRequest': [
            'cascadeId', 'trajectoryType', 'source', 'metadata', 'workspaceUris',
        ],
        'exa.language_server_pb.SendUserCascadeMessageRequest': [
            'cascadeId', 'items', 'metadata',
        ],
        'exa.codeium_common_pb.TextOrScopeItem': ['text'],
        'exa.language_server_pb.CancelCascadeInvocationRequest': ['cascadeId'],
        'exa.language_server_pb.DeleteCascadeTrajectoryRequest': ['cascadeId'],
        'exa.jetski_cortex_pb.StreamAgentStateUpdatesRequest': [
            'conversationId', 'subscriberId', 'trajectoryVerbosity', 'disableRehydration',
        ],
        'exa.language_server_pb.GetAllCascadeTrajectoriesResponse': ['trajectorySummaries'],
        'exa.jetski_cortex_pb.CascadeTrajectorySummary': [
            'summary', 'stepCount', 'lastModifiedTime', 'status',
            'latestNotifyUserStep',
        ],
        'exa.jetski_cortex_pb.CortexTrajectoryStepWithIndex': ['step'],
        'exa.cortex_pb.CortexStepNotifyUser': ['notificationContent'],
        'google.protobuf.Timestamp': ['seconds', 'nanos'],
        'exa.language_server_pb.GetCascadeTrajectoryRequest': [
            'cascadeId', 'trajectoryVerbosity',
        ],
        'exa.language_server_pb.GetCascadeTrajectoryResponse': ['trajectory'],
        'gemini_coder.Trajectory': ['steps'],
        'gemini_coder.Step': [
            'type', 'status', 'plannerResponse', 'userInput', 'runCommand',
            'viewFile', 'writeToFile', 'grepSearch', 'errorMessage',
        ],
        'exa.cortex_pb.CortexStepPlannerResponse': [
            'response', 'modifiedResponse', 'thinking',
        ],
        'exa.cortex_pb.CortexStepUserInput': ['query', 'items'],
        'exa.cortex_pb.CortexStepRunCommand': ['commandLine'],
        'exa.cortex_pb.CortexStepViewFile': ['absolutePathUri'],
        'exa.cortex_pb.CortexStepWriteToFile': ['targetFileUri'],
        'exa.cortex_pb.CortexStepGrepSearch': ['query'],
        'exa.cortex_pb.CortexStepErrorMessage': ['error'],
        'exa.cortex_pb.CortexErrorDetails': ['userErrorMessage', 'shortError'],
        'exa.language_server_pb.GetAvailableModelsRequest': ['forceRefresh'],
        'exa.language_server_pb.GetAvailableModelsResponse': ['response'],
        'google.internal.cloud.code.v1internal.FetchAvailableModelsResponse': ['models'],
        'google.internal.cloud.code.v1internal.ModelDetails': [
            'displayName', 'recommended', 'supportsThinking', 'disabled',
        ],
        'exa.jetski_cortex_pb.AgentStateUpdate': [
            'mainTrajectoryUpdate', 'fullyIdle',
        ],
        'exa.jetski_cortex_pb.TrajectoryUpdate': ['stepsUpdate'],
        'exa.jetski_cortex_pb.StepsUpdate': ['indices', 'steps'],
    }

    def test_every_used_key_exists_in_the_schema(self):
        for message, keys in self.KEYS.items():
            self.assertIn(message, MESSAGES)
            fields = {camel(field['name']) for field in MESSAGES[message]['fields']}
            for key in keys:
                self.assertIn(key, fields, f'{message} has no field {key}')

    def test_sources_contain_the_declared_keys(self):
        combined = '\n'.join(path.read_text(encoding='utf-8')
                             for path in KT.glob('*.kt'))
        for message, keys in self.KEYS.items():
            for key in keys:
                self.assertIn('"%s"' % key, combined,
                              f'{message}.{key} is not referenced by the native UI')

    def test_chat_methods_are_in_the_service(self):
        methods = {m['name'] for m in
                   DESCRIPTORS['services']['exa.language_server_pb.LanguageServerService']['methods']}
        for name in ['StartCascade', 'SendUserCascadeMessage', 'StreamAgentStateUpdates',
                     'CancelCascadeInvocation', 'DeleteCascadeTrajectory',
                     'GetCascadeTrajectory', 'GetAllCascadeTrajectories',
                     'GetAvailableModels']:
            self.assertIn(name, methods)

    def test_enum_wire_names(self):
        client = source('HibouChatClient.kt')
        enums = DESCRIPTORS['enums']
        expected = {
            'VERBOSITY_PROD_UI': ('exa.jetski_cortex_pb.ClientTrajectoryVerbosity',
                                  'CLIENT_TRAJECTORY_VERBOSITY_PROD_UI'),
            'SOURCE_CASCADE_CLIENT': ('exa.cortex_pb.CortexTrajectorySource',
                                      'CORTEX_TRAJECTORY_SOURCE_CASCADE_CLIENT'),
            'TYPE_USER_MAINLINE': ('exa.cortex_pb.CortexTrajectoryType',
                                   'CORTEX_TRAJECTORY_TYPE_USER_MAINLINE'),
        }
        for constant, (enum, value) in expected.items():
            names = [item['name'] for item in enums[enum]['values']]
            self.assertIn(value, names)
            self.assertIn('const val %s = "%s"' % (constant, value), client)


class TestEnvelopeFraming(unittest.TestCase):
    def test_round_trip(self):
        payloads = [{'update': {'conversationId': 'c1'}}, {'update': {'fullyIdle': True}}]
        stream = b''.join(connect_frames.encode_envelope(json.dumps(item)) for item in payloads)
        stream += connect_frames.encode_envelope('{"metadata":{}}', connect_frames.FLAG_END_STREAM)
        messages, trailer = connect_frames.decode_stream(stream)
        self.assertEqual(messages, payloads)
        self.assertEqual(trailer, {'metadata': {}})

    def test_header_is_big_endian_length(self):
        frame = connect_frames.encode_envelope(b'abcd')
        self.assertEqual(frame[0], 0)
        self.assertEqual(int.from_bytes(frame[1:5], 'big'), 4)
        self.assertEqual(frame[5:], b'abcd')

    def test_truncated_payload_is_rejected(self):
        frame = connect_frames.encode_envelope(b'{"a":1}')
        with self.assertRaises(connect_frames.EnvelopeError):
            list(connect_frames.iter_envelopes(frame[:-2]))

    def test_truncated_header_is_rejected(self):
        with self.assertRaises(connect_frames.EnvelopeError):
            list(connect_frames.iter_envelopes(b'\x00\x00'))

    def test_compressed_envelope_is_rejected(self):
        frame = connect_frames.encode_envelope('{}', connect_frames.FLAG_COMPRESSED)
        with self.assertRaises(connect_frames.EnvelopeError):
            connect_frames.decode_stream(frame)

    def test_error_trailer_is_surfaced(self):
        stream = connect_frames.encode_envelope(
            '{"error":{"code":"internal","message":"boom"}}',
            connect_frames.FLAG_END_STREAM)
        _, trailer = connect_frames.decode_stream(stream)
        self.assertEqual(trailer['error']['code'], 'internal')


class TestDexContract(unittest.TestCase):
    """The reflection targets in HibouCore/HibouChatClient must exist in the
    shipped DEX, otherwise the native UI silently loses the core port."""

    @classmethod
    def setUpClass(cls):
        cls.dex = Dex((ROOT / 'app' / 'classes.dex').read_bytes())

    def methods(self, descriptor):
        return {(m['name'], ''.join(m['proto']['parameters']), m['proto']['returns'])
                for m in self.dex.declared_methods(descriptor)}

    def test_main_activity_is_subclassable(self):
        activity = self.dex.find_class('Lcom/agy/ui/MainActivity;')
        self.assertIsNotNone(activity)
        self.assertTrue(activity['access'] & 0x1, 'MainActivity must be public')
        self.assertFalse(activity['access'] & 0x10, 'MainActivity must not be final')
        self.assertEqual(activity['super'], 'Landroid/app/Activity;')

    def test_main_activity_reflection_targets(self):
        fields = {field['name']: field for field in
                  self.dex.declared_fields('Lcom/agy/ui/MainActivity;')}
        self.assertIn('androidBridge', fields)
        self.assertEqual(fields['androidBridge']['type'], 'Lcom/agy/bridge/AndroidBridge;')
        self.assertIn('activeActivity', fields)
        self.assertTrue(fields['activeActivity']['access'] & 0x8, 'activeActivity must be static')
        self.assertIn(('showFatalError', 'Ljava/lang/String;', 'V'),
                      self.methods('Lcom/agy/ui/MainActivity;'))
        self.assertIn(('onCreate', 'Landroid/os/Bundle;', 'V'),
                      self.methods('Lcom/agy/ui/MainActivity;'))

    def test_android_bridge_exposes_port_and_csrf(self):
        methods = self.methods('Lcom/agy/bridge/AndroidBridge;')
        self.assertIn(('getEnginePort', '', 'I'), methods)
        self.assertIn(('getCsrfToken', '', 'Ljava/lang/String;'), methods)
        self.assertIn(('getEngineBaseUrl', '', 'Ljava/lang/String;'), methods)
        self.assertIn(('isEngineReady', '', 'Z'), methods)

    def test_core_source_only_uses_verified_bridge_methods(self):
        text = source('HibouCore.kt')
        for name in ['getEnginePort', 'getCsrfToken', 'isEngineReady', 'getConfig']:
            self.assertIn('"%s"' % name, text)


class TestClientFlow(unittest.TestCase):
    def test_cascade_id_is_generated_client_side(self):
        text = source('HibouChatClient.kt')
        self.assertIn('UUID.randomUUID().toString()', text)
        self.assertNotIn('response.optString("cascadeId"', text)

    def test_stream_reads_main_trajectory_steps(self):
        text = source('HibouChatClient.kt')
        self.assertIn('"mainTrajectoryUpdate"', text)
        self.assertIn('"stepsUpdate"', text)
        self.assertIn('"indices"', text)

    def test_activity_cancels_the_stream_on_destroy(self):
        activity = source('HibouChatActivity.kt')
        self.assertIn('override fun onDestroy()', activity)
        self.assertIn('chatView?.onDestroy()', activity)


if __name__ == '__main__':
    unittest.main()

#!/usr/bin/env python3
"""Tests for the staged native Kotlin UI migration (M0/M1)."""
import json
import re
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))
sys.path.insert(0, str(ROOT / 'tools'))

import rebuild
import patch_manifest


class TestManifestPatch(unittest.TestCase):
    def setUp(self):
        self.original = (ROOT / 'app' / 'AndroidManifest.xml').read_bytes()

    def test_patch_is_idempotent(self):
        patched, changed = patch_manifest.patch_manifest(self.original)
        self.assertTrue(changed)
        self.assertTrue(patch_manifest.has_native_launcher(patched))
        self.assertEqual(len(patched), len(self.original))

        again, changed_again = patch_manifest.patch_manifest(patched)
        self.assertFalse(changed_again)
        self.assertEqual(again, patched)

    def test_patch_keeps_launcher_attributes(self):
        patched, _ = patch_manifest.patch_manifest(self.original)
        strings = [value for _, _, value, _ in patch_manifest.iter_strings(patched)]
        self.assertIn(patch_manifest.DEFAULT_ACTIVITY, strings)
        self.assertNotIn(patch_manifest.ORIGINAL_LAUNCHER, strings)
        # The intent-filter and theme resources are untouched.
        self.assertIn('android.intent.action.MAIN', strings)
        self.assertIn('android.intent.category.LAUNCHER', strings)

    def test_check_detects_unpatched_manifest(self):
        self.assertFalse(patch_manifest.has_native_launcher(self.original))
        self.assertEqual(
            patch_manifest.main(['--check', '--input', str(ROOT / 'app' / 'AndroidManifest.xml')]),
            1,
        )


class TestDescriptorIndex(unittest.TestCase):
    def test_chat_methods_are_indexed(self):
        index = json.loads((ROOT / 'native-src' / 'proto' / 'descriptors.json').read_text())
        service = index['services']['exa.language_server_pb.LanguageServerService']
        methods = {method['name']: method for method in service['methods']}
        for name in [
            'GetAvailableModels',
            'GetAllCascadeTrajectories',
            'GetCascadeTrajectorySteps',
            'SendUserCascadeMessage',
            'GetAuthStatus',
            'GetMcpServerStates',
            'GetAllSkills',
        ]:
            self.assertIn(name, methods)
            self.assertIn(methods[name]['input'], index['messages'])
            self.assertIn(methods[name]['output'], index['messages'])

        streaming = index['services']['exa.language_server_pb.LanguageServerService']['methods']
        agent = [m for m in streaming if m['name'] == 'StreamAgentStateUpdates'][0]
        self.assertTrue(agent['server_streaming'])


class TestNativeShellSources(unittest.TestCase):
    KT = ROOT / 'native-src' / 'com' / 'agy' / 'ru'

    def test_activity_reuses_main_activity(self):
        source = (self.KT / 'HibouChatActivity.kt').read_text()
        self.assertIn('class HibouChatActivity : com.agy.ui.MainActivity()', source)
        self.assertIn('dismissSplash()', source)
        self.assertIn('setContentView(view)', source)
        self.assertIn('showWebFallback', source)

    def test_shell_has_navigation_and_composer(self):
        source = (self.KT / 'HibouChatView.kt').read_text()
        for token in ['buildTopBar', 'buildComposer', 'buildDrawer', 'buildEmptyState',
                      'submit()', 'handleBack', 'onOpenWeb']:
            self.assertIn(token, source)

    def test_store_persists_conversations(self):
        source = (self.KT / 'HibouChatStore.kt').read_text()
        self.assertIn('hibou_native_chat.json', source)
        self.assertIn('fun append(', source)
        self.assertIn('fun save(', source)
        self.assertIn('fun load(', source)

    def test_no_emojis_in_native_ui(self):
        emoji = re.compile(r'[\U00010000-\U0010ffff]')
        for path in list(self.KT.glob('*.kt')) + [ROOT / 'native-src' / 'AndroidManifest.xml']:
            text = path.read_text(encoding='utf-8')
            self.assertEqual(emoji.findall(text), [], f'{path.name} must not contain emojis')


class TestBuildContract(unittest.TestCase):
    def test_native_launcher_descriptor_matches_manifest(self):
        self.assertEqual(
            rebuild.NATIVE_LAUNCHER,
            'L' + patch_manifest.DEFAULT_ACTIVITY.replace('.', '/') + ';',
        )

    def test_dex_class_lookup(self):
        classes = (ROOT / 'app' / 'classes4.dex').read_bytes()
        self.assertTrue(rebuild.dex_defines_class(classes, 'Lcom/agy/ru/HibouBridge;'))
        self.assertFalse(rebuild.dex_defines_class(classes, 'Lcom/agy/ru/HibouChatActivity;'))

    def test_build_fails_closed_when_toolchain_is_missing(self):
        source = (ROOT / 'rebuild.py').read_text()
        self.assertIn('REQUIRE_KOTLIN', source)
        self.assertIn('classes5.dex', source)
        self.assertIn('HIBOU_NATIVE_UI', source)

    def test_stdlib_bundler_exists(self):
        script = ROOT / 'tools' / 'bundle_kotlin_stdlib.py'
        self.assertTrue(script.is_file())
        self.assertIn('kotlin-stdlib.jar', script.read_text())


if __name__ == '__main__':
    unittest.main()

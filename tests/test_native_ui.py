#!/usr/bin/env python3
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

class TestNativeKotlinUI(unittest.TestCase):
    def test_native_kotlin_files_exist_and_no_emojis(self):
        kt_dir = ROOT / "native-src" / "com" / "agy" / "ru"
        expected = ["HibouNativeSplash.kt", "HibouPermissionSheet.kt", "HibouControlCenterDialog.kt", "HibouBridge.kt"]
        emoji_pattern = re.compile(r"[\U00010000-\U0010ffff]")

        for fname in expected:
            fpath = kt_dir / fname
            self.assertTrue(fpath.is_file(), f"{fname} should exist")
            text = fpath.read_text(encoding="utf-8")
            self.assertEqual(len(emoji_pattern.findall(text)), 0, f"{fname} must contain zero emojis")

    def test_dex_files_integrity(self):
        import rebuild
        classes_dex = (ROOT / "app" / "classes.dex").read_bytes()
        classes4_dex = (ROOT / "app" / "classes4.dex").read_bytes()
        rebuild.validate_dex(classes_dex, "classes.dex")
        rebuild.validate_dex(classes4_dex, "classes4.dex")
        self.assertGreater(len(classes4_dex), 30000)

    def test_smali_delegation_to_native_ui(self):
        splash_loading = (ROOT / ".cache" / "test_apk_out" / "smali" / "com" / "agy" / "ui" / "splash" / "SplashLoadingHtml.smali").read_text()
        splash_overlay = (ROOT / ".cache" / "test_apk_out" / "smali" / "com" / "agy" / "ui" / "splash" / "SplashOverlayHtml.smali").read_text()
        agy_tools = (ROOT / "native-src" / "smali" / "com" / "agy" / "ru" / "AgyTools.smali").read_text()

        self.assertIn("Lcom/agy/ru/HibouBridge;->showNativeSplash", splash_loading)
        self.assertIn("Lcom/agy/ru/HibouBridge;->showFatalError", splash_overlay)
        self.assertIn("openControlCenter", agy_tools)
        self.assertIn("showPermissionSheet", agy_tools)

if __name__ == "__main__":
    unittest.main()

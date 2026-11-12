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
        # The compiled DEX is the source of truth. Decompiled smali under
        # .cache/test_apk_out is an optional local artifact, so fall back to
        # scanning classes.dex when it is not present.
        classes_dex = (ROOT / "app" / "classes.dex").read_bytes()
        self.assertIn(b"SplashLoadingHtml", classes_dex)
        self.assertIn(b"SplashOverlayHtml", classes_dex)
        self.assertIn(b"showNativeSplash", classes_dex)
        self.assertIn(b"showFatalError", classes_dex)

        agy_tools = (ROOT / "native-src" / "smali" / "com" / "agy" / "ru" / "AgyTools.smali").read_text()
        self.assertIn("openControlCenter", agy_tools)
        self.assertIn("showPermissionSheet", agy_tools)

        smali_dir = ROOT / ".cache" / "test_apk_out" / "smali" / "com" / "agy" / "ui" / "splash"
        if smali_dir.is_dir():
            splash_loading = (smali_dir / "SplashLoadingHtml.smali").read_text()
            splash_overlay = (smali_dir / "SplashOverlayHtml.smali").read_text()
            self.assertIn("Lcom/agy/ru/HibouBridge;->showNativeSplash", splash_loading)
            self.assertIn("Lcom/agy/ru/HibouBridge;->showFatalError", splash_overlay)

if __name__ == "__main__":
    unittest.main()

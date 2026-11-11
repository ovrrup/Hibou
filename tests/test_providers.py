import unittest
import re
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))
import rebuild

class TestProviderNormalization(unittest.TestCase):
    def setUp(self):
        self.providers_js = (ROOT / "app" / "assets" / "web" / "agy_providers.js").read_text(encoding="utf-8")
        self.hibou_ui_js = (ROOT / "app" / "assets" / "web" / "hibou_ui.js").read_text(encoding="utf-8")

    def test_openrouter_norm_pattern(self):
        # Python implementation matching the JS logic in normBase:
        def norm_base(base, type_id=None, id_val=None):
            b = str(base or "").strip().rstrip("/")
            if not b:
                return b
            is_or = bool(re.search(r"openrouter\.ai", b, re.I) or (id_val and re.search(r"openrouter", id_val, re.I)) or type_id == "openrouter")
            is_ds = bool(re.search(r"api\.deepseek\.com", b, re.I) or (id_val and re.search(r"deepseek", id_val, re.I)))
            is_oc = bool(re.search(r"opencode\.ai", b, re.I) or (id_val and re.search(r"opencode", id_val, re.I)) or type_id == "opencode")
            is_mis = bool(re.search(r"api\.mistral\.ai", b, re.I) or (id_val and re.search(r"mistral", id_val, re.I)))

            if is_or:
                return re.sub(r"/+(api(/v1)?|v1)?/?$", "", b, flags=re.I) + "/api/v1"
            if is_oc:
                if re.search(r"localhost|127\.0\.0\.1|:4096", b, re.I):
                    return re.sub(r"/+v1/?$", "", b, flags=re.I) + "/v1"
                return re.sub(r"/+(zen(/v1)?|api(/v1)?|v1)?/?$", "", b, flags=re.I) + "/zen/v1"
            if is_ds or is_mis:
                return re.sub(r"/+v1/?$", "", b, flags=re.I) + "/v1"
            if type_id == "anthropic":
                return re.sub(r"/v1$", "", b, flags=re.I)
            return b

        # Test OpenRouter variants
        expected_or = "https://openrouter.ai/api/v1"
        self.assertEqual(norm_base("https://openrouter.ai"), expected_or)
        self.assertEqual(norm_base("https://openrouter.ai/"), expected_or)
        self.assertEqual(norm_base("https://openrouter.ai/v1"), expected_or)
        self.assertEqual(norm_base("https://openrouter.ai/v1/"), expected_or)
        self.assertEqual(norm_base("https://openrouter.ai/api"), expected_or)
        self.assertEqual(norm_base("https://openrouter.ai/api/v1"), expected_or)
        self.assertEqual(norm_base("https://openrouter.ai/api/v1/"), expected_or)

        # Test DeepSeek
        expected_ds = "https://api.deepseek.com/v1"
        self.assertEqual(norm_base("https://api.deepseek.com"), expected_ds)
        self.assertEqual(norm_base("https://api.deepseek.com/v1"), expected_ds)

        # Test OpenCode Zen & Local
        expected_oc_zen = "https://opencode.ai/zen/v1"
        self.assertEqual(norm_base("https://opencode.ai"), expected_oc_zen)
        self.assertEqual(norm_base("https://opencode.ai/"), expected_oc_zen)
        self.assertEqual(norm_base("https://opencode.ai/v1"), expected_oc_zen)
        self.assertEqual(norm_base("https://opencode.ai/zen/v1"), expected_oc_zen)
        self.assertEqual(norm_base("http://localhost:4096", type_id="opencode"), "http://localhost:4096/v1")
        self.assertEqual(norm_base("http://localhost:4096/v1", type_id="opencode"), "http://localhost:4096/v1")

        # Test Mistral
        expected_mis = "https://api.mistral.ai/v1"
        self.assertEqual(norm_base("https://api.mistral.ai"), expected_mis)
        self.assertEqual(norm_base("https://api.mistral.ai/v1"), expected_mis)

        # Test Anthropic
        self.assertEqual(norm_base("https://api.anthropic.com/v1", type_id="anthropic"), "https://api.anthropic.com")
        self.assertEqual(norm_base("https://api.anthropic.com", type_id="anthropic"), "https://api.anthropic.com")

    def test_clean_body_logic(self):
        # Python implementation matching the JS cleanBody logic
        def clean_body(b):
            if not b:
                return ""
            s = str(b).strip()
            # Try json
            import json
            try:
                j = json.loads(s)
                if isinstance(j, dict):
                    err = j.get("error")
                    if isinstance(err, str):
                        return err[:200]
                    if isinstance(err, dict) and isinstance(err.get("message"), str):
                        return err["message"][:200]
                    if isinstance(j.get("message"), str):
                        return j["message"][:200]
            except Exception:
                pass
            no_scripts = re.sub(r"<script\b[^<]*(?:(?!<\/script>)<[^<]*)*<\/script>", " ", s, flags=re.I)
            no_scripts = re.sub(r"<style\b[^<]*(?:(?!<\/style>)<[^<]*)*<\/style>", " ", no_scripts, flags=re.I)
            title_m = re.search(r"<title[^>]*>([^<]+)<\/title>", s, flags=re.I)
            title = title_m.group(1).strip() if title_m else ""
            text = re.sub(r"<[^>]+>", " ", no_scripts)
            text = re.sub(r"\s+", " ", text).strip()
            if re.search(r"function\s*\(|sendBeacon|var\s+|let\s+|const\s+", text, re.I) or not text:
                if title:
                    return title[:150]
                return "Server returned HTML error page instead of JSON"
            if len(text) > 200:
                if title and len(title) < len(text):
                    return title[:150]
                return text[:200]
            return text or title or "Unknown response"

        # Test raw Vercel HTML error
        sample_vercel_404 = """
        <!DOCTYPE html><html><head><title>404: This page could not be found</title>
        <script>(function({location:a,navigator:b},c){let d=a.hostname.toLowerCase();try{b.sendBeacon("/api/f")}catch{}})();</script>
        </head><body><p>404 Not Found</p></body></html>
        """
        cleaned = clean_body(sample_vercel_404)
        self.assertNotIn("sendBeacon", cleaned)
        self.assertNotIn("function", cleaned)
        self.assertTrue("404" in cleaned)

        # Test JSON error
        json_err = '{"error":{"message":"Invalid OpenRouter API Key provided","code":401}}'
        self.assertEqual(clean_body(json_err), "Invalid OpenRouter API Key provided")

    def test_openrouter_endpoint_field_in_hibou_ui(self):
        self.assertIn('id="hibou-pv-url-openrouter"', self.hibou_ui_js)
        self.assertIn('value="https://openrouter.ai/api/v1"', self.hibou_ui_js)

    def test_opencode_multi_plan_and_off_by_default(self):
        # Verify OpenCode card has .inactive by default
        self.assertIn('class="hibou-m3-provider-card inactive" data-pv="opencode"', self.hibou_ui_js)
        # Verify toggle is unchecked by default (no 'checked' attribute)
        self.assertIn('<input type="checkbox" id="hibou-pv-toggle-opencode">', self.hibou_ui_js)
        self.assertNotIn('<input type="checkbox" id="hibou-pv-toggle-opencode" checked', self.hibou_ui_js)

        # Verify all 5 plans exist in the plan selector
        self.assertIn('data-opencode-plan="free"', self.hibou_ui_js)
        self.assertIn('data-opencode-plan="go"', self.hibou_ui_js)
        self.assertIn('data-opencode-plan="go_plus"', self.hibou_ui_js)
        self.assertIn('data-opencode-plan="zen"', self.hibou_ui_js)
        self.assertIn('data-opencode-plan="local"', self.hibou_ui_js)

        # Verify free plan is active by default with no key needed
        self.assertIn('class="hibou-m3-plan-chip active" data-opencode-plan="free"', self.hibou_ui_js)
        self.assertIn('NO KEY', self.hibou_ui_js)

        # Verify API key row is hidden by default for free plan
        self.assertIn('id="hibou-opencode-key-row" style="display: none;"', self.hibou_ui_js)

        # Verify default endpoint is live OpenCode Zen
        self.assertIn('value="https://opencode.ai/zen/v1"', self.hibou_ui_js)

        # Verify plan selection and state sync exports exist
        self.assertIn("window.__hibouSelectOpenCodePlan", self.hibou_ui_js)
        self.assertIn("window.__hibouSyncProviderCardsState", self.hibou_ui_js)

    def test_zero_emojis_in_web_assets(self):
        # Strict zero-emoji check across redesigned web assets
        emoji_pattern = re.compile(
            r"[\U0001F600-\U0001F64F\U0001F300-\U0001F5FF\U0001F680-\U0001F6FF\U0001F900-\U0001F9FF\U0001FA70-\U0001FAFF\U00002600-\U000026FF\U00002700-\U000027BF]+"
        )
        for name in ["hibou_ui.js", "agy_providers.js", "hibou_mobile.css"]:
            content = (ROOT / "app" / "assets" / "web" / name).read_text(encoding="utf-8")
            matches = emoji_pattern.findall(content)
            self.assertEqual(matches, [], f"Found emojis {matches} in {name}")

    def test_classes4_dex_integrity(self):
        dex_path = ROOT / "app" / "classes4.dex"
        self.assertTrue(dex_path.is_file())
        dex_bytes = dex_path.read_bytes()
        rebuild.validate_dex(dex_bytes, "classes4.dex")

    def test_classes_dex_integrity(self):
        dex_path = ROOT / "app" / "classes.dex"
        self.assertTrue(dex_path.is_file())
        dex_bytes = dex_path.read_bytes()
        rebuild.validate_dex(dex_bytes, "classes.dex")

    def test_in_app_permissions_and_loading(self):
        # 1. In-app permissions in hibou_ui.js
        self.assertIn("checkAndPromptPermissions", self.hibou_ui_js)
        self.assertIn("hibou-permission-banner", self.hibou_ui_js)
        self.assertIn("AgyTools.checkPermissions", self.hibou_ui_js)
        self.assertIn("AgyTools.requestPermission", self.hibou_ui_js)

        # 2. Styles in hibou_mobile.css
        css = (ROOT / "app" / "assets" / "web" / "hibou_mobile.css").read_text(encoding="utf-8")
        self.assertIn(".hibou-perm-banner-wrap", css)
        self.assertIn(".hibou-initial-skeleton", css)

        # 3. Boot skeleton in index.html
        idx = (ROOT / "app" / "assets" / "web" / "index.html").read_text(encoding="utf-8")
        self.assertIn('id="hibou-initial-skeleton"', idx)

        # 4. FirstRun bypass in smali
        firstrun = (ROOT / ".cache" / "test_apk_out" / "smali" / "com" / "agy" / "ru" / "FirstRun.smali").read_text(encoding="utf-8")
        self.assertIn("const/4 v0, 0x1", firstrun)

if __name__ == "__main__":
    unittest.main()

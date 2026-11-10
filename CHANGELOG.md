# Hibou Changelog

[Home](README.md) · [All Releases](https://github.com/ovrrup/Hibou/releases) · [Development Archive](docs/CHANGELOG-ARCHIVE.md)

Changes are grouped by release in reverse-chronological order.

## [1.0.0-beta.1] — Initial Hibou Beta Release (2026-11-10)

Initial beta release of Hibou, an AI-powered development environment designed for Android ARM64 userspace.

### Highlights & Architecture
- **Official Versioning:** Reset project versioning to start from `1.0.0-beta.1` for the first unified distribution of the application.
- **Project Identity:** Rebranded to Hibou with owl mascot vector identity (`01_owl`), copper styling, and standalone repository at `ovrrup/Hibou`.
- **Standalone Application ID (`agy.hibou`):** Transitioned application ID and package namespace from legacy `com.agyru.t2` to `agy.hibou`, updating compiled binary manifests, primary DEX bytecode, internal IPC broadcasts, Shizuku `rish` authorization, and all terminal shims.
- **English by Default:** Interface defaults to English across first-run onboarding, settings, and developer tool managers. Full Russian localization is preserved and accessible via *Settings → Interface → App language*.
- **Foundational Credits:** Explicit in-app and documentation attribution to upstream creators:
  - **werdio325-png** ([werdio325-png/antigravity-android](https://github.com/werdio325-png/antigravity-android)): Pioneer of native Android ARM64 execution, Chromium WebView bridge, and Shizuku/IntentBridge integration.
  - **Leostrange** ([Leostrange/Leostryx](https://github.com/Leostrange/Leostryx)): Creator of custom model provider support (OpenAI, Claude, Gemini), MCP server tool forwarding, Security Shield policies, and developer toolchain management.
- **Default Workspaces:** Standardized on `Hibou Projects` and `Hibou Backup` in user storage.
- **Built-in Updater:** Integrated update engine with support for semantic prerelease versions (`1.0.0-beta.1`), SHA-256 validation, and variant preservation (Bypass updates Bypass, Standard updates Standard).
- **Tooling Support:** Python 3, OpenJDK 17/21, Node.js LTS, and Flutter/Dart toolchains configured for Bionic libc.

---

## Predecessor Lineage (Legacy 3.0.x Iterations)

*(Prior to the 1.0.0-beta.1 release of Hibou, experimental builds followed the legacy 3.0.x numbering scheme inherited from upstream development:)*

### 3.0.5 — Updater & Integrity
- Built-in update checking, downloading, and APK installation.
- Native bridge package verification.

### 3.0.4 — Provider Categorization & Unified Paths
- Chat model selector grouped by provider headers (OpenAI, Claude, Gemini).
- Harmonized backup folder paths.

### 3.0.3 — Visual Branding & Pillow
- Introduced selectable branding packs (Owl, Lynx, LSX, Angular X).
- Included Pillow (PIL) in the runtime environment.

### 3.0.1–3.0.2 — Tools Console & Backup System
- In-app terminal command console and error history tracking.
- ZIP application backup/restore for keys, settings, and conversations.
- PyMuPDF-powered session PDF export.

### 3.0 Baseline Port
- Core Antigravity 2.4.1 port to Android userspace ARM64/Bionic by `werdio325-png` and `Leostrange`.

# Hibou Changelog

[Home](README.md) · [All Releases](https://github.com/ovrrup/Hibou/releases) · [Development Archive](docs/CHANGELOG-ARCHIVE.md)

Changes are grouped by release in reverse-chronological order. Technical details and intermediate rebuild logs are preserved in the development archive.

## Rebranding to Hibou & English Default — 2026-11-10

- **Rebranded to Hibou** (Repository: `ovrrup/Hibou`).
- **English Default:** English is now configured as the default application language across first-run onboarding, settings, and runtime tools; Russian remains fully selectable in Settings → Interface.
- **Visual Identity:** Updated vector banner and branding assets with the stylized Hibou owl mascot (`01_owl`).
- **Default Paths:** Unified workspace and backup paths to **Hibou Projects** and **Hibou Backup**.
- **Updater:** Redirected built-in update client to `ovrrup/Hibou`, with User-Agent set to `Hibou-Updater`.
- **UI & Credits:** Updated About screen with Hibou identity, version inspection, and source repository links while preserving attribution to upstream authors.
- **Build Pipeline:** `rebuild.py` produces `Hibou-<version>-<variant>.apk` binaries and supports `HIBOU_*` signing environment variables.

## Repaired Releases: 3.0.3, 3.0.4, and 3.0.5 — 2026-11-10

Reassembled six release APKs (Standard and Bypass across all three versions) preserving package ID `com.agyru.t2`, original release signature `d72235a5…`, and versionCodes 19, 20, and 21. Repaired APKs install directly over previous versions.

- Cleanly reassembled `classes.dex`, eliminating the `Bad checksum` startup crash and restoring `AppFileProvider` initialization.
- Fixed web UI syntax errors in tools settings; version info is now dynamically queried from Android `PackageManager`.
- In 3.0.4: added custom provider header groupings and consistent backup paths.
- In 3.0.5: implemented integrated updater with JSON protocol, version comparison, variant preservation, download integrity checks (size, SHA-256, ZIP, DEX), and native installer handoff.
- Preserved previous damaged binaries in GitHub Releases under `.before-repair` filenames; active release artifacts and `SHA256SUMS.txt` reflect the repaired builds.

## [3.0.5](https://github.com/ovrrup/Hibou/releases/tag/v3.0.5)

- Includes all improvements from 3.0.4.
- Update checking queries `ovrrup/Hibou`.
- Version and variant are dynamically detected via native bridge instead of hardcoded strings.
- JSON protocol handles release tag spaces cleanly; version comparison uses numeric semantic versioning.
- Bypass builds update to Bypass, Standard builds update to Standard.
- Strict download validation: size, SHA-256, ZIP structure, and DEX headers.
- Native bridge verifies package ID, certificate, versionCode, and variant before launching Android's system package installer.

## [3.0.4](https://github.com/ovrrup/Hibou/releases/tag/v3.0.4) — 2026-11-10

### Changed
- Grouped models in the chat model selector under provider headers: OpenAI-compatible, Anthropic Claude, and Gemini-compatible.
- Renamed default workspaces and backup folders to **Hibou Projects** and **Hibou Backup**.

### Fixed
- Fixed display of custom provider models in the chat selector.

### Builds
- Rebuilt Standard and Bypass APKs with updated `SHA256SUMS.txt`.

## [3.0.3](https://github.com/ovrrup/Hibou/releases/tag/v3.0.3) — 2026-11-10

### Added
- Four branding packs: **Owl**, **Lynx**, **LSX**, and **Angular X**, selectable in appearance settings.
- Pillow (PIL) included in the runtime environment.
- Vector SVG emblem assets.

### Changed
- Project rebranded with refreshed application name, launcher icons, and branding strings in Standard and Bypass.

## [3.0.2](https://github.com/ovrrup/Hibou/releases/tag/v3.0.2)

### Added
- Dedicated **Backup & Restore** section in settings.
- ZIP export and import with selective categories: providers and API keys, MCP keys, user settings, chat history, and media attachments.
- External storage folder selection to preserve backups across app reinstallation.

### Fixed
- Resolved `runtime integrity check failed: hash mismatch` error.
- Fixed layout stretching in conversation sort menu.

## [3.0.1](https://github.com/ovrrup/Hibou/releases/tag/v3.0.1) — 2026-11-09

### Added
- In-app console in Tools settings: separate command execution, log output copy/clear, and fast `logcat` capture.
- Error history viewer with copy and clear actions.
- Session export (text, images, and PyMuPDF-based PDF rendering).
- Package count and download size preview prior to tool updates.

### Fixed
- Fixed `ENXIO/ENOENT` terminal I/O errors when launching commands from tools.
- Suppressed false positive error flags triggered by the word "error" in successful command output.
- Fixed scroll jumping and background scroll lock in modal dialogs.
- Connection watchdog no longer interrupts background tasks prematurely.

## 3.0 — Baseline Enhancements

Consolidated features built upon Antigravity 2.4.1:

### Interface & Chat
- Multilingual interface (English and Russian) with instant switching in settings and on first run.
- Compact dialogs and controls optimized for touch screens, with full light and dark theme support.
- Up to 10 media attachments per turn with inline preview and removal.
- Voice dictation settings, on-device speech recognition for custom providers, and thinking/reasoning modes.
- Vision support for custom models and persistent model selection across app backgrounding.

### Agent & Governance
- Security Shield for managing file access, network read/write rules, and shell execution policies.
- MCP server runtime adapted for Android/Bionic, with tool forwarding to custom providers.
- MCP server catalog with configuration UI and custom server definitions.
- UTCP / OpenAPI integration for external API actuation.
- Skill browser, editor, and creator; system instructions and UI plugins.

### Environment & Tooling
- Integrated package management and verification from settings.
- Flutter & Dart SDKs adapted for Android userspace (Bionic).
- JVM environment fixes, `jadx-agy` wrapper, and environment path restoration.
- Download retries, operation cancellation, and configurable timeouts.
- Automatic dependency resolution protecting installed Node.js LTS packages.

<p align="center">
  <img src="assets/hibou-banner.svg" alt="Hibou — AI-Powered Development Environment for Android" width="100%" />
</p>

<p align="center">
  <a href="https://github.com/ovrrup/Hibou/releases/latest"><img src="https://img.shields.io/github/v/release/ovrrup/Hibou?style=flat-square&color=D58151&label=release" alt="Latest release" /></a>
  <img src="https://img.shields.io/badge/Android-arm64--v8a-373438?style=flat-square&logo=android&logoColor=F0DFCC" alt="Android · arm64-v8a" />
  <img src="https://img.shields.io/badge/Interface-EN%20%2F%20RU-373438?style=flat-square" alt="English & Russian UI" />
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-Apache%202.0-373438?style=flat-square" alt="Apache 2.0" /></a>
</p>

<p align="center"><b>An AI-powered development environment directly on Android.</b><br />
Custom model providers, developer toolchain, and granular agent control.</p>

<p align="center">
  <a href="https://github.com/ovrrup/Hibou/releases/latest"><b>Download APK</b></a> ·
  <a href="docs/USAGE.md">Setup & Usage</a> ·
  <a href="CHANGELOG.md">Changelog</a> ·
  <a href="docs/BUILD.md">Build</a> ·
  <a href="https://github.com/ovrrup/Hibou/issues">Report an Issue</a>
</p>

## About the Project

**Hibou** is an enhanced build of [Antigravity for Android](https://github.com/werdio325-png/antigravity-android) featuring an improved user interface, custom model provider configuration, and built-in development tooling management. The autonomous AI agent can read and edit project files, execute terminal commands, and trigger builds directly on your device. The runtime operates natively in Android userspace on ARM64/Bionic without PRoot, QEMU, or chroot overhead.

## Features

| Category | Available Features |
|---|---|
| **Chat & Models** | Standard and custom providers (OpenAI, Claude, Gemini-compatible), model grouping, reasoning/thinking modes, up to 10 file attachments |
| **Agent Governance** | Security Shield: file and network access rules, terminal command auto-execution policies, browser JavaScript execution permissions |
| **Developer Tooling** | In-app component installer, Android SDK/NDK, OpenJDK, Node.js LTS, Python 3, and Flutter/Dart toolchains |
| **Integrations** | Model Context Protocol (MCP) servers, UTCP/OpenAPI, agent skills, system rules, and UI plugins |
| **Diagnostics & Data** | In-app console and error history in Tools settings, logcat extraction, session export (text, images, PDF), and comprehensive backup/restore |
| **UI & Customization** | English and Russian interface with immediate switching, light and dark themes, and four branding packs: Owl (Hibou mascot), Lynx, LSX, and Angular X |

## Screenshots

<table>
  <tr>
    <td align="center"><img src="assets/screenshots/home.jpg" alt="Home Screen" width="230" /><br /><sub>Home Screen</sub></td>
    <td align="center"><img src="assets/screenshots/models.jpg" alt="Model Selector" width="230" /><br /><sub>Model Selector</sub></td>
    <td align="center"><img src="assets/screenshots/shield.jpg" alt="Security Shield" width="230" /><br /><sub>Security Shield</sub></td>
  </tr>
</table>

<details>
<summary>Themes and Dictation</summary>

<table>
  <tr>
    <td align="center"><img src="assets/screenshots/light-theme.jpg" alt="Light Theme" width="230" /><br /><sub>Light Theme</sub></td>
    <td align="center"><img src="assets/screenshots/dark-theme.jpg" alt="Dark Theme" width="230" /><br /><sub>Dark Theme</sub></td>
    <td align="center"><img src="assets/screenshots/dictation.jpg" alt="Voice Dictation" width="230" /><br /><sub>Voice Dictation</sub></td>
  </tr>
</table>

</details>

## Download & Install · Repaired Releases

| Variant · 3.0.5 | Description | Download |
|---|---|---|
| **Standard** | Base build without core regional check patches | [Standard APK](https://github.com/ovrrup/Hibou/releases/download/v3.0.5/Hibou-3.0.5-standard.apk) |
| **Bypass** | Build patched to bypass core regional restrictions | [Bypass APK](https://github.com/ovrrup/Hibou/releases/download/v3.0.5/Hibou-3.0.5-bypass.apk) |

1. Download your preferred APK from [Releases](https://github.com/ovrrup/Hibou/releases/latest).
2. Allow installation from unknown sources in Android settings and install the APK.
3. On first run, complete the initial setup (permissions and language), then sign in or configure your custom model provider.

The repaired APKs are signed with the original release certificate used in versions 3.0.3–3.0.5. Always install the same or newer version; Android prevents downgrading `versionCode`. If you had previously downloaded an older, corrupted APK file, re-download the repaired package.

## Release Differences

All six APKs across versions 3.0.3–3.0.5 have been rebuilt with the original signature. The DEX startup crash has been resolved; previous corrupted packages are preserved as `.before-repair` files and should not be installed.

| Release | versionCode | Highlights |
|---|---|---|
| [3.0.3](https://github.com/ovrrup/Hibou/releases/tag/v3.0.3) | 19 | Branding refresh, 4 emblem packs, fixed startup, and tools settings tab |
| [3.0.4](https://github.com/ovrrup/Hibou/releases/tag/v3.0.4) | 20 | Added provider headers in model selector and consistent backup paths |
| [3.0.5](https://github.com/ovrrup/Hibou/releases/tag/v3.0.5) | 21 | Added built-in update checking, downloading, and installation while preserving the build variant |

For upgrading over an existing 3.0.3, 3.0.4, or 3.0.5 installation, install the repaired **3.0.5**. Uninstalling the app is not required because the signing certificate matches.

[Repair Report](docs/REPAIRED-RELEASES.md) · [Full Changelog](CHANGELOG.md).

## Important Considerations

- Standard and custom providers operate under different core modes; refer to the [Setup Guide](docs/USAGE.md) for configuration instructions.
- Voice dictation with custom providers leverages Android's on-device SpeechRecognizer.
- Model, regional, and external API availability depends on the provider. The Bypass variant modifies core regional checks but does not guarantee third-party API accessibility.
- The tools catalog lists available development packages; packages are downloaded and installed on demand via settings.

## Documentation

| Document | Content |
|---|---|
| [Setup & Diagnostics](docs/USAGE.md) | Installation, providers, toolchain, MCP, error logging, and backup management |
| [Building Hibou](docs/BUILD.md) | APK modification workflow, dependencies, and reproducibility notes |
| [Changelog](CHANGELOG.md) | Release history and version highlights |
| [Development Archive](docs/CHANGELOG-ARCHIVE.md) | Complete 65-point development log with technical details |
| [Flutter/Dart](docs/flutter-toolchain.md) · [JVM & jadx](docs/jadx-fixes.md) | Specialized setup and troubleshooting guides |

## Credits & License

Built upon [werdio325-png/antigravity-android](https://github.com/werdio325-png/antigravity-android). Previous contributions by [Leostryx (@Leostrange)](https://github.com/Leostrange/Leostryx). Hibou development and maintenance by [@ovrrup](https://github.com/ovrrup).

Licensed under [Apache 2.0](LICENSE). Third-party dependencies and components are distributed under their respective licenses.

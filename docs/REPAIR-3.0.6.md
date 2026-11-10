# Repairing Releases 3.0.3–3.0.5

[Home](../README.md) · [Building](BUILD.md) · [Changelog](../CHANGELOG.md)

## Root Cause Analysis

An investigation of six published APKs (Standard and Bypass across versions 3.0.3, 3.0.4, and 3.0.5) revealed that within each variant, all DEX files, web assets, and runtime environment bundles were identical across version tags. Only the manifest version attributes and package signatures had been modified between releases.

In the primary DEX (`classes.dex`), six strings originally reading `Antigravity` had been replaced with `Leostryx` via raw byte editing without updating the SHA-1 and Adler-32 checksums in the DEX header. This caused Android's Dalvik/ART verifier to reject the file at boot time:

```text
Bad checksum (d58dfb03, expected 25d3ffb3)
Unable to get provider com.agy.util.AppFileProvider
```

While `AppFileProvider` was present in the DEX byte stream, Android rejected the entire DEX container due to header checksum validation failure. Reverting the six strings back to the original brand restored the exact legacy checksums, confirming the corruption mechanism byte-for-byte.

## Fixes Implemented

- **DEX Recompilation:** The primary DEX has been cleanly reassembled from smali source with valid header checksums, ensuring `AppFileProvider` initializes correctly.
- **Web UI Syntax:** Resolved syntax errors in the tools settings interface.
- **Provider Headers:** Added dynamic headers for configured custom model providers in the chat model selector.
- **Backup Paths:** Unified backup paths across UI, file pickers, and shell backends.
- **About Dialog:** Application version and variant are now dynamically queried from Android's `PackageManager`; repository and documentation links point to Hibou.
- **Built-in Updater:** Implemented a robust updater utilizing JSON communication, numeric semver comparison, variant preservation (Bypass updates Bypass, Standard updates Standard), and verified HTTPS downloads. Downloads are checked against content size, SHA-256, ZIP structure, and valid DEX headers before invocation.
- **Native Installer Bridge:** Validates package ID, certificate, versionCode, and variant before triggering Android's native package installer intent.

## Verification

- 14 update backend unit tests and 5 UI integration tests pass.
- Verified scenarios: identical/older/newer versions, release tags containing spaces, Bypass variant handling, missing APK/hash errors, interrupted downloads, SHA-256 mismatches, malformed DEX rejection, absence of duplicate UI elements, and model selector groupings across multiple providers.
- Both rebuilt APKs specify `versionName 3.0.6` and `versionCode 22`. DEX integrity, `AppFileProvider` presence, the native update bridge, manifest permissions, variant consistency, and v2/v3 signatures have been statically verified.

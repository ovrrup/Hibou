# Building Hibou 1.0.0-beta.1

[Home](../README.md) · [Repair Notes](REPAIR-3.0.6.md)

## Input Files

This repository contains working files for APK modification, repaired DEX binaries, and native updater source code. It is not the full Java/Kotlin/React project source for all application components.

`rebuild.py` uses base **3.0.5** APKs, validating their pinned SHA-256 checksums, and overlays the modified files onto them. This preserves visual resources, four emblem branding packs, native libraries, and the selected core variant even if large assets are not tracked in Git.

The damaged `classes.dex` in the base APK is replaced by the rebuilt, verified DEX. The base APK should not be installed directly on a device without these patches.

| File | Purpose |
|---|---|
| `app/AndroidManifest.xml` | Compiled manifest with agy.hibou application ID, versionCode 1, and package installation permissions |
| `app/resources.arsc` | Compiled resource table with Hibou application label and agy.hibou package identity |
| `app/classes.dex` | Rebuilt primary DEX with agy.hibou IPC shims, fixed bridge and valid checksums |
| `app/classes4.dex` | Native metadata and system installer invocation bridge |
| `app/assets/web/agy_tools.js`, `agy_tools.sh` | Tools settings UI and updater backend module |
| `app/assets/web/agy_providers.js` | Configured model provider groupings |
| `tools/patch_web_bundle.py` | Verified patch for model group rendering and Hibou branding in web bundle |
| `tools/update_backend.py` | Source of the update logic, embedded into the shell script |
| `native-src/` | New Java bridge sources, modified AgyTools smali, and raw manifest XML |

Remaining files from the old `app/` tree are not treated as implicit overrides. The list of files to overlay is explicitly defined in `OVERRIDES` to avoid cross-build contamination.

## Prerequisites

- Python 3.9+
- Bash & standard POSIX utilities
- Java Runtime Environment (JRE)
- `tools/uber-apk-signer.jar` (included in repository)
- Default signing keystore: `dist/hibou-debug.keystore` (alias: `debug`)

## Build Commands

Build both APK variants with automatic base fetching:

```bash
python3 rebuild.py both --fetch-base
python3 rebuild.py standard --fetch-base
python3 rebuild.py bypass --fetch-base
```

For building from pre-downloaded base APKs:

```bash
python3 rebuild.py both \
  --standard-apk /path/to/Hibou-3.0.5-standard.apk \
  --bypass-apk /path/to/Hibou-3.0.5-bypass.apk
```

Outputs are written to `dist/signed/Hibou-1.0.0-beta.1-standard.apk`, `Hibou-1.0.0-beta.1-bypass.apk`, and `SHA256SUMS.txt`. Fetched base APKs are cached in `.cache/rebuild/`.

The build script verifies exact base APK SHA-256 checksums, modified JS and shell syntax, DEX header integrity, ZIP structure, and runtime hashes before and after signing. Alignment and signatures are validated via `uber-apk-signer`.

## Signing & Migration

Repaired files use the project debug certificate (`7360d9349c7d1e9af564d32c4d0392fcb4effe211567c8f3dadec640efbcfa53`). The official published 3.0.3–3.0.5 APKs are signed with the author's release certificate: `d72235a57bb27fe9755199e8b9daca1ae305adf8a0f0feac3ac98434f6f7e416`.

To install a custom build over an existing installation without data loss, the original signing key must be used. Otherwise, backup your data and reinstall the application. Raising `versionCode` alone does not bypass signature verification.

To specify a custom keystore, configure the environment variables:
`HIBOU_KEYSTORE` (or `LEOSTRYX_KEYSTORE`), `HIBOU_KEY_ALIAS`, `HIBOU_STORE_PASSWORD`, and `HIBOU_KEY_PASSWORD`.

## Development & Testing

Run unit tests:

```bash
python3 -m unittest discover -s tests -v
```

When modifying `tools/update_backend.py`, its contents must remain byte-for-byte identical to the embedded `HIBOU_UPDATE_PY` block in `app/assets/web/agy_tools.sh`.

The native Java bridge resides in `native-src/com/agy/ru/LeostryxUpdates.java`; it compiles against the Android API and is translated by D8 into `classes4.dex`. The main DEX is reassembled from smali rather than modified by raw byte replacement. `native-src/smali/com/agy/ru/AgyTools.smali` contains the added `getAppInfo()` and `installUpdate()` JavaScript interfaces.

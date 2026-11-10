# Repaired Releases: 3.0.3, 3.0.4, and 3.0.5

[Home](../README.md) · [Repair Analysis](REPAIR-3.0.6.md)

## Background

In the original six release APKs across versions 3.0.3, 3.0.4, and 3.0.5, replacing strings in `classes.dex` without recalculating SHA-1 and Adler-32 header checksums caused Android to crash at startup with: `Bad checksum (d58dfb03, expected 25d3ffb3)`. While `AppFileProvider` was compiled into the DEX, Android rejected the entire binary.

The APK containers and signatures were structurally valid; corruption occurred during the build process rather than during download.

## Repaired Packages

| Version | versionCode | Key Functional Additions |
|---|---|---|
| 3.0.3 | 19 | Fixed base launch, initial tools tab, and visual branding |
| 3.0.4 | 20 | Added provider grouping in model selector and unified backup paths |
| 3.0.5 | 21 | Added integrated updater: checking, downloading, and system installer handoff |

Standard and Bypass APKs were reassembled for each version. Original corrupted packages were renamed with the `.before-repair` extension to preserve history, while release downloads and `SHA256SUMS.txt` reflect the verified packages.

## Installation Compatibility

The author's signing key matches the original release signature:

```text
d72235a57bb27fe9755199e8b9daca1ae305adf8a0f0feac3ac98434f6f7e416
```

In the legacy 3.0.3–3.0.5 releases, the package ID was `com.agyru.t2`. Starting with Hibou `1.0.0-beta.1`, the application transitions to its standalone identity with Application ID `agy.hibou`. Because the package ID changes to `agy.hibou`, users upgrading from legacy versions can install Hibou cleanly alongside or migrate via export/import without package signature conflicts.

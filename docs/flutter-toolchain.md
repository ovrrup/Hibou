# Flutter / Dart Toolchain on Antigravity Android

> [!IMPORTANT]
> This is the authoritative reference for setting up and fixing the Flutter toolchain inside the Antigravity Android sandbox.
> All commands run directly in the Antigravity userspace terminal. Fixes are idempotent.

> [!NOTE]
> **Fully Automated:** The `agy-fix-flutter` script (provided in the environment overlay on `$PATH`) performs steps 1–7 automatically. Run `agy-fix-flutter` to apply all fixes. The manual steps below explain the underlying mechanics for debugging.

---

## Quick Diagnostics: Symptom → Cause → Fix

| Terminal Symptom | Cause | Resolution |
|---|---|---|
| `dart: cannot execute: required file not found` (running `flutter --version`) | Flutter downloaded a **glibc** build of Dart, but Android lacks `/lib/ld-linux-aarch64.so.1` / `libdl.so.2` (uses bionic libc instead) | [1. Bionic Dart SDK Swap](#1-pkg-install-dart--swap-cached-dart-sdk) |
| `Unsupported host platform: android_arm64` | `flutter_tools` does not recognize the `androidArm64` ABI in `hostPlatform` | [2. Patch os.dart](#2-patch-flutter_toolsosdart) |
| `Unsupported operating system: android` | `flutter_cache.dart` (Font Subsets) checks `operatingSystem` against `{macos, linux, windows}` and fails | [3. Patch platform.dart](#3-patch-flutter_toolsplatformdart) |
| Same `Unsupported operating system` error from `package:platform` | Dependency package in `.pub-cache` lacks Android host recognition | [4. Patch local_platform.dart](#4-patch-packageplatform-pub-cache) |
| Patches have no effect after edits | Flutter is running from cached `flutter_tools.snapshot` | [5. Rebuild Snapshot](#5-rebuild-flutter_toolssnapshot) |
| `Could not initialize native services` / `Failed to load native library 'libnative-platform.so'` + `Permission denied` | JVM default `user.home` points to inaccessible path `/data/data/com.termux/...` | [6. GRADLE_OPTS=-Duser.home](#6-gradle--duserhome) |
| `Caused by: net.rubygrapefruit.platform.NativeException ... broken executable` | Corrupted `libnative-platform.so` in `~/.gradle/native/` | [7. Clear Gradle Native Cache](#7-clear-gradle-native-cache) |

---

## Installed Components

| Component | Version | Installation | Path |
|---|---|---|---|
| Dart SDK (bionic, Android) | 3.13.7 | `pkg update && pkg install dart` (~92 MB) | `$PREFIX/lib/dart-sdk` (`$PREFIX/bin/dart`) |
| Flutter SDK | 3.47.9 (stable) | Installed | `$HOME/flutter_sdk` |
| OpenJDK | 17 LTS | Installed | `java`, `javac` in `$PREFIX/bin` |
| Android SDK | Platforms 34–37, build-tools 34–37 | Installed | `$HOME/android-sdk` |
| Gradle | 9.x | Installed | `$PREFIX/opt/gradle` |

---

## 1. `pkg install dart` + Swap Cached `dart-sdk`

**Why:** Flutter downloads an official Dart SDK targeting Linux glibc (`/lib/ld-linux-aarch64.so.1`). Android's Bionic environment cannot execute this binary. The solution is swapping it with the native bionic Dart build matching Flutter's expected version.

```bash
# 1) Install Dart SDK for Android (bionic)
pkg update
pkg install dart

# 2) Verify versions match
dart --version
cat $HOME/flutter_sdk/bin/cache/dart-sdk/version

# 3) Swap glibc build with bionic build in Flutter cache
cd $HOME/flutter_sdk/bin/cache
mv dart-sdk dart-sdk.glibc
ln -s $PREFIX/lib/dart-sdk dart-sdk

# 4) Verify
dart-sdk/bin/dart --version
```

---

## 2. Patch `flutter_tools/base/os.dart`

**File:** `$HOME/flutter_sdk/packages/flutter_tools/lib/src/base/os.dart`

In the `hostPlatform` getter (inside `switch (_currentAbi)`), add Android ABI cases before the default:

```dart
      Abi.windowsArm64 => HostPlatform.windows_arm64,
      // Antigravity (Android userspace) patch: treat Android host as Linux.
      Abi.androidArm64 => HostPlatform.linux_arm64,
      Abi.androidArm => HostPlatform.linux_arm64,
      Abi.androidX64 => HostPlatform.linux_x64,
      _ => throw UnsupportedError('Unsupported host platform: $_currentAbi'),
```

---

## 3. Patch `flutter_tools/base/platform.dart`

**File:** `$HOME/flutter_sdk/packages/flutter_tools/lib/src/base/platform.dart`

In class `LocalPlatform`, replace the `operatingSystem` getter:

```dart
  @override
  String get operatingSystem => io.Platform.operatingSystem == 'android'
      ? 'linux'
      : io.Platform.operatingSystem;
```

---

## 4. Patch `package:platform` (pub-cache)

**File:** `$HOME/.pub-cache/hosted/pub.dev/platform-*/lib/src/interface/local_platform.dart`

```dart
  @override
  String get operatingSystem =>
      io.Platform.operatingSystem == 'android' && !_runningOnDevice
          ? 'linux'
          : io.Platform.operatingSystem;

  static final bool _runningOnDevice =
      io.Platform.environment.containsKey('FLUTTER_ON_DEVICE');
```

---

## 5. Rebuild `flutter_tools.snapshot`

```bash
rm -f $HOME/flutter_sdk/bin/cache/flutter_tools.snapshot \
      $HOME/flutter_sdk/bin/cache/flutter_tools.stamp

export GRADLE_OPTS="-Duser.home=$HOME"
flutter --version
```

---

## 6. Gradle: `-Duser.home`

Add to `~/.gradle/gradle.properties`:

```properties
org.gradle.jvmargs=-Xmx1536m -Djava.io.tmpdir=$PREFIX/tmp -Duser.home=$HOME
```

---

## 7. Clear Gradle Native Cache

```bash
rm -rf ~/.gradle/native
```

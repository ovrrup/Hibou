#!/data/data/com.termux/files/usr/bin/bash
# SPDX-FileCopyrightText: 2026 ovrrup <ofeducationc@gmail.com>
# SPDX-License-Identifier: Apache-2.0
# Build Kestrel — the fully redesigned standalone app (new identity, native
# Kotlin UI, engine contracts unchanged).
#
#   ./build.sh            build reconstruct/dist/Kestrel-$VERSION.apk
#   ./build.sh --no-sign  stop after the aligned-but-unsigned APK
#
# Program logic (every classes*.dex) is compiled from ./src (Kotlin).
# resources.arsc, res/, assets/ and lib/ are reused verbatim from ../app as the
# app's data payload so resource IDs stay stable; the identity rebrand
# (package, label, launcher colors) is applied to the staged copies only.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$ROOT/.." && pwd)"
WORK="$ROOT/out"
DIST="$ROOT/dist"
STAGE="$WORK/stage"

JVM="${JAVA_HOME:-/data/data/com.termux/files/usr/lib/jvm/java-21-openjdk}"
export JAVA_HOME="$JVM"
export PATH="$JVM/bin:$PATH"

SDK_JAR="${ANDROID_JAR:-$WORK/android.jar}"
MIN_API=24
VERSION=1.0.0-beta.1
LAUNCHER="${KESTREL_LAUNCHER:-com.kestrel.ui.KestrelActivity}"
KEYSTORE="${KESTREL_KEYSTORE:-$REPO/dist/kestrel-debug.keystore}"
GRAPHITE='#FF14171C'

for tool in kotlinc d8 aapt zipalign apksigner java; do
  command -v "$tool" >/dev/null 2>&1 || { echo "missing tool: $tool (pkg install ...)" >&2; exit 1; }
done

# --- android.jar (API 33, matches the payload's compileSdkVersion) ----------
if [ ! -f "$SDK_JAR" ]; then
  mkdir -p "$WORK"
  echo ">> fetching android.jar (API 33)"
  curl -fSL --retry 4 --retry-delay 3 --retry-all-errors -o "$SDK_JAR" \
    https://raw.githubusercontent.com/Sable/android-platforms/master/android-33/android.jar \
    || { echo "android.jar download failed (network)" >&2; exit 1; }
  unzip -l "$SDK_JAR" >/dev/null 2>&1 || { echo "android.jar download invalid" >&2; exit 1; }
fi

KOTLIN_STDLIB="${KOTLIN_STDLIB:-/data/data/com.termux/files/usr/opt/kotlin/lib/kotlin-stdlib.jar}"
if [ ! -f "$KOTLIN_STDLIB" ]; then
  # guarded: outside Termux this find finds nothing and must not kill the script
  KOTLIN_STDLIB="$( { find /data/data/com.termux/files/usr -maxdepth 5 -name kotlin-stdlib.jar 2>/dev/null || true; } | head -n 1 )"
fi
[ -n "$KOTLIN_STDLIB" ] && [ -f "$KOTLIN_STDLIB" ] || { echo "kotlin-stdlib.jar not found (set KOTLIN_STDLIB)" >&2; exit 1; }

rm -rf "$WORK/classes" "$WORK/dex" "$STAGE"
mkdir -p "$WORK/classes" "$WORK/dex" "$STAGE" "$DIST"

# --- 1. Kotlin -> JVM classes ------------------------------------------------
mapfile -t SRCS < <(find "$ROOT/src" \( -name '*.kt' -o -name '*.java' \) | sort)
[ ${#SRCS[@]} -gt 0 ] || { echo "no sources under $ROOT/src" >&2; exit 1; }
echo ">> compiling ${#SRCS[@]} source files"
kotlinc "${SRCS[@]}" -cp "$SDK_JAR:$KOTLIN_STDLIB" -jvm-target 1.8 -d "$WORK/classes" 2>&1 |
  grep -v "Failed to load native library:jansi\|UnsatisfiedLinkError" || true

# --- 2. desugar + dex (stdlib merged in so no separate kotlin DEX is needed) --
find "$WORK/classes" -name '*.class' > "$WORK/classlist.txt"
echo ">> dexing $(wc -l < "$WORK/classlist.txt") classes"
( cd "$WORK/classes" && jar cf "$WORK/classes.jar" . )
d8 --release --min-api "$MIN_API" \
   --lib "$SDK_JAR" --lib "$KOTLIN_STDLIB" \
   --output "$WORK/dex" \
   "$WORK/classes.jar" 2>&1 | grep -vE "^(IV:|Stripped|Buckets|Headers)" || true
[ -f "$WORK/dex/classes.dex" ] || { echo "d8 produced no classes.dex" >&2; exit 1; }

# --- 3. stage payload --------------------------------------------------------
cp "$REPO/app/resources.arsc" "$STAGE/"
cp -r "$REPO/app/res" "$STAGE/res"
cp -r "$REPO/app/assets" "$STAGE/assets"
cp -r "$REPO/app/lib" "$STAGE/lib"
cp "$WORK/dex/classes.dex" "$STAGE/classes.dex"

# icons: regenerate the Kestrel mark into the staged res tree (app/ stays
# pristine; content geometry matches the original payload art)
python3 "$ROOT/art/make_icons.py" >/dev/null

# identity rebrand (staged copies only; app/ stays pristine)
python3 "$REPO/tools/arsc_strings.py" \
  --input "$STAGE/resources.arsc" --map Hibou=Kestrel >/dev/null
for color in window_background ic_launcher_background \
             ic_launcher_background_dark ic_launcher_background_light \
             splash_bg_dark splash_bg_light; do
  python3 "$REPO/tools/arsc_color.py" \
    --input "$STAGE/resources.arsc" --name "$color" --set "$GRAPHITE" >/dev/null
done

# manifest: rebrand the namespace, then select the launcher
python3 "$REPO/tools/rebrand_manifest.py" \
  --input "$REPO/app/AndroidManifest.xml" \
  --output "$STAGE/AndroidManifest.xml" >/dev/null
python3 "$REPO/tools/patch_manifest.py" \
  --input "$STAGE/AndroidManifest.xml" \
  --output "$STAGE/AndroidManifest.xml" \
  --activity "$LAUNCHER" >/dev/null

echo ">> packaging"
python3 - "$STAGE" "$WORK/unsigned.apk" <<'PY'
import os, sys, zipfile
stage, out = sys.argv[1], sys.argv[2]
if os.path.exists(out):
    os.unlink(out)
with zipfile.ZipFile(out, "w") as z:
    # resources.arsc must be stored uncompressed and first (AAPT requirement).
    z.write(os.path.join(stage, "resources.arsc"), "resources.arsc",
            compress_type=zipfile.ZIP_STORED)
    for root, _, files in os.walk(stage):
        for f in sorted(files):
            p = os.path.join(root, f)
            name = os.path.relpath(p, stage)
            if name == "resources.arsc":
                continue
            z.write(p, name, compress_type=zipfile.ZIP_DEFLATED)
PY

echo ">> aligning"
zipalign -f -p 4 "$WORK/unsigned.apk" "$WORK/aligned.apk"

UNSIGNED="$WORK/aligned.apk"
if [ "${1:-}" = "--no-sign" ]; then
  cp "$UNSIGNED" "$DIST/Kestrel-$VERSION-unsigned.apk"
  echo ">> done (unsigned): $DIST/Kestrel-$VERSION-unsigned.apk"
  exit 0
fi

echo ">> signing"
apksigner sign \
  --ks "$KEYSTORE" --ks-key-alias "${KESTREL_KEY_ALIAS:-kestrel}" \
  --ks-pass pass:"${KESTREL_STORE_PASSWORD:-kestrel}" \
  --key-pass pass:"${KESTREL_KEY_PASSWORD:-kestrel}" \
  --out "$DIST/Kestrel-$VERSION.apk" "$UNSIGNED"

apksigner verify --print-certs "$DIST/Kestrel-$VERSION.apk" | head -3
echo ">> done: $DIST/Kestrel-$VERSION.apk"

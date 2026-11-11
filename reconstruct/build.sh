#!/data/data/com.termux/files/usr/bin/bash
# Rebuild Hibou as a standalone app: fresh Kotlin DEX + reused resource payload.
#
#   ./build.sh            build reconstruct/dist/Hibou-standalone.apk
#   ./build.sh --no-sign  stop after the aligned-but-unsigned APK
#
# Program logic (every classes*.dex) is compiled from ./src (Kotlin).
# resources.arsc, res/, assets/ and lib/ are reused verbatim from ../app as the
# app's data payload so resource IDs stay stable.  The binary manifest is
# patched in place to select the launcher activity (tools/patch_manifest.py).
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
LAUNCHER="${HIBOU_LAUNCHER:-com.agy.ui.MainActivity}"
KEYSTORE="${HIBOU_KEYSTORE:-$REPO/dist/hibou-debug.keystore}"

for tool in kotlinc d8 aapt zipalign apksigner java; do
  command -v "$tool" >/dev/null 2>&1 || { echo "missing tool: $tool (pkg install ...)" >&2; exit 1; }
done

# --- android.jar (API 33, matches the payload's compileSdkVersion) ----------
if [ ! -f "$SDK_JAR" ]; then
  mkdir -p "$WORK"
  echo ">> fetching android.jar (API 33)"
  curl -sL -o "$SDK_JAR" \
    https://raw.githubusercontent.com/Sable/android-platforms/master/android-33/android.jar
  unzip -l "$SDK_JAR" >/dev/null 2>&1 || { echo "android.jar download invalid" >&2; exit 1; }
fi

KOTLIN_STDLIB="${KOTLIN_STDLIB:-/data/data/com.termux/files/usr/opt/kotlin/lib/kotlin-stdlib.jar}"
[ -f "$KOTLIN_STDLIB" ] || KOTLIN_STDLIB="$(find /data/data/com.termux/files/usr -maxdepth 5 -name kotlin-stdlib.jar 2>/dev/null | head -1)"
[ -n "$KOTLIN_STDLIB" ] && [ -f "$KOTLIN_STDLIB" ] || { echo "kotlin-stdlib.jar not found" >&2; exit 1; }

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

# manifest: binary, patched in place to select the launcher
python3 "$REPO/tools/patch_manifest.py" \
  --input "$REPO/app/AndroidManifest.xml" \
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
  cp "$UNSIGNED" "$DIST/Hibou-$VERSION-standalone-unsigned.apk"
  echo ">> done (unsigned): $DIST/Hibou-$VERSION-standalone-unsigned.apk"
  exit 0
fi

echo ">> signing"
apksigner sign \
  --ks "$KEYSTORE" --ks-key-alias "${HIBOU_KEY_ALIAS:-debug}" \
  --ks-pass pass:"${HIBOU_STORE_PASSWORD:-android}" \
  --key-pass pass:"${HIBOU_KEY_PASSWORD:-android}" \
  --out "$DIST/Hibou-$VERSION-standalone.apk" "$UNSIGNED"

apksigner verify --print-certs "$DIST/Hibou-$VERSION-standalone.apk" | head -3
echo ">> done: $DIST/Hibou-$VERSION-standalone.apk"

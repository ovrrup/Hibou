# jadx Decompilation Fixes for Android Userspace

Reference target: `/storage/emulated/0/Download/xgesture-1.62.0-full.apk` (~70 MB).

## Root Cause

The AGY environment utilizes a Termux-built OpenJDK 17 package with hardcoded Termux filesystem paths:
- Binary RPATH: `/data/data/com.termux/files/usr/lib/jvm/java-17-openjdk/lib:...`
- `user.home` → `/data/data/com.termux/files/home`
- `java.io.tmpdir` → `/data/data/com.termux/files/usr/tmp`

These directories are inaccessible from the AGY sandbox (`$HOME`), triggering `java.nio.file.AccessDeniedException`.

---

## Errors & Solutions

### 1. `ExceptionInInitializerError: Failed to init common directories`

```text
java.nio.file.AccessDeniedException: /data/data/com.termux/files/home/.cache
  at jadx.commons.app.JadxCommonFiles$DirsLoader.loadEnvDir
```

**Cause:** jadx initializes cache inside `user.home/.cache`, but `user.home` targets the nonexistent Termux home.

**Fix:** Redirect `user.home` and `XDG_CACHE_HOME` to an accessible directory:

```bash
env HOME="$HOME" XDG_CACHE_HOME="$WORK/cache" \
  JAVA_TOOL_OPTIONS="-Duser.home=$HOME" jadx ...
```

### 2. `Failed to create temp root directory`

```text
java.nio.file.AccessDeniedException: /data/data/com.termux/files/usr/tmp/jadx-instance-...
  at jadx.core.utils.files.FileUtils.createTempRootDir
```

**Cause:** `java.io.tmpdir` is hardcoded to Termux paths. The JVM ignores the `$TMPDIR` environment variable for `java.io.tmpdir`, requiring an explicit JVM property flag.

**Fix:** `JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=$WORK/cache/tmp" jadx ...`

### 3. Exit code 137 (SIGKILL)

**Cause:** Out-of-memory (OOM) on large APKs (~70 MB) when running unbounded threads on devices with limited RAM.

**Fix:** Restrict heap and thread concurrency:

```bash
JAVA_TOOL_OPTIONS="... -Xmx1500m" jadx -j 2 ...
```

Also pass `--no-res` when resource decoding is unnecessary or handled by apktool.

### 4. `Unknown option: --single-class-thread`

This option does not exist in jadx 1.5.x. Use `-j N` to control parallelism instead.

### 5. apktool: `W: Could not decode file, replacing by NULL value: res/*.9.png`

**Cause:** Obfuscation tools break chunk structures in nine-patch PNGs.

**Fix:** Safe to ignore for smali and manifest inspection. Extract raw PNG assets directly if needed:

```bash
unzip -o app.apk "res/*.9.png" -d raw_res
```

---

## Universal jadx Invocation

```bash
WORK="$HOME/.gemini/antigravity-app/scratch/<job>"
mkdir -p "$WORK/cache/tmp"
env HOME="$HOME" XDG_CACHE_HOME="$WORK/cache" TMPDIR="$WORK/cache/tmp" \
  JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=$WORK/cache/tmp -Duser.home=$HOME -Xmx1500m" \
  jadx -d "$WORK/jadx_out" --no-res -j 2 /path/to/app.apk
```

## Permanent Wrapper: `jadx-agy`

The `jadx-agy` wrapper resides at `$PREFIX/bin/jadx-agy`:

```sh
#!/data/user/0/com.agyru.t2/files/usr/bin/bash
WORK="${XDG_CACHE_HOME:-$HOME/.cache}/jadx"
mkdir -p "$WORK/tmp"
export JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=$WORK/tmp -Duser.home=$HOME -Xmx1500m ${JAVA_TOOL_OPTIONS:-}"
exec jadx "$@"
```

Usage: `jadx-agy -d out --no-res -j 2 app.apk`.

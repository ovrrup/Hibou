# Porting conventions — Hibou standalone reconstruction (branch `reconstruct`)

Target: a **standalone Android app** that reimplements Hibou's program logic
entirely in Kotlin. Everything the shipped DEX did is rebuilt here; the
resource payload (resources.arsc, res/, assets/, lib/ — including the bundled
engine runtime and web UI) is reused verbatim as data.

## Ground rules

1. **1:1 port fidelity.** Each decompiled Java class maps to one Kotlin file at
   the same path (`com.agy.core.CoreServerService` → `src/com/agy/core/CoreServerService.kt`).
   Keep class/package/method names so behavior and debugging match the original.
   Exceptions are noted in the class KDoc.
2. **No framework.** Plain Kotlin + android.jar (API 33) + kotlin-stdlib only.
   No AndroidX, no coroutines, no third-party deps. Java classes used by the
   ported code that exist in the app payload (e.g. `org.json`) come from the
   runtime; `org.json` is available on-device.
3. **JVM target 1.8, min API 24.** No JVM-only APIs not in android.jar.
4. **Fail-closed.** Port behavior, not just syntax: preserve swallowed-error
   fallbacks, thread-safety (`volatile`, `synchronized`), and exact strings
   written to logs/JS/JSON. These are API contracts with the web layer.
5. **Kotlin idioms allowed, semantics fixed.** Use `object`, `@JvmStatic`,
   `val`/`var`, null-safety mapped faithfully from Java nullability. Keep
   `public` visibility semantics (Kotlin `public` is default).
6. **Comments.** Keep decompiled comments (`/* loaded from: classes.dex */`)
   out; instead write a KDoc header: purpose, original file, any port deltas.
7. **No R class usage.** Resource IDs referenced by code must go through
   `Resources.getIdentifier(name, type, packageName)` or existing
   `com.agy.util.*` helpers, because the payload arsc is reused binary.
8. **Static singletons** that Java had as `enum` or `static final` maps: keep
   the same externally observable values.

## Layout

```
reconstruct/
  build.sh          on-device build (kotlinc -> d8 -> payload -> sign)
  src/com/agy/...   Kotlin sources (this is the whole app)
  out/              build intermediates (gitignored)
  dist/             signed APK output
```

Subsystems (jadx decompile at
`/data/data/com.termux/files/usr/tmp/opencode/rea-work/jadx-out/sources/com/agy`):

| Subsystem | Role |
|---|---|
| `com.agy.install` | env bootstrap: tar extraction, prefix rewriting, pkg install |
| `com.agy.runtime` | runtime install/verification, network config, onboarding |
| `com.agy.core` | engine process: service, launcher, supervisor, ready probe |
| `com.agy.gateway` | local HTTP gateway (browser/clipboard/device/exec/notification/power) |
| `com.agy.shell` | Shizuku/rish execution bridge |
| `com.agy.oauth` | OAuth custom-tab flow and token store |
| `com.agy.bridge` | JS↔native `AndroidBridge` and friends |
| `com.agy.ui` | MainActivity, WebView host/splash/lifecycle, launchers |
| `com.agy.theme` | theme detection, painter, icon switcher |
| `com.agy.log`, `com.agy.net`, `com.agy.util` | logging, network monitor, helpers |
| `com.agy.ru` | Hibou patch layer (AgyConst/AgySTT/AgyVosk/AgyTools/...) |

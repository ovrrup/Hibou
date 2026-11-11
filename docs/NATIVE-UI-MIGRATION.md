# Native Kotlin UI Migration

Branch: `kotlin-ui`

This document tracks the staged replacement of the WebView/React UI with a native
Kotlin UI. The repository is a patcher (see `docs/BUILD.md`), so every milestone
must be shippable as an APK overlay without a full application source tree.

## Ground rules

1. **No big-bang cutover.** Every milestone keeps the WebView reachable until the
   native surface has proven parity.
2. **One source of truth for state.** Native screens must read and write the same
   provider, session, attachment, MCP and backup stores as the web layer.
3. **Fail-closed builds.** A build that references a native class which is not in
   the packaged DEX must fail instead of shipping a crash.
4. **No new resource pipeline unless required.** Prefer programmatic views, the
   way `HibouNativeSplash`, `HibouPermissionSheet` and `HibouControlCenterDialog`
   already do. `app/resources.arsc` is precompiled.
5. **Protocol first.** The native UI is only as good as the core client. Contract
   tests for the ConnectRPC layer gate the chat milestones.

## Milestones

### M0 - Foundation and toolchain

- [x] Extract the embedded protobuf descriptors from the web bundle into
      `native-src/proto/descriptors.json` (`tools/extract_proto_descriptors.py`).
- [x] Add a binary `AndroidManifest.xml` patcher
      (`tools/patch_manifest.py`) that registers the native launcher without an
      apktool round-trip.
- [x] Bundle the Kotlin standard library into its own DEX
      (`tools/bundle_kotlin_stdlib.py`) so Kotlin classes cannot crash on a base
      APK that does not ship it.
- [x] Make `rebuild.py` fail closed when the native launcher is enabled but the
      compiled DEX does not contain it.
- [x] Keep `HIBOU_NATIVE_UI=0` as the escape hatch back to the web launcher.

### M1 - Native shell and navigation

- [x] `HibouChatActivity` - native launcher that subclasses the precompiled
      `com.agy.ui.MainActivity` to reuse core startup, splash handshake and the
      hidden WebView fallback.
- [x] `HibouChatView` - top bar, drawer, empty state, transcript list and
      composer built from platform widgets.
- [x] `HibouTheme` - shared colors, spacing and drawables.
- [x] `HibouChatStore` - local conversation model with JSON persistence.
- [x] Web fallback entry in the drawer.
- [ ] Device verification on Android 8/10/13/14.

### M2 - Core protocol client

- [x] `HibouConnect` - ConnectRPC unary and server-streaming transport against
      `https://127.0.0.1:<port>` with the `x-codeium-csrf-token` header.
- [x] `HibouCore` - reflective access to the private `androidBridge` field of
      `MainActivity` for the engine port, CSRF token, readiness and metadata.
- [x] `HibouChatClient` - cascade lifecycle (`StartCascade`,
      `SendUserCascadeMessage`, `CancelCascadeInvocation`,
      `DeleteCascadeTrajectory`), streaming (`StreamAgentStateUpdates`), history
      (`GetAllCascadeTrajectories`, `GetCascadeTrajectory`) and models
      (`GetAvailableModels`).
- [x] Streamed `AgentStateUpdate` steps rendered into the transcript, with
      tool chips, stop/cancel and the local conversation cache.
- [x] Contract tests (`tests/test_native_connect.py`) for the service path,
      headers, envelope framing, descriptor field names and DEX reflection.
- [ ] Device verification against a running core (needs a base APK and device).

#### Codec decision

The shipped web bundle constructs its client with `useBinaryFormat:false`, so
the core is spoken to with the Connect **JSON** codec: unary calls use
`application/json`, streaming calls use `application/connect+json` and 5-byte
envelopes. `HibouProto` is therefore not needed for M2; a protobuf wire
reader/writer is deferred until a call requires binary-only fields.

Cascade ids are generated client-side with `UUID.randomUUID()`, matching
`crypto.randomUUID()` in the web client, so `StartCascade` does not need a
round-trip to obtain an id.

### M3 - Chat transcript and composer

- [ ] Stream assistant responses, reasoning blocks and tool cards.
- [ ] Markdown/GFM renderer, code blocks, diffs and copy actions.
- [ ] Attachment picker (images, PDFs, text) and previews.
- [ ] Model selector with provider groups, quotas and reasoning effort.
- [ ] Retry, edit and regenerate (stop/cancel shipped with M2).

### M4 - Sessions, projects and history

- [ ] Conversation list, search, rename, archive, fork and delete.
- [ ] Projects, worktrees, branches and working directories.
- [ ] Export and backup formats compatible with the web layer.

### M5 - Providers, settings, tools and MCP

- [ ] Unify `HibouControlCenterDialog` on the `agyProviders`/`AgyProviders`
      store (`providers.json`), removing `hibou_providers.json`.
- [ ] Settings, tools installer, console and diagnostics.
- [ ] MCP/UTCP servers, skills and customizations.
- [ ] Security Shield policy surface.

### M6 - Attachments, artifacts and previews

- [ ] Artifact browser, image/PDF previews and save/share.
- [ ] Browser and terminal panels if they cannot be delegated to the core.

### M7 - Auth, theme, i18n and accessibility

- [ ] Native OAuth via Custom Tabs plus secure token storage.
- [ ] Four branding packs, dark/light theme and Russian/English strings.
- [ ] TalkBack, font scaling, RTL, IME insets and predictive back.

### M8 - Web removal and hardening

- [ ] Delete `assets/web/generated/main.js`, workers, icon sprites and the
      WebView host from the packaged APK (`rebuild.py` deletion list).
- [ ] Remove `patch_web_bundle.py` and the web asset overrides.
- [ ] Instrumentation/UI tests and a device matrix in CI.
- [ ] Mandatory Kotlin compilation in CI.

## Architecture

```
HibouChatActivity (classes4.dex)
  extends com.agy.ui.MainActivity (classes.dex)
    - starts core service, owns splash handshake
    - keeps the WebView alive as a hidden fallback
  installs HibouChatView
    HibouChatView
      HibouTopBar
      HibouDrawer
      HibouTranscriptView
      HibouComposerView
    HibouChatStore (JSON in filesDir)
    HibouChatClient (M2) -> HibouConnect -> https://127.0.0.1:<enginePort>
      HibouCore (reflection: androidBridge -> port, CSRF, metadata)
```

### Why subclass `MainActivity`

`MainActivity` is public and non-final, and `agyStartCore()`, `agyHoldCore()`,
`dismissSplash()` and `showFatalError()` are public. Subclassing lets the native
UI reuse the existing core supervisor and splash handshake while the source for
`MainActivity` is unavailable in this repository. The WebView is detached from
the content view but kept alive, so `onUiReady` still dismisses the splash and
the drawer can restore it as a fallback.

### Manifest strategy

`app/AndroidManifest.xml` is binary AXML. `tools/patch_manifest.py` rewrites the
launcher activity name in place to `com.agy.ru.HibouChatActivity`, preserving all
other attributes, the icon, the theme and the intent filter. The patch is
idempotent and is applied by `rebuild.py` at package time.

## Build

```bash
pkg install kotlin d8 openjdk-21
python3 tools/make_android_stubs.py
python3 tools/bundle_kotlin_stdlib.py
python3 tools/compile_kotlin.py
python3 rebuild.py both --fetch-base
```

Set `HIBOU_NATIVE_UI=0` to package the web launcher instead. Set
`HIBOU_REQUIRE_KOTLIN=1` (implied by the native UI) to make missing
`kotlinc`/`d8` a hard error.

## Testing

```bash
python3 -m unittest discover -s tests -v
```

`tests/test_native_chat.py` covers the manifest patch, the launcher DEX
contract, the descriptor index and the native shell sources.
`tests/test_native_connect.py` pins the M2 protocol contract: Connect service
path and headers, JSON codec choice, envelope framing
(`tools/connect_frames.py`), every JSON key used by the client against
`native-src/proto/descriptors.json`, the `androidBridge` reflection targets read
with `tools/dex_inspect.py`, and the loopback-only guard.

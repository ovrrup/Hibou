# Setup & Diagnostics

[Home](../README.md) · [Building](BUILD.md) · [Changelog](../CHANGELOG.md)

## Installation & Upgrades

Download the **Standard** or **Bypass** package from the [Releases](https://github.com/ovrrup/Hibou/releases/latest) page. Both variants target Android ARM64; the Bypass build patches regional verification checks in the core engine.

When signatures match, the app can be upgraded in place over an existing installation. If Android reports a package conflict or signature mismatch:

1. Backup your projects and application data to external storage (`/storage/emulated/0/Documents/Hibou Backup/`).
2. Verify that important files are safely stored outside the app's internal sandbox.
3. Uninstall the conflicting application, then install the new APK.
4. Complete the initial setup walkthrough and restore your data from backup.

> [!NOTE]
> Uninstalling the app completely clears internal app data (`/data/user/0/agy.hibou/`). Ensure you have exported backups before reinstalling.

## Providers & Models

Standard and custom model providers use separate core modes. To configure a custom API endpoint:
- Navigate to **Settings → Providers**.
- Select the provider format (OpenAI-compatible, Anthropic Claude, or Gemini-compatible).
- Enter the Base URL and API Key.
- Tap **Load Models** or add model identifiers manually.

Configured models appear grouped under provider headers in the chat model selector. Capability toggles (images, tools, reasoning effort) can be fine-tuned per model. Voice dictation with custom providers uses Android's native SpeechRecognizer service.

## Developer Tooling

Open **Settings → Tools** to manage development packages:
- Android development toolchain: `aapt`, `aapt2`, `d8`, `apksigner`, `aidl`, and `android-tools` (`adb`, `fastboot`).
- Compilers & build tools: Clang/LLVM, LLD, CMake, Make, Ninja, and Python 3.
- JVM ecosystem: OpenJDK 17 LTS, OpenJDK 21, Kotlin, and Gradle.
- Web development: Node.js LTS, npm, Yarn, pnpm, and TypeScript.
- Flutter & Dart SDKs.

> [!IMPORTANT]
> When installing Node.js, use **Node.js LTS**. Do not install both LTS and the current branch simultaneously, as they conflict over the `node` binary.

Verify installations via the terminal:

```bash
command -v node && node --version
command -v npm && npm --version
command -v clang && clang --version
```

For specialized tooling guidance, see: [Flutter/Dart](flutter-toolchain.md) and [JVM/jadx](jadx-fixes.md).

## Console & Error History

The **Tools** settings tab includes an integrated application console and error log inspector:
- **Console:** Runs individual commands in separate background jobs, displays real-time execution logs, supports clearing output, and captures device `logcat`. (Note: this runs commands non-interactively; environment state between tasks is managed via the shell profile).
- **Error History:** Dedicated log stream capturing package installation and verification failures. Includes quick copy-all and clear actions.

When reporting an issue, provide your APK version and variant, Android OS version, reproduction steps, and relevant log snippets. Never include API keys or secret tokens in public reports.

## MCP, UTCP & Plugins

- **Model Context Protocol (MCP):** Configured in **Settings → MCP Servers**. JavaScript-based MCP servers require Node.js LTS. Configuration template: [`mcp_config.example.json`](../mcp_config.example.json); live configuration: `~/.gemini/config/mcp_config.json`.
- **UTCP / OpenAPI:** Connect external tools and APIs dynamically via the agent tooling bridge.
- **Agent Customizations:** Manage agent skills, global instructions (`AGENTS.md`), and UI plugins in **Settings → Customizations**.

## Sessions & Backups

- **Export Session:** Saves conversation history, prompt steps, and media attachments as text, Markdown, or PDF (via PyMuPDF).
- **Backup & Restore:** Located in **Settings → Backup**. Exports and imports full application state as a ZIP archive, including provider configurations, MCP configurations, custom settings, chat history, and attachments.
  - Default backup path: `Hibou Backup` (`/storage/emulated/0/Documents/Hibou Backup/`).
  - Default project directory: `Hibou Projects` (`/storage/emulated/0/Documents/Hibou Projects/`).

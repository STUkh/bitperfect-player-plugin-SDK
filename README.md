# Bitperfect Player Plugin SDK (API Level 6)

Official open-source Software Development Kit (SDK) for building external, independent plugins for **Bitperfect Player** on Android.

---

## 1. Overview & Architecture

Bitperfect Player uses an **Out-of-Process Isolation** and **Zero-Trust Sandbox** architecture:

* **Separate Linux Process**: Each plugin is packaged and distributed as an independent Android application (APK) running in its own sandboxed process.
* **No Third-Party Bytecode in Host**: The player never uses `DexClassLoader` or loads untrusted code into the playback engine.
* **No PCM over Binder IPC**:
  * **Audio Sources (`SOURCE`)**: Plugins return stream descriptors (`StreamDescriptorDto`: URL, HTTP headers, decrypt key). Network streaming, bit-perfect decoding, and DAC hardware communication are handled host-side.
  * **DSP Processing (`DSP`)**: High-resolution audio frames are exchanged out-of-process via Linux `SharedMemory` (Ashmem) zero-copy ring buffers and `eventfd` signals, eliminating Binder IPC bottlenecks.
* **Server-Driven UI (Pages API v1)**: Plugin catalog and configuration screens are described via declarative JSON models (`PluginPage`). The host renders them natively using the Bitperfect Player Flat-Dark design system with buttery-smooth **120 FPS** animations.

---

## 2. Repository Structure

```text
bitperfect-player-plugin-sdk/
├── .claude/                     # AI assistant instructions & skill playbooks
│   ├── CLAUDE.md                # Claude Code reference to AGENTS.md
│   └── skills/                  # Turnkey AI skills (bitperfect-plugin-dev)
├── AGENTS.md                    # Universal AI agent instructions & architectural invariants
├── plugin-aidl/                 # AIDL interfaces (IBitperfectPluginV1) & Parcelable DTOs
├── plugin-pages/                # Kotlin models & parser for Server-Driven UI (Pages API v1)
├── plugin-runtime/              # Base services (BitperfectSourcePlugin, BitperfectDspPlugin) & trust guards
├── sample-plugin/               # Basic buildable sample plugin app
├── sample-network-source/       # Legal open-source streaming source (FLAC/MP3, SDUI, search, tabs)
├── sample-dsp-filter/           # Audiophile DSP filter (SharedMemory zero-copy ring, eventfd, SLIDER/SWITCH)
├── docs/                        # In-depth technical guides
│   ├── naming-and-discovery.md  # Critical package naming & manifest discovery rules
│   ├── pages-sdui-guide.md      # Server-Driven UI, 120 FPS controls, SWITCH, SLIDER
│   ├── dsp-audio-processing.md  # Out-of-process DSP audio processing via SharedMemory
│   └── ai-plugin-dev-skill.md   # AI assistant skill tooling & activation guide
├── build.gradle.kts             # Root Gradle build configuration
├── settings.gradle.kts          # Multi-project Gradle settings
└── gradle/                      # Gradle wrapper & version catalogs
```

---

## 3. Quick Start: Build & Run in 3 Minutes

### Prerequisites
* JDK 17+
* Android SDK (API 35+ / compileSdk 37)

### Step 1: Clone & Build
```bash
git clone https://github.com/bitperfect-player/bitperfect-player-plugin-sdk.git
cd bitperfect-player-plugin-sdk
./gradlew assembleDebug test
```

### Step 2: Install Sample Plugin on Device
```bash
adb install -r sample-plugin/build/outputs/apk/debug/sample-plugin-debug.apk
```

### Step 3: Enable in Bitperfect Player
1. Open **Bitperfect Player → Settings → Plugins**.
2. Locate **Sample Music** in the discovered plugins list.
3. Toggle the plugin on.
4. Open the navigation sidebar to browse your new plugin's catalog!


### For AI Coding Assistants & Robots
AI coding agents (Claude Code, Cursor, Windsurf, Copilot, Cline, etc.) can start working immediately:
* **Universal Rules**: Read [`AGENTS.md`](AGENTS.md) for module mappings, build commands, and architectural constraints.
* **Plugin Dev Skill**: Consult [`.claude/skills/bitperfect-plugin-dev/SKILL.md`](.claude/skills/bitperfect-plugin-dev/SKILL.md) and reference guides before generating or editing plugin code.
---

## 4. Capabilities Reference Table (API Level 6)

Plugins declare their capabilities as an integer bitmask via `<meta-data android:name="com.bitperfect.plugin.CAPABILITIES" ... />` and `capabilities()`:

| Constant | Bit | Value | Description |
|---|---|---|---|
| `CAPABILITY_SOURCE` | `1 << 0` | `1` | Audio source: library catalog, search, and direct stream resolution. |
| `CAPABILITY_METADATA` | `1 << 1` | `2` | Track/album metadata enrichment. |
| `CAPABILITY_OUTPUT_RENDERER` | `1 << 2` | `4` | External playback renderer (UPnP / DLNA / Cast). |
| `CAPABILITY_UI_TABS` | `1 << 3` | `8` | Host navigation entry opening a plugin-owned Activity. |
| `CAPABILITY_NOW_PLAYING_EXT` | `1 << 4` | `16` | Custom `RemoteViews` card embedded in the Now Playing screen. |
| `CAPABILITY_SETTINGS` | `1 << 5` | `32` | Settings sub-screen (via plugin Activity). |
| `CAPABILITY_AUTH` | `1 << 6` | `64` | Access to host credential callbacks. |
| `CAPABILITY_CLOUD_SOURCE` | `1 << 7` | `128` | Cloud storage browser (WebDAV, Nextcloud, S3, etc.). |
| `CAPABILITY_PAGES` | `1 << 8` | `256` | Declarative Server-Driven UI (`onRenderPage` / `onDispatchAction`). |
| `CAPABILITY_DSP` | `1 << 9` | `512` | Out-of-process real-time DSP filter stage via `SharedMemory`. |
| `CAPABILITY_RECEIVER` | `1 << 10` | `1024` | Remote-control receiver / cast target. |

---

## 5. Critical Discovery Rule: Package Naming

> 🚨 **IMPORTANT**: The declared `com.bitperfect.plugin.PLUGIN_ID` in your `AndroidManifest.xml` **MUST strictly match** the APK's `applicationId` (package name).

The host application validates identity against Android's `PackageManager`. If there is any mismatch between `PLUGIN_ID` and the actual package name, the plugin is rejected to prevent impersonation.

```xml
<service
    android:name=".MyPluginService"
    android:exported="true">
    <intent-filter>
        <action android:name="com.bitperfect.plugin.BIND_PLUGIN" />
    </intent-filter>
    <meta-data
        android:name="com.bitperfect.plugin.PLUGIN_ID"
        android:value="com.example.bitperfect.plugin.mysource" />
    ...
</service>
```

For complete details on manifest tags and extension IDs, read the [Plugin Naming, Manifest & Discovery Guide](docs/naming-and-discovery.md).

---

## 6. Building AAR Artifacts

To compile standalone release AAR libraries:
```bash
./gradlew assembleRelease
```
The output AARs will be located at:
* `plugin-aidl/build/outputs/aar/plugin-aidl-release.aar`
* `plugin-pages/build/outputs/aar/plugin-pages-release.aar`
* `plugin-runtime/build/outputs/aar/plugin-runtime-release.aar`

---

## 7. Developer Tools & Scripts

* **Scaffold a new plugin**:
  ```bash
  ./scripts/create-plugin.sh --id com.example.myplugin --name MyAudioPlugin --type source --out my-audio-plugin
  ```
* **Validate plugin manifest**:
  ```bash
  ./scripts/validate-plugin.sh my-audio-plugin
  ```
* **Publish SDK to Maven Local**:
  ```bash
  ./gradlew publishToMavenLocal
  ```

---

## 8. AI Assistant & Agent Skills

This repository includes a turnkey AI coding skill playbook designed specifically for AI robots and coding agents (Claude Code, Cursor, Windsurf, GitHub Copilot, Cline, skills.sh).

### Skill Location & Structure
* 🤖 **Universal Guidelines**: [`AGENTS.md`](AGENTS.md) — Architectural invariants, module maps, build commands, and security bounds.
* 📘 **Primary Playbook**: [`.claude/skills/bitperfect-plugin-dev/SKILL.md`](.claude/skills/bitperfect-plugin-dev/SKILL.md) — Out-of-process architecture rules, manifest discovery, and implementation templates.
* 🎨 **SDUI Schema & Vocabulary**: [`.claude/skills/bitperfect-plugin-dev/references/pages-vocabulary.md`](.claude/skills/bitperfect-plugin-dev/references/pages-vocabulary.md) — Pages API v1 JSON models, 12 section layouts, 120 FPS latency constraints.
* 🔌 **AIDL Contracts**: [`.claude/skills/bitperfect-plugin-dev/references/aidl-contract.md`](.claude/skills/bitperfect-plugin-dev/references/aidl-contract.md) — Exact AIDL signatures, parcelable DTOs, and session protocols.
* 📖 **In-Depth Guide**: [`docs/ai-plugin-dev-skill.md`](docs/ai-plugin-dev-skill.md) — Tooling integration, trigger configurations, and prompt guides.

### Activation Triggers
AI agents should activate or consult this skill whenever prompt topics involve:
* `"bitperfect plugin"`
* `"create plugin"` / `"scaffold plugin"`
* `"audio source plugin"`
* `"dsp plugin"` / `"sharedmemory dsp"`
* `"pages api"` / `"sdui"`
* `"plugin manifest"` / `"BIND_PLUGIN"`

### How to Load in AI Coding Environments
* **Claude Code**: Works out of the box via `.claude/CLAUDE.md` and `.claude/skills/`.
* **Cursor / Windsurf**: Add a rule in `.cursorrules` / `.windsurfrules` pointing to `AGENTS.md` and `.claude/skills/bitperfect-plugin-dev/SKILL.md`.
* **skills.sh Ecosystem**:
  ```bash
  npx skills add bitperfect-player/bitperfect-plugin-dev
  ```

---

## 9. Documentation & Contributing

* 📖 [Plugin Naming, Manifest & Discovery Guide](docs/naming-and-discovery.md)
* 🎨 [Server-Driven UI (Pages API v1) Guide](docs/pages-sdui-guide.md)
* 🎛️ [DSP Audio Processing via SharedMemory](docs/dsp-audio-processing.md)
* 🤝 [Contributing Guide](CONTRIBUTING.md)
* 🤖 [AI Coding Assistant Skill Guide](docs/ai-plugin-dev-skill.md)

---

## 10. License
Licensed under the [Apache License, Version 2.0](LICENSE).

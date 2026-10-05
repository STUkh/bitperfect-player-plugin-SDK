# Repository Guidelines — Bitperfect Player Plugin SDK

Official Software Development Kit (SDK) and reference implementation for building out-of-process Android plugins (API Level 6).

Read `.claude/skills/bitperfect-plugin-dev/SKILL.md` before generating, modifying, or reviewing plugin implementations.

## Module Map

- `:plugin-aidl` — AIDL interfaces (`IBitperfectPluginV1`, `IPluginHostCallbackV1`, `IPluginDspSessionV1`, `IPluginReceiverSessionV1`) and Parcelable DTOs (`TrackRefDto`, `StreamDescriptorDto`, `PageDescriptorDto`, `ActionResultDto`, `DspSessionOfferDto`, etc.).
- `:plugin-pages` — Server-Driven UI (Pages API v1) data models (`PluginPage`, `PluginSection`, `PluginItem`, `PluginAction`) and JSON parser.
- `:plugin-runtime` — Base plugin services (`BitperfectSourcePlugin`, `BitperfectDspPlugin`, `BitperfectRendererPlugin`), signature verification, and host trust guards (`PluginHostTrust`).
- `:sample-plugin` — Minimal baseline plugin module.
- `:sample-network-source` — Streaming source plugin with Server-Driven UI, catalog browsing, and search.
- `:sample-dsp-filter` — Real-time out-of-process DSP audio filter with Linux `SharedMemory` ring buffer and `eventfd` signaling.
- `scripts/` — CLI developer tools (`create-plugin.sh`, `validate-plugin.sh`).
- `.claude/skills/bitperfect-plugin-dev/` — Machine-readable AI agent skill playbook and modular reference guides.

## Core Architectural Invariants

### 1. Zero-Trust Host Sandbox
- **Zero Host Infiltration**: Plugins run as independent APKs in separate Linux processes. Host never loads plugin bytecode via `DexClassLoader` and never embeds external View/Compose hierarchies.
- **In-Host UI**: Rendered exclusively from declarative Pages API v1 JSON descriptors. Interactive full-screen experiences run as plugin-owned Activities in the plugin process, launched via explicit `Intent`.
- **Identity Rule**: Manifest `com.bitperfect.plugin.PLUGIN_ID` **must strictly equal** `applicationId` (package name). Mismatches fail host admission checks.
- **Permissions**: Plugins run under their own Linux UID and declare required Android permissions (e.g. `android.permission.INTERNET`). Host grants zero permissions.

### 2. Zero PCM over Binder IPC
- **Audio Sources (`CAPABILITY_SOURCE`)**: Plugins return stream descriptors (`StreamDescriptorDto`: URL, HTTP headers, decrypt cipher/key). Host downloads, bit-perfect decodes, and routes to DAC hardware. Never stream audio samples over Binder.
- **DSP Filters (`CAPABILITY_DSP`)**: High-resolution audio frames are exchanged out-of-process via Linux `SharedMemory` (Ashmem) zero-copy ring buffers and `eventfd` notify signals. Real-time processing must complete within the 2.0 ms deadline.

### 3. Server-Driven UI (Pages API v1) Constraints
- **Payload Limit**: Page descriptor JSON must not exceed 512 KiB.
- **Structural Bounds**: Maximum 12 nesting depth, 40 sections per page, 500 items per section.
- **120 FPS Target**: Keep page serialization fast; avoid heavy computation on UI thread.
- **Supported Section Kinds**: `LIST`, `GRID`, `SHELF`, `CHIPS`, `HERO_CAROUSEL`, `QUICK_PLAY`, `RADIO`, `BUTTONS`, `SWITCH`, `SLIDER`, `KEY_VALUE`, `TEXT`. Read `.claude/skills/bitperfect-plugin-dev/references/pages-vocabulary.md` for schemas.
- **Theme**: Host renders strictly using Flat-Dark design tokens. No light mode, neon glows, or translucent blurs.

### 4. AIDL Evolution & Contracts
- **Append-Only Transactions**: Never reorder, mutate, or remove transaction methods in `IBitperfectPluginV1.aidl`. Always append new methods at the end and bump `API_VERSION`.
- **Read** `.claude/skills/bitperfect-plugin-dev/references/aidl-contract.md` when inspecting transaction codes or method signatures.

## Build and Verification Commands

Run commands from the repository root:

- **Build and unit test**:
  ```bash
  ./gradlew assembleDebug test
  ```
- **Full CI verification**:
  ```bash
  ./gradlew check assembleRelease assembleDebug
  ```
- **Publish SDK artifacts to Maven Local** (`~/.m2/repository`):
  ```bash
  ./gradlew publishToMavenLocal
  ```
- **Validate plugin manifest**:
  ```bash
  ./scripts/validate-plugin.sh <plugin-module-dir>
  ```
- **Scaffold a new plugin module**:
  ```bash
  ./scripts/create-plugin.sh --id <applicationId> --name <PluginName> --type <source|dsp|renderer> --out <target-dir>
  ```

## AI Agent Skill Playbook

The repository includes a turnkey skill for AI coding agents:
- **Playbook**: `.claude/skills/bitperfect-plugin-dev/SKILL.md`
- **SDUI Vocabulary**: `.claude/skills/bitperfect-plugin-dev/references/pages-vocabulary.md`
- **AIDL Contracts**: `.claude/skills/bitperfect-plugin-dev/references/aidl-contract.md`
- **Integration Guide**: `docs/ai-plugin-dev-skill.md`

**Agent Directive**: Always consult `.claude/skills/bitperfect-plugin-dev/SKILL.md` when prompted to create, modify, or debug plugins, manifest declarations, SDUI pages, or DSP pipelines.

## Code Style & Engineering Standards

- **Comments**: Single-line comments only (`//`). Never write multi-line comments (`/* ... */`). Add comments only for non-obvious constraints.
- **Kotlin & Toolchain**: Kotlin 1.9+, Java 17, Android `compileSdk 37`, `minSdk 26`.
- **Models & Serialization**: Use `@Serializable` data classes for internal page models. Ensure ProGuard keeps all DTOs and serialized types.
- **Verification Gate**: Every change must pass `./scripts/validate-plugin.sh <module>` and `./gradlew test` before declaring completion.

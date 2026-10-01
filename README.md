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
├── plugin-aidl/                 # AIDL interfaces (IBitperfectPluginV1) & Parcelable DTOs
├── plugin-pages/                # Kotlin models & parser for Server-Driven UI (Pages API v1)
├── plugin-runtime/              # Base services (BitperfectSourcePlugin, BitperfectDspPlugin) & trust guards
├── sample-plugin/               # Basic buildable sample plugin app
├── sample-network-source/       # Legal open-source streaming source (FLAC/MP3, SDUI, search, tabs)
├── sample-dsp-filter/           # Audiophile DSP filter (SharedMemory zero-copy ring, eventfd, SLIDER/SWITCH)
├── skills/                      # AI assistant skills (skills.sh, Claude, Cursor, Windsurf)
│   └── bitperfect-plugin-dev/   # Complete plugin dev skill playbook
├── docs/                        # In-depth technical guides
│   ├── naming-and-discovery.md  # Critical package naming & manifest discovery rules
│   ├── pages-sdui-guide.md      # Server-Driven UI, 120 FPS controls, SWITCH, SLIDER
│   └── dsp-audio-processing.md  # Out-of-process DSP audio processing via SharedMemory
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

## 8. Documentation & Contributing

* 📖 [Plugin Naming, Manifest & Discovery Guide](docs/naming-and-discovery.md)
* 🎨 [Server-Driven UI (Pages API v1) Guide](docs/pages-sdui-guide.md)
* 🎛️ [DSP Audio Processing via SharedMemory](docs/dsp-audio-processing.md)
* 🤝 [Contributing Guide](CONTRIBUTING.md)
* 🤖 [AI Coding Assistant Skill (skills.sh / Cursor / Claude)](docs/ai-plugin-dev-skill.md)

---

## 9. License
Licensed under the [Apache License, Version 2.0](LICENSE).

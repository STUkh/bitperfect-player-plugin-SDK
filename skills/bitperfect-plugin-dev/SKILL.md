---
name: bitperfect-plugin-dev
description: Bitperfect Player plugin development skill for skills.sh, Claude, Cursor, and Windsurf. Scaffolding, AIDL interfaces, manifest discovery, Pages API v1 Server-Driven UI, Linux SharedMemory zero-copy DSP, and zero-trust security invariants.
---

# Bitperfect Player Plugin Development Playbook

Authoritative specification and development guide for building plugins for **Bitperfect Player** (audiophile music player on Android).
Covers architecture, security invariants, manifest declarations, Server-Driven UI (Pages API v1), out-of-process DSP audio processing via Linux `SharedMemory`, and production best practices.

---

## 1. Architectural Model & Security Invariants

A Bitperfect Player plugin is a **separate, signed Android APK** communicating with the player host over Android IPC (AIDL) and Linux kernel primitives.

### 🛡️ Core Security Invariants
1. **Zero Host Infiltration (No Plugin Code in Host)**:
   - The host application **NEVER** executes plugin bytecode via `DexClassLoader`.
   - The host **NEVER** loads external Compose/View hierarchies.
   - All in-host plugin UI is strictly **declarative Server-Driven UI (Pages API v1)** parsed from JSON descriptors.
   - Interactive full-screen experiences run as **plugin-owned Activities** in the plugin's own sandboxed process.
2. **Zero PCM over Binder IPC**:
   - Streaming source plugins (`CAPABILITY_SOURCE`) return direct stream descriptors (URLs, HTTP headers, authentication tokens). Host handles downloading, network jitter buffering, bit-perfect decoding, and DAC output.
   - DSP plugins (`CAPABILITY_DSP`) process audio frames out-of-process via **Linux `SharedMemory` (Ashmem) zero-copy ring buffers** and `eventfd` signals. Binder is never used for raw audio sample payloads.
3. **No Elevated Host Permissions**:
   - The plugin runs under its own Linux UID.
   - Host grants no permissions; plugin requests only its own required permissions (e.g. `android.permission.INTERNET`).
4. **Dark-Only Design Language**:
   - Plugin screens rendered by the host adhere strictly to the **Bitperfect Flat-Dark design system**.
   - No glass, blur, neon glows, or global theme overrides.
5. **Capability Gating**:
   - The host only invokes AIDL methods corresponding to declared capability bits in `com.bitperfect.plugin.CAPABILITIES`.

---

## 2. Capabilities Reference Table (Host API v6)

| Capability Constant | Bitmask | Decimal | Description |
|---|---|---|---|
| `CAPABILITY_SOURCE` | `1 << 0` | `1` | Streaming or library source (stream resolution, catalog browse, search). |
| `CAPABILITY_METADATA` | `1 << 1` | `2` | Supplementary metadata provider (lyrics, artist bios, credits). |
| `CAPABILITY_OUTPUT_RENDERER` | `1 << 2` | `4` | External output target (UPnP, DLNA, Cast renderer). |
| `CAPABILITY_UI_TABS` | `1 << 3` | `8` | Host navigation drawer/sidebar entry linking to plugin Activity. |
| `CAPABILITY_NOW_PLAYING_EXT` | `1 << 4` | `16` | Embedded `RemoteViews` card inside Now Playing screen. |
| `CAPABILITY_SETTINGS` | `1 << 5` | `32` | Settings sub-screen deep-linking to plugin Activity. |
| `CAPABILITY_AUTH` | `1 << 6` | `64` | Access to host credential callbacks. |
| `CAPABILITY_CLOUD_SOURCE` | `1 << 7` | `128` | Cloud storage browser (WebDAV, Nextcloud, S3, etc.). |
| `CAPABILITY_PAGES` | `1 << 8` | `256` | Declarative Server-Driven UI (`onRenderPage` / `onDispatchAction`). |
| `CAPABILITY_DSP` | `1 << 9` | `512` | Out-of-process real-time DSP filter stage via `SharedMemory`. |
| `CAPABILITY_RECEIVER` | `1 << 10` | `1024` | Remote-control cast target / receiver session. |

---

## 3. Manifest & Discovery Invariants

### 🚨 Critical Discovery Rule: Package Naming Identity
The metadata tag `com.bitperfect.plugin.PLUGIN_ID` **MUST strictly match the APK's `applicationId`** (package name):

```xml
<manifest package="com.example.bitperfect.plugin.mysource">
    <application>
        <service
            android:name=".MyPluginService"
            android:exported="true">
            <intent-filter>
                <action android:name="com.bitperfect.plugin.BIND_PLUGIN" />
            </intent-filter>

            <!-- MUST EQUAL applicationId -->
            <meta-data
                android:name="com.bitperfect.plugin.PLUGIN_ID"
                android:value="com.example.bitperfect.plugin.mysource" />

            <!-- Bitmask: SOURCE (1) | PAGES (256) = 257 -->
            <meta-data
                android:name="com.bitperfect.plugin.CAPABILITIES"
                android:value="257" />

            <!-- Minimum host API version (6 for API Level 6) -->
            <meta-data
                android:name="com.bitperfect.plugin.MIN_HOST_API"
                android:value="6" />

            <!-- Host Navigation Integration -->
            <meta-data
                android:name="com.bitperfect.plugin.NAVIGATION_EXTENSION_ID"
                android:value="my_source_catalog" />
            <meta-data
                android:name="com.bitperfect.plugin.NAVIGATION_TARGET"
                android:value="sidebar" />
            <meta-data
                android:name="com.bitperfect.plugin.NAVIGATION_LABEL"
                android:value="My Music Catalog" />
            <meta-data
                android:name="com.bitperfect.plugin.NAVIGATION_ROUTE"
                android:value="catalog" />
            <meta-data
                android:name="com.bitperfect.plugin.NAVIGATION_ORDER"
                android:value="10" />

            <!-- Declarative Settings Screen -->
            <meta-data
                android:name="com.bitperfect.plugin.SETTINGS_ROUTE"
                android:value="settings" />
        </service>
    </application>
</manifest>
```

---

## 4. Server-Driven UI (Pages API v1) Specification

When declaring `CAPABILITY_PAGES`, the plugin provides declarative JSON descriptors via `onRenderPage(routeId, paramsJson, pageToken)`.

### Root Page Schema (`PluginPage`)
```json
{
  "v": 1,
  "title": "Station Catalog",
  "subtitle": "Curated Lossless Streams",
  "layout": "DETAIL",
  "tabs": {
    "selectedId": "featured",
    "tabs": [
      { "id": "featured", "label": "Featured", "params": { "tab": "featured" } },
      { "id": "genres", "label": "Genres", "params": { "tab": "genres" } }
    ]
  },
  "search": {
    "placeholder": "Search stations...",
    "routeId": "catalog",
    "paramKey": "q",
    "minChars": 2
  },
  "sections": [ ... ]
}
```

### Supported Section Kinds (`kind`)
1. **`LIST`**: Vertical song/station list. Displays track number, title, subtitle, duration, format badge (`HI_RES`, `LOSSLESS`), and context menu. Supports `nextPageToken` for infinite scrolling.
2. **`GRID`**: 2-column adaptive grid for albums, artists, or stations.
3. **`SHELF`**: Horizontal scroll carousel of items (160–172 dp width).
4. **`CHIPS`**: Horizontal category filter chips (`selectedId` highlights active chip).
5. **`SWITCH`**: Boolean toggle row. Toggled locally; sends `onDispatchAction` with `{ "checked": "true" }`.
6. **`SLIDER`**: Stepped or continuous numeric slider (`minValue`, `maxValue`, `step`, `value`, `meta`).
   - **120 FPS Latency Shield**: Local touch tracking updates readout at up to 120 Hz. IPC Binder call is dispatched **only on touch release** (`{ "value": "2.5" }`).
7. **`KEY_VALUE`**: Technical telemetry table displaying `title` on the left and `meta` on the right.
8. **`BUTTONS`**: Interactive action buttons list.
9. **`TEXT`**: Multi-line text block (up to 4000 characters).

### Parser Constraints
- **Max JSON payload**: 512 KiB per page.
- **Max nesting depth**: 12 levels.
- **Max sections**: 40 per page.
- **Max items per section**: 500 items.
- **Image URLs**: Must be HTTPS and <= 2048 characters.
- **Timeouts**: `onRenderPage` has a 10s timeout; `onDispatchAction` has a 15s timeout.

---

## 5. Linux SharedMemory (Ashmem) DSP Specification

When declaring `CAPABILITY_DSP`, the host invokes `onOpenDspSession(offer: DspSessionOfferDto)`.

### Offer Negotiation (`DspSessionOfferDto`)
- `sessionId`: Unique identifier for the audio session.
- `format`: `DspStreamFormatDto` (sampleRate: 44100..384000, channels: 2, sampleFormat: `SAMPLE_FORMAT_FLOAT32`).
- `chunkFrames`: Number of audio frames per processing chunk (e.g. 512).
- `capacityFrames`: Total capacity of the circular ring buffer (e.g. 4096).
- `shmemInFd`: `ParcelFileDescriptor` for the input audio shared memory.
- `shmemOutFd`: `ParcelFileDescriptor` for the output audio shared memory.
- `notifyInFd`: `ParcelFileDescriptor` (Linux `eventfd`) signaled by host when new frames are ready.
- `notifyOutFd`: `ParcelFileDescriptor` (Linux `eventfd`) signaled by plugin when output frames are written.

### Real-Time Zero-Copy Processing Loop
```kotlin
// 1. Map memory zero-copy via FileChannel.map (or Os.mmap)
val ringBytes = (offer.capacityFrames * offer.format.channels * 4).toLong()
val inMapped = FileInputStream(offer.shmemInFd.fileDescriptor).channel
    .map(FileChannel.MapMode.READ_ONLY, 0, ringBytes).order(ByteOrder.nativeOrder())
val outMapped = FileOutputStream(offer.shmemOutFd.fileDescriptor).channel
    .map(FileChannel.MapMode.READ_WRITE, 0, ringBytes).order(ByteOrder.nativeOrder())

// 2. EventFD synchronization with 2.0 ms deadline
val pollFd = StructPollfd().apply {
    fd = offer.notifyInFd.fileDescriptor
    events = OsConstants.POLLIN.toShort()
}

while (!isClosed.get()) {
    if (Os.poll(arrayOf(pollFd), 2) > 0) {
        // Drain 8-byte uint64 eventfd counter
        drainEventFd(offer.notifyInFd.fileDescriptor)

        // Process samples in memory
        filter.process(inMapped.asFloatBuffer(), outMapped.asFloatBuffer())

        // Signal host that processing is complete
        signalEventFd(offer.notifyOutFd.fileDescriptor)
    }
}
```

---

## 6. Quickstart: Building a Streaming Source Plugin

### 1. `build.gradle.kts`
```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.bitperfect.plugin.mysource"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.bitperfect.plugin.mysource"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }
}

dependencies {
    implementation("com.bitperfect.player.sdk:plugin-runtime:1.0.0")
    implementation("com.bitperfect.player.sdk:plugin-aidl:1.0.0")
    implementation("com.bitperfect.player.sdk:plugin-pages:1.0.0")
    implementation(libs.serialization.json)
}
```

### 2. Service Implementation
```kotlin
package com.example.bitperfect.plugin.mysource

import com.bitperfect.player.sdk.plugin.dto.*
import com.bitperfect.player.sdk.plugin.runtime.BitperfectSourcePlugin
import com.bitperfect.player.sdk.plugin.runtime.PluginSdk

class MySourcePlugin : BitperfectSourcePlugin() {

    override fun capabilities(): Int =
        PluginSdk.CAPABILITY_SOURCE or PluginSdk.CAPABILITY_PAGES

    override fun onResolveStream(
        track: TrackRefDto,
        maxBitDepth: Int,
        maxSampleRate: Int,
    ): StreamDescriptorDto? {
        return StreamDescriptorDto(
            url = "https://stream.example.com/audio.flac",
            headers = mapOf("User-Agent" to "BitperfectPlayer/1.0"),
            encrypted = false,
            trackKey = null,
            cipher = null,
            codec = "flac",
            bitDepth = 16,
            sampleRate = 44100,
            expiresAtEpochMs = System.currentTimeMillis() + 86400000L,
        )
    }

    override fun onRenderPage(
        routeId: String,
        paramsJson: String?,
        pageToken: String?,
    ): PageDescriptorDto? {
        val json = """
        {
          "v": 1,
          "title": "Open Audio",
          "layout": "DETAIL",
          "sections": [
            {
              "id": "stations",
              "kind": "LIST",
              "items": [
                {
                  "id": "st_1",
                  "title": "Classical FLAC Live",
                  "subtitle": "44.1kHz / 16-bit FLAC",
                  "action": {
                    "type": "play",
                    "tracks": [
                      { "trackId": "st_1", "title": "Classical Live", "artist": "Broadcast", "durationMs": 0 }
                    ]
                  }
                }
              ]
            }
          ]
        }
        """.trimIndent()

        return PageDescriptorDto(1, json)
    }

    override fun onDispatchAction(actionId: String, paramsJson: String?): ActionResultDto {
        return ActionResultDto(ActionResultDto.KIND_NONE, null)
    }
}
```

---

## 7. Verification Checklist

Before publishing or committing a plugin:
- [ ] `PLUGIN_ID` in `AndroidManifest.xml` matches `applicationId` character-for-character.
- [ ] Service has `<intent-filter><action android:name="com.bitperfect.plugin.BIND_PLUGIN" /></intent-filter>`.
- [ ] `getCapabilities()` in Kotlin matches `com.bitperfect.plugin.CAPABILITIES` in `AndroidManifest.xml`.
- [ ] No commercial or proprietary streaming service names or credentials in codebase.
- [ ] Pages API v1 JSON validated against `PluginPageLimits` (<= 512 KiB, depth <= 12).
- [ ] Sliders dispatch action on finger release; no Binder IPC generated per drag frame.
- [ ] DSP plugins process audio exclusively via SharedMemory ring buffer within the 2.0 ms deadline.
- [ ] ProGuard rules keep DTOs, `PluginPage` serialization models, and Plugin Service subclasses intact.

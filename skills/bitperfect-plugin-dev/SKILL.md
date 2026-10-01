---
name: bitperfect-plugin-dev
description: Develops plugins for Bitperfect Player on Android, covering architecture, manifest discovery, Pages API v1 Server-Driven UI, Linux SharedMemory DSP, and zero-trust sandbox rules. Use when you need to build, modify, or debug a bitperfect plugin, create plugin, audio source plugin, dsp plugin, pages api, or plugin manifest.
allowed-tools: Read, Write, Edit, Glob, Grep, Bash
---

# Bitperfect Player Plugin Development

Reference Guides:
- **Read** [references/pages-vocabulary.md](references/pages-vocabulary.md) when constructing Server-Driven UI pages, choosing section layouts, configuring slider/switch controls, or checking JSON limits.
- **Read** [references/aidl-contract.md](references/aidl-contract.md) when inspecting AIDL transaction IDs, method signatures, host callbacks, or session contracts.

---

## 1. Zero-Trust Sandbox & Discovery Invariants

- **Zero Host Infiltration**: The host application **never** executes plugin bytecode via `DexClassLoader` and **never** loads external View/Compose hierarchies. Host UI is strictly declarative Pages API v1 JSON. Full-screen interactive experiences run as plugin-owned Activities in the plugin's own sandboxed process.
- **Canonical Identity Rule (`PLUGIN_ID == applicationId`)**: The manifest tag `com.bitperfect.plugin.PLUGIN_ID` **must strictly equal** the APK's `applicationId` (package name). The host rejects mismatched plugins before admission, approval lookup, or binding.
- **Zero PCM over Binder**: Streaming sources (`CAPABILITY_SOURCE`) resolve URLs and headers; host handles downloading and DAC output. DSP filters (`CAPABILITY_DSP`) process PCM out-of-process via Linux `SharedMemory` ring buffers and `eventfd` synchronization within a 2.0 ms deadline. Never send raw PCM samples across Binder.
- **No Elevated Permissions**: Plugins execute under their own Linux UID and request only required Android permissions (e.g. `android.permission.INTERNET`). Host grants zero permissions.
- **Dark-Only Theme**: Host UI renders strictly using Bitperfect Flat-Dark design system tokens (no light theme, glass, or neon glows).
- **Capability Gating**: Host invokes only AIDL methods declared in `com.bitperfect.plugin.CAPABILITIES`.

---

## 2. Capabilities Reference (Host API v6)

| Capability Constant | Bitmask | Dec | Purpose |
|---|---|---|---|
| `CAPABILITY_SOURCE` | `1 << 0` | `1` | Stream resolution, catalog browse, search. |
| `CAPABILITY_METADATA` | `1 << 1` | `2` | Supplementary metadata (lyrics, credits). |
| `CAPABILITY_OUTPUT_RENDERER` | `1 << 2` | `4` | External output target (UPnP, DLNA, Cast). |
| `CAPABILITY_UI_TABS` | `1 << 3` | `8` | Sidebar navigation linking to plugin Activity. |
| `CAPABILITY_NOW_PLAYING_EXT` | `1 << 4` | `16` | RemoteViews card in Now Playing screen. |
| `CAPABILITY_SETTINGS` | `1 << 5` | `32` | Settings sub-screen deep-linking to plugin Activity. |
| `CAPABILITY_AUTH` | `1 << 6` | `64` | Access to host credential callbacks (deprecated; use local secure storage). |
| `CAPABILITY_CLOUD_SOURCE` | `1 << 7` | `128` | Cloud storage browser (WebDAV, Nextcloud, S3). |
| `CAPABILITY_PAGES` | `1 << 8` | `256` | Declarative SDUI (`onRenderPage` / `onDispatchAction`). |
| `CAPABILITY_DSP` | `1 << 9` | `512` | Real-time DSP audio filter via `SharedMemory`. |
| `CAPABILITY_RECEIVER` | `1 << 10` | `1024` | Remote-control cast target / receiver session. |

---

## 3. Manifest Declaration (`AndroidManifest.xml`)

**Declare** an exported `Service` responding to `com.bitperfect.plugin.BIND_PLUGIN`:

```xml
<manifest package="com.example.bitperfect.plugin.mysource">
    <uses-permission android:name="android.permission.INTERNET" />
    <application>
        <service
            android:name=".MySourcePlugin"
            android:exported="true">
            <intent-filter>
                <action android:name="com.bitperfect.plugin.BIND_PLUGIN" />
            </intent-filter>

            <!-- MUST equal applicationId -->
            <meta-data
                android:name="com.bitperfect.plugin.PLUGIN_ID"
                android:value="com.example.bitperfect.plugin.mysource" />
            <!-- SOURCE (1) | PAGES (256) = 257 -->
            <meta-data
                android:name="com.bitperfect.plugin.CAPABILITIES"
                android:value="257" />
            <meta-data
                android:name="com.bitperfect.plugin.MIN_HOST_API"
                android:value="6" />
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
                android:name="com.bitperfect.plugin.SETTINGS_ROUTE"
                android:value="settings" />
        </service>
    </application>
</manifest>
```

---

## 4. Implementation Patterns

### Streaming Source Plugin (`BitperfectSourcePlugin`)

**Subclass** `BitperfectSourcePlugin` and override required hooks:

```kotlin
class MySourcePlugin : BitperfectSourcePlugin() {
    override fun capabilities(): Int =
        PluginSdk.CAPABILITY_SOURCE or PluginSdk.CAPABILITY_PAGES

    override fun onResolveStream(
        track: TrackRefDto,
        maxBitDepth: Int,
        maxSampleRate: Int,
    ): StreamDescriptorDto? = StreamDescriptorDto(
        url = "https://stream.example.com/audio.flac",
        headers = mapOf("User-Agent" to "BitperfectPlayer/1.0"),
        encrypted = false, trackKey = null, cipher = null,
        codec = "flac", bitDepth = 16, sampleRate = 44100,
        expiresAtEpochMs = System.currentTimeMillis() + 86_400_000L,
    )

    override fun onRenderPage(
        routeId: String,
        paramsJson: String?,
        pageToken: String?,
    ): PageDescriptorDto? = PageDescriptorDto(
        1, """{ "v": 1, "title": "Catalog", "layout": "DETAIL", "sections": [ ... ] }"""
    )

    override fun onDispatchAction(actionId: String, paramsJson: String?): ActionResultDto =
        ActionResultDto(ActionResultDto.KIND_NONE, "{}")
}
```

### DSP Audio Filter (`BitperfectDspPlugin`)

**Process** frames via Linux `SharedMemory` ring buffer within a 2.0 ms deadline:

```kotlin
// Map memory zero-copy via FileChannel
val bytes = (offer.capacityFrames * offer.format.channels * 4).toLong()
val inMapped = FileInputStream(offer.shmemInFd.fileDescriptor).channel
    .map(FileChannel.MapMode.READ_ONLY, 0, bytes).order(ByteOrder.nativeOrder())
val outMapped = FileOutputStream(offer.shmemOutFd.fileDescriptor).channel
    .map(FileChannel.MapMode.READ_WRITE, 0, bytes).order(ByteOrder.nativeOrder())

val pollFd = StructPollfd().apply {
    fd = offer.notifyInFd.fileDescriptor
    events = OsConstants.POLLIN.toShort()
}

while (!isClosed.get()) {
    if (Os.poll(arrayOf(pollFd), 2) > 0) { // 2.0 ms deadline
        drainEventFd(offer.notifyInFd.fileDescriptor)
        dspEngine.process(inMapped.asFloatBuffer(), outMapped.asFloatBuffer())
        signalEventFd(offer.notifyOutFd.fileDescriptor)
    }
}
```

---

## 5. Verification Checklist

- [ ] **Verify** `PLUGIN_ID` matches `applicationId` character-for-character in `AndroidManifest.xml`.
- [ ] **Confirm** `<intent-filter>` contains `<action android:name="com.bitperfect.plugin.BIND_PLUGIN" />`.
- [ ] **Match** `capabilities()` bitmask with `com.bitperfect.plugin.CAPABILITIES` in `AndroidManifest.xml`.
- [ ] **Audit** codebase to confirm zero commercial or proprietary streaming service names or credentials exist.
- [ ] **Validate** Pages API v1 JSON against limits (payload <= 512 KiB, depth <= 12, sections <= 40).
- [ ] **Ensure** sliders dispatch Binder IPC only on finger release; never per drag frame.
- [ ] **Benchmark** DSP filter loop to guarantee completion within 2.0 ms deadline.
- [ ] **Configure** ProGuard rules to keep DTOs, `PluginPage` serialization models, and plugin service classes.

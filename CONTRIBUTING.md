# Contributing to the Bitperfect Player Plugin SDK

Thank you for contributing to the **Bitperfect Player Plugin SDK**! This document provides a short, crisp guide to the repository architecture, local development workflows, publishing, and contributing guidelines.

---

## 1. SDK Architecture

Bitperfect Player plugins run as independent Android applications (APKs) isolated in separate Linux processes using a zero-trust architecture:

* **`:plugin-aidl`**: Low-level AIDL contracts (`IBitperfectPluginV1`, `IPluginHostCallbackV1`) and Parcelable Data Transfer Objects (`TrackRefDto`, `StreamDescriptorDto`, `BrowseResultDto`, `SearchResultDto`, `DspSessionOfferDto`, etc.).
* **`:plugin-pages`**: High-level Server-Driven UI (Pages API v1) data models (`PluginPage`, `PluginSection`, `PluginItem`, `PluginAction`) and JSON serializers. UI screens are declared as JSON and rendered natively by the host player at **120 FPS**.
* **`:plugin-runtime`**: Base Service classes (`BitperfectSourcePlugin`, `BitperfectDspPlugin`, `BitperfectRendererPlugin`), signature verification, host trust guards (`PluginHostTrust`), and lifecycle handling.
* **`sample-*` modules**: Fully functional, production-ready reference plugins demonstrating audio streaming, browsing, search, Ashmem SharedMemory DSP filtering, and Server-Driven UI.

---

## 2. Local Development & Building

### Prerequisites
* **JDK 17** (Temurin or OpenJDK 17)
* **Android SDK** with Platform API 35+ / compileSdk 37

### Build and Run Unit Tests
To build all SDK libraries and run unit test suites locally:
```bash
./gradlew assembleDebug test
```

### Full Validation Suite
To run full CI checks and assemble debug/release binaries:
```bash
./gradlew check assembleRelease assembleDebug
```

---

## 3. Maven Publishing (`maven-publish`)

The SDK modules `:plugin-aidl`, `:plugin-pages`, and `:plugin-runtime` are published under the group `com.bitperfect.player` at version `6.0.0`.

### Publish to Maven Local
To compile and install the SDK artifacts into your local Maven cache (`~/.m2/repository`):
```bash
./gradlew publishToMavenLocal
```

This installs:
* `com.bitperfect.player:plugin-aidl:6.0.0` (`.aar` and `-sources.jar`)
* `com.bitperfect.player:plugin-pages:6.0.0` (`.aar` and `-sources.jar`)
* `com.bitperfect.player:plugin-runtime:6.0.0` (`.aar` and `-sources.jar`)

### Consuming in an External Plugin
In your external plugin's `settings.gradle.kts` or `build.gradle.kts`:
```kotlin
repositories {
    google()
    mavenCentral()
    mavenLocal() // For local SDK testing
}

dependencies {
    implementation("com.bitperfect.player:plugin-runtime:6.0.0")
    implementation("com.bitperfect.player:plugin-aidl:6.0.0")
    implementation("com.bitperfect.player:plugin-pages:6.0.0")
}
```

---

## 4. Developer Tools & Scripts

The repository includes automation scripts under `scripts/`:

### Scaffold a New Plugin Module (`scripts/create-plugin.sh`)
Quickly generate a production-ready plugin module conforming to the SDK specifications:
```bash
# Non-interactive CLI:
./scripts/create-plugin.sh \
    --id com.example.bitperfect.plugin.flacradio \
    --name FlacRadioPlugin \
    --type source \
    --out my-flac-radio

# Or run interactively:
./scripts/create-plugin.sh
```
Supports plugin types:
* `source`: Audio streaming, catalog browsing, and stream resolution.
* `dsp`: Real-time zero-copy DSP filtering via Linux SharedMemory and eventfd.
* `renderer`: Remote network playback targets.

### Validate Plugin Manifest (`scripts/validate-plugin.sh`)
Verifies that an `AndroidManifest.xml` satisfies Bitperfect Player discovery requirements:
```bash
./scripts/validate-plugin.sh my-flac-radio
```
Checks:
1. `applicationId` / package detection.
2. `com.bitperfect.plugin.PLUGIN_ID` matches `applicationId`.
3. Exported service (`android:exported="true"`) declares `com.bitperfect.plugin.BIND_PLUGIN`.
4. `com.bitperfect.plugin.CAPABILITIES` bitmask exists and is valid.
5. `com.bitperfect.plugin.MIN_HOST_API` >= 4 (recommended: 6).

---

## 5. Working with Sample Plugins

You can install and inspect any sample plugin on an Android device or emulator:

```bash
# Build sample debug APK
./gradlew :sample-network-source:assembleDebug

# Install on connected device
adb install -r sample-network-source/build/outputs/apk/debug/sample-network-source-debug.apk
```

Available samples:
* **`sample-plugin`**: Minimal baseline implementation.
* **`sample-network-source`**: Complete open-source streaming source (FLAC/MP3, SDUI, search, tabs).
* **`sample-dsp-filter`**: Low-latency DSP filter using Ashmem SharedMemory and eventfd sync.

---

## 6. Pull Request Guidelines

1. **Create a Topic Branch**:
   ```bash
   git checkout -b feature/my-improvement
   ```
2. **Verify Before Submitting**:
   Always ensure tests, manifest validation, and builds succeed:
   ```bash
   ./gradlew check assembleRelease assembleDebug
   ./scripts/validate-plugin.sh sample-plugin
   ./scripts/validate-plugin.sh sample-network-source
   ./scripts/validate-plugin.sh sample-dsp-filter
   ```
3. **Commit Messages**:
   Write clear, imperative commit messages (e.g. `feat: add EQ slider preset to sample-dsp-filter`).
4. **Submit PR**:
   Open a pull request against `main`. Ensure all GitHub Actions CI checks pass.

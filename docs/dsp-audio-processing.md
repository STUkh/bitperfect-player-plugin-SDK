# DSP & Out-of-Process Audio Processing Guide

Bitperfect Player supports third-party digital signal processing (DSP) modules via **API Level 5+** and the `CAPABILITY_DSP` capability bit (`512`).

---

## 1. Zero-Copy IPC Architecture

Android's Binder IPC has a global 1 MiB transaction buffer per process. Streaming raw, uncompressed high-resolution PCM (e.g. 192 kHz / 24-bit stereo requiring over 1.15 MB/s, or 384 kHz multichannel) over standard Binder calls would cause transaction overflows, audio dropouts, and unacceptable latency.

To achieve bit-perfect real-time audio processing without compromising stability:
* **Zero PCM over Binder**: Audio samples are never transferred inside Binder IPC payloads.
* **Linux SharedMemory (Ashmem) Ring Buffers**: Audio frames are mapped into shared memory ring buffers accessible by both the Bitperfect host and the plugin process.
* **Low-Latency Signaling (`eventfd`)**: Synchronization between the player audio engine and the plugin occurs via Linux event file descriptors (`eventfd`), bypassing the Android framework overhead.
* **Strict Real-Time Deadline**: DSP stages must process frames within **2.0 milliseconds** to prevent hardware buffer underruns.

---

## 2. Implementing `BitperfectDspPlugin`

To create an out-of-process DSP plugin, extend `BitperfectDspPlugin`:

```kotlin
package com.example.plugin.dsp

import com.bitperfect.player.sdk.plugin.dto.*
import com.bitperfect.player.sdk.plugin.runtime.BitperfectDspPlugin
import com.bitperfect.player.sdk.plugin.runtime.PluginSdk

class ConvolverDspPlugin : BitperfectDspPlugin() {

    override fun capabilities(): Int =
        PluginSdk.CAPABILITY_DSP or PluginSdk.CAPABILITY_PAGES

    override fun onOpenDspSession(offer: DspSessionOfferDto): DspSessionDescriptorDto? {
        val inputFormat = offer.inputFormat
        
        // 1. Verify support for input sample rate and bit depth
        if (inputFormat.sampleRate > 192000) return null

        // 2. Configure internal filter / native DSP worker
        val stageInfo = DspStageInfoDto(
            stageId = "convolver_stage_1",
            displayName = "Impulse Response Convolver",
            outputFormat = inputFormat,
            latencyFrames = 64,
        )

        // 3. Return session descriptor with stage metadata
        return DspSessionDescriptorDto(
            stageInfo = stageInfo,
            sessionBinder = null, // Or custom IPluginDspSessionV1 implementation
        )
    }
}
```

---

## 3. Host Pipeline Integration

When a DSP plugin is enabled by the user:
1. Bitperfect Player opens a session via `openDspSession()`.
2. The plugin stage is inserted into the host audio pipeline.
3. The player's **Signal Path** sheet displays the active DSP stage, showing its stage name, input/output sample rates, and bit depth.
4. If a DSP plugin crashes or exceeds the processing deadline, the host automatically isolates the failure, bypasses the stage without interrupting playback, and logs a warning.

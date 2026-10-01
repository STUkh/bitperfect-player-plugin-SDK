# DSP & Out-of-Process Audio Processing Guide

Bitperfect Player supports third-party digital signal processing (DSP) modules via **API Level 5+** and the `CAPABILITY_DSP` capability bit (`512`).

---

## 1. Zero-Copy IPC Architecture

Android's Binder IPC has a global 1 MiB transaction buffer per process. Streaming raw, uncompressed high-resolution PCM (e.g. 192 kHz / 24-bit stereo requiring over 1.15 MB/s, or 384 kHz multichannel) over standard Binder calls would cause transaction overflows, audio dropouts, and unacceptable latency.

To achieve bit-perfect real-time audio processing without compromising stability:
* **Zero PCM over Binder**: Audio samples are never transferred inside Binder IPC payloads.
* **Linux SharedMemory (Ashmem) Ring Buffers**: Audio frames are mapped into shared memory ring buffers preceded by a 64-byte atomic control header (`DspSharedHeader`).
* **Low-Latency Signaling (`eventfd`)**: Synchronization between the player audio engine and the plugin occurs via Linux event file descriptors (`eventfd`), bypassing the Android framework overhead.
* **Strict Real-Time Deadline**: DSP stages must process frames within **2.0 milliseconds** to prevent hardware buffer underruns.

### Transport Control Header (64 Bytes)
Both input and output shared memory regions are mapped `READ_WRITE` with a 64-byte atomic header at offset 0:
```
Offset 0:  magic (u32, 0x42504453 "BPDS")
Offset 4:  version (u32, 1)
Offset 8:  epoch (u64)
Offset 16: write_pos (u64)
Offset 24: read_pos (u64)
Offset 32: flags (u32, 1=ACTIVE, 2=BYPASS, 4=ERROR)
Offset 36: xruns (u32)
Offset 40..63: padding (24 bytes)
Offset 64+: PCM Float32 audio frame buffer
```
- Input buffer: Host acts as producer (increments `write_pos`), Plugin acts as consumer (advances `read_pos`).
- Output buffer: Plugin acts as producer (increments `write_pos`), Host acts as consumer (advances `read_pos`).
- Audio frame position: `64 + (frame_pos % capacityFrames) * channels * 4` bytes.

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

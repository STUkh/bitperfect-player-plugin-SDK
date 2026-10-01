package com.example.bitperfect.plugin.dspfilter

import com.bitperfect.player.core.plugin.page.*
import com.bitperfect.player.sdk.plugin.dto.*
import com.bitperfect.player.sdk.plugin.runtime.BitperfectDspPlugin
import com.bitperfect.player.sdk.plugin.runtime.PluginSdk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Audiophile DSP Filter Plugin demonstrating:
 * 1. CAPABILITY_DSP and CAPABILITY_PAGES
 * 2. Out-of-process real-time DSP filter stage via Linux SharedMemory (Ashmem) zero-copy ring buffer
 * 3. Linux eventfd file descriptors for low-latency host-plugin synchronization
 * 4. High-precision biquad filter with adjustable pre-amp gain, cutoff frequency, and Q-factor
 * 5. Server-Driven UI (Pages API v1) with SWITCH and SLIDER controls for zero-IPC lag adjustments
 */
class DspFilterPlugin : BitperfectDspPlugin() {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    // Filter configuration parameters (thread-safe and real-time reactive)
    private val isFilterActive = AtomicBoolean(true)
    private val isBypassed = AtomicBoolean(false)
    private val gainDb = AtomicReference(0.0f)
    private val cutoffHz = AtomicReference(1000.0f)
    private val qFactor = AtomicReference(0.7071f)

    // Biquad filter instance
    private val filter = BiquadFilter(
        sampleRate = 44100,
        cutoffHz = cutoffHz.get(),
        qFactor = qFactor.get(),
        gainDb = gainDb.get(),
    )

    // Active DSP sessions indexed by sessionId
    private val activeSessions = ConcurrentHashMap<String, DspSharedMemorySession>()

    override fun capabilities(): Int =
        PluginSdk.CAPABILITY_DSP or PluginSdk.CAPABILITY_PAGES

    /**
     * Negotiates and establishes an out-of-process real-time DSP processing session.
     * Receives SharedMemory and eventfd descriptors from the Bitperfect Player host.
     */
    override fun onOpenDspSession(offer: DspSessionOfferDto): DspSessionDescriptorDto? {
        if (!validateOffer(offer)) {
            return null
        }

        // Configure filter for the incoming stream's sample rate
        filter.update(
            newSampleRate = offer.format.sampleRate,
            newCutoffHz = cutoffHz.get(),
            newQFactor = qFactor.get(),
            newGainDb = gainDb.get(),
        )

        val stageInfo = DspStageInfoDto(
            stageId = "sample_parametric_filter",
            displayName = "Parametric EQ & High-Res Gain Stage",
            outputSampleRate = offer.format.sampleRate,
            latencyFrames = 0, // Direct zero-latency filter topology
            isLengthChanging = false,
        )

        val session = DspSharedMemorySession(
            offer = offer,
            stageInfo = stageInfo,
            filter = filter,
            isBypassedProvider = { !isFilterActive.get() || isBypassed.get() },
        )

        // Store active session and start audio processing worker loop
        activeSessions[offer.sessionId]?.close()
        activeSessions[offer.sessionId] = session
        session.startWorker()

        return DspSessionDescriptorDto(
            stageInfo = stageInfo,
            sessionBinder = session,
        )
    }

    private fun validateOffer(offer: DspSessionOfferDto): Boolean {
        if (offer.sessionId.isBlank()) return false
        if (offer.format.channels != 2) return false // Stereo
        if (offer.format.sampleFormat != DspStreamFormatDto.SAMPLE_FORMAT_FLOAT32) return false
        if (offer.format.sampleRate !in 44100..384000) return false
        if (offer.chunkFrames <= 0 || offer.capacityFrames < offer.chunkFrames) return false
        if (offer.shmemInFd == null || offer.shmemOutFd == null) return false
        if (offer.notifyInFd == null || offer.notifyOutFd == null) return false
        return true
    }

    /**
     * Renders Server-Driven UI settings page via Pages API v1.
     * Host displays this interface in the DSP & Signal Path configuration menu.
     */
    override fun onRenderPage(
        routeId: String,
        paramsJson: String?,
        pageToken: String?,
    ): PageDescriptorDto? {
        val page = when (routeId) {
            "dsp_settings", "settings", "home" -> buildSettingsPage()
            else -> buildSettingsPage()
        }

        val pageJsonString = json.encodeToString(PluginPage.serializer(), page)
        return PageDescriptorDto(
            contractVersion = 1,
            json = pageJsonString,
        )
    }

    /**
     * Dispatches user configuration actions triggered by Server-Driven UI switches and sliders.
     */
    override fun onDispatchAction(actionId: String, paramsJson: String?): ActionResultDto {
        val params = try {
            paramsJson?.let { json.parseToJsonElement(it).jsonObject }
        } catch (_: Exception) {
            null
        }

        return when (actionId) {
            "toggle_active" -> {
                val checked = params?.get("checked")?.jsonPrimitive?.content?.toBoolean() ?: true
                isFilterActive.set(checked)
                ActionResultDto(ActionResultDto.KIND_REFRESH, "")
            }

            "toggle_bypass" -> {
                val checked = params?.get("checked")?.jsonPrimitive?.content?.toBoolean() ?: false
                isBypassed.set(checked)
                ActionResultDto(ActionResultDto.KIND_REFRESH, "")
            }

            "set_gain" -> {
                // Slider value dispatched on touch release for 120 FPS latency-free UI
                val value = params?.get("value")?.jsonPrimitive?.content?.toFloatOrNull() ?: 0.0f
                gainDb.set(value)
                filter.update(newGainDb = value)
                ActionResultDto(ActionResultDto.KIND_NONE, "")
            }

            "set_cutoff" -> {
                val value = params?.get("value")?.jsonPrimitive?.content?.toFloatOrNull() ?: 1000.0f
                cutoffHz.set(value)
                filter.update(newCutoffHz = value)
                ActionResultDto(ActionResultDto.KIND_NONE, "")
            }

            "set_q" -> {
                val value = params?.get("value")?.jsonPrimitive?.content?.toFloatOrNull() ?: 0.7071f
                qFactor.set(value)
                filter.update(newQFactor = value)
                ActionResultDto(ActionResultDto.KIND_NONE, "")
            }

            "reset_defaults" -> {
                gainDb.set(0.0f)
                cutoffHz.set(1000.0f)
                qFactor.set(0.7071f)
                isFilterActive.set(true)
                isBypassed.set(false)
                filter.update(newGainDb = 0.0f, newCutoffHz = 1000.0f, newQFactor = 0.7071f)
                filter.reset()
                ActionResultDto(ActionResultDto.KIND_REFRESH, "")
            }

            else -> ActionResultDto(ActionResultDto.KIND_NONE, "")
        }
    }

    private fun buildSettingsPage(): PluginPage {
        val curGain = gainDb.get()
        val curCutoff = cutoffHz.get()
        val curQ = qFactor.get()
        val active = isFilterActive.get()
        val bypassed = isBypassed.get()

        val latestSession = activeSessions.values.firstOrNull()

        return PluginPage(
            title = "Parametric DSP Filter",
            subtitle = "Zero-copy Linux SharedMemory real-time audio stage",
            layout = "DETAIL",
            sections = listOf(
                PluginSection(
                    id = "activation_switches",
                    kind = "SWITCH",
                    header = PluginSectionHeader(title = "DSP Stage Activation"),
                    items = listOf(
                        PluginItem(
                            id = "switch_active",
                            title = "Enable Parametric Filter Stage",
                            subtitle = "Activate out-of-process digital signal processing in signal path",
                            enabled = active,
                            action = PluginAction(
                                type = "dispatch",
                                actionId = "toggle_active",
                            ),
                        ),
                        PluginItem(
                            id = "switch_bypass",
                            title = "Bit-Perfect Passthrough Bypass",
                            subtitle = "Bypass all biquad calculations without removing stage from pipeline",
                            enabled = bypassed,
                            action = PluginAction(
                                type = "dispatch",
                                actionId = "toggle_bypass",
                            ),
                        ),
                    ),
                ),
                PluginSection(
                    id = "filter_sliders",
                    kind = "SLIDER",
                    header = PluginSectionHeader(title = "Equalizer & Acoustic Tuning"),
                    items = listOf(
                        PluginItem(
                            id = "slider_gain",
                            title = "Pre-Amp Gain (dB)",
                            subtitle = "Digital head-room and level adjustment",
                            value = curGain,
                            minValue = -12.0f,
                            maxValue = 12.0f,
                            step = 0.5f,
                            meta = "${if (curGain > 0) "+" else ""}$curGain dB",
                            action = PluginAction(
                                type = "dispatch",
                                actionId = "set_gain",
                            ),
                        ),
                        PluginItem(
                            id = "slider_cutoff",
                            title = "Low-Pass Cutoff Frequency",
                            subtitle = "Corner frequency for Butterworth acoustic roll-off",
                            value = curCutoff,
                            minValue = 100.0f,
                            maxValue = 20000.0f,
                            step = 50.0f,
                            meta = "${curCutoff.toInt()} Hz",
                            action = PluginAction(
                                type = "dispatch",
                                actionId = "set_cutoff",
                            ),
                        ),
                        PluginItem(
                            id = "slider_q",
                            title = "Filter Resonance (Q-Factor)",
                            subtitle = "Curve sharpness: 0.7071 Butterworth, higher = peak resonance",
                            value = curQ,
                            minValue = 0.5f,
                            maxValue = 5.0f,
                            step = 0.1f,
                            meta = "Q = $curQ",
                            action = PluginAction(
                                type = "dispatch",
                                actionId = "set_q",
                            ),
                        ),
                    ),
                ),
                PluginSection(
                    id = "telemetry",
                    kind = "KEY_VALUE",
                    header = PluginSectionHeader(title = "Real-Time IPC Telemetry"),
                    items = listOf(
                        PluginItem(
                            id = "tel_session",
                            title = "Active Session ID",
                            meta = latestSession?.offer?.sessionId ?: "Idle (Awaiting Playback)",
                        ),
                        PluginItem(
                            id = "tel_rate",
                            title = "Current Sample Rate",
                            meta = latestSession?.let { "${it.offer.format.sampleRate} Hz (Float32)" } ?: "44100 Hz (Default)",
                        ),
                        PluginItem(
                            id = "tel_ipc",
                            title = "Zero-Copy IPC Plane",
                            meta = "Linux SharedMemory (Ashmem) + eventfd",
                        ),
                        PluginItem(
                            id = "tel_latency",
                            title = "Real-Time Processing Deadline",
                            meta = "< 2.0 ms per audio chunk",
                        ),
                    ),
                ),
                PluginSection(
                    id = "actions",
                    kind = "BUTTONS",
                    items = listOf(
                        PluginItem(
                            id = "btn_reset",
                            title = "Reset to Default Parameters",
                            subtitle = "Restore 0 dB gain, 1000 Hz cutoff, and Q = 0.7071",
                            action = PluginAction(
                                type = "dispatch",
                                actionId = "reset_defaults",
                            ),
                        ),
                    ),
                ),
            ),
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        activeSessions.values.forEach { it.close() }
        activeSessions.clear()
    }
}

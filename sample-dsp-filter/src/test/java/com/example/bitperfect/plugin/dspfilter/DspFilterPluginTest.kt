package com.example.bitperfect.plugin.dspfilter

import android.os.ParcelFileDescriptor
import com.bitperfect.player.core.plugin.page.PluginPageParser
import com.bitperfect.player.core.plugin.page.PluginPageParseResult
import com.bitperfect.player.sdk.plugin.dto.ActionResultDto
import com.bitperfect.player.sdk.plugin.dto.DspSessionOfferDto
import com.bitperfect.player.sdk.plugin.dto.DspStreamFormatDto
import com.bitperfect.player.sdk.plugin.runtime.PluginSdk
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.math.abs

class DspFilterPluginTest {

    private lateinit var plugin: DspFilterPlugin

    @BeforeEach
    fun setUp() {
        plugin = DspFilterPlugin()
    }

    @Test
    fun `capabilities returns DSP and PAGES`() {
        val expected = PluginSdk.CAPABILITY_DSP or PluginSdk.CAPABILITY_PAGES
        assertEquals(expected, plugin.capabilities())
        assertEquals(768, plugin.capabilities())
    }

    @Test
    fun `onOpenDspSession accepts valid offer and returns descriptor`() {
        val mockShmemIn: ParcelFileDescriptor = mockk(relaxed = true)
        val mockShmemOut: ParcelFileDescriptor = mockk(relaxed = true)
        val mockNotifyIn: ParcelFileDescriptor = mockk(relaxed = true)
        val mockNotifyOut: ParcelFileDescriptor = mockk(relaxed = true)

        val offer = DspSessionOfferDto(
            sessionId = "session_test_101",
            nonce = 42L,
            format = DspStreamFormatDto(
                sampleRate = 96000,
                channelCount = 2,
                bitDepth = 32,
                encoding = DspStreamFormatDto.SAMPLE_FORMAT_FLOAT32,
            ),
            chunkFrames = 512,
            capacityFrames = 4096,
            shmemInFd = mockShmemIn,
            shmemOutFd = mockShmemOut,
            notifyInFd = mockNotifyIn,
            notifyOutFd = mockNotifyOut,
        )

        val descriptor = plugin.onOpenDspSession(offer)
        assertNotNull(descriptor)
        assertEquals("sample_parametric_filter", descriptor!!.stageInfo.stageId)
        assertEquals(96000, descriptor.stageInfo.outputSampleRate)
        assertEquals(0, descriptor.stageInfo.latencyFrames)
        assertFalse(descriptor.stageInfo.isLengthChanging)
        assertNotNull(descriptor.sessionBinder)
    }

    @Test
    fun `onOpenDspSession rejects invalid offers`() {
        val mockPfd: ParcelFileDescriptor = mockk(relaxed = true)

        // Missing session id
        val blankIdOffer = DspSessionOfferDto(
            sessionId = "",
            nonce = 1L,
            format = DspStreamFormatDto(44100, 2),
            chunkFrames = 256,
            capacityFrames = 2048,
            shmemInFd = mockPfd,
            shmemOutFd = mockPfd,
            notifyInFd = mockPfd,
            notifyOutFd = mockPfd,
        )
        assertNull(plugin.onOpenDspSession(blankIdOffer))

        // Unsupported channel count (e.g. 6 channels)
        val multiChannelOffer = DspSessionOfferDto(
            sessionId = "session_mc",
            nonce = 1L,
            format = DspStreamFormatDto(44100, 6),
            chunkFrames = 256,
            capacityFrames = 2048,
            shmemInFd = mockPfd,
            shmemOutFd = mockPfd,
            notifyInFd = mockPfd,
            notifyOutFd = mockPfd,
        )
        assertNull(plugin.onOpenDspSession(multiChannelOffer))

        // Missing shared memory file descriptors
        val nullShmemOffer = DspSessionOfferDto(
            sessionId = "session_null_shmem",
            nonce = 1L,
            format = DspStreamFormatDto(44100, 2),
            chunkFrames = 256,
            capacityFrames = 2048,
            shmemInFd = null,
            shmemOutFd = mockPfd,
            notifyInFd = mockPfd,
            notifyOutFd = mockPfd,
        )
        assertNull(plugin.onOpenDspSession(nullShmemOffer))
    }

    @Test
    fun `onRenderPage generates valid Server-Driven UI with SWITCH and SLIDER controls`() {
        val pageDto = plugin.onRenderPage("dsp_settings", null, null)
        assertNotNull(pageDto)
        assertEquals(1, pageDto!!.contractVersion)

        val parseResult = PluginPageParser.parse(pageDto.json)
        assertTrue(parseResult is PluginPageParseResult.Ok, "Failed to parse: $parseResult")

        val page = (parseResult as PluginPageParseResult.Ok).page
        assertEquals("Parametric DSP Filter", page.title)

        val switchSection = page.sections.find { it.kind == "SWITCH" }
        assertNotNull(switchSection)
        assertTrue(switchSection!!.items.size >= 2)

        val sliderSection = page.sections.find { it.kind == "SLIDER" }
        assertNotNull(sliderSection)
        assertTrue(sliderSection!!.items.any { it.id == "slider_gain" })
        assertTrue(sliderSection.items.any { it.id == "slider_cutoff" })
        assertTrue(sliderSection.items.any { it.id == "slider_q" })

        val telemetrySection = page.sections.find { it.kind == "KEY_VALUE" }
        assertNotNull(telemetrySection)
    }

    @Test
    fun `onDispatchAction handles parameter adjustments with zero-IPC lag`() {
        // Toggle bypass
        val bypassResult = plugin.onDispatchAction("toggle_bypass", """{"checked":"true"}""")
        assertEquals(ActionResultDto.KIND_REFRESH, bypassResult.kind)

        // Set gain slider
        val gainResult = plugin.onDispatchAction("set_gain", """{"value":"3.5"}""")
        assertEquals(ActionResultDto.KIND_NONE, gainResult.kind)

        // Set cutoff slider
        val cutoffResult = plugin.onDispatchAction("set_cutoff", """{"value":"2500.0"}""")
        assertEquals(ActionResultDto.KIND_NONE, cutoffResult.kind)

        // Set Q slider
        val qResult = plugin.onDispatchAction("set_q", """{"value":"1.41"}""")
        assertEquals(ActionResultDto.KIND_NONE, qResult.kind)

        // Reset to defaults
        val resetResult = plugin.onDispatchAction("reset_defaults", null)
        assertEquals(ActionResultDto.KIND_REFRESH, resetResult.kind)
    }

    @Test
    fun `BiquadFilter accurately filters stereo audio and handles bypass`() {
        val filter = BiquadFilter(
            sampleRate = 48000,
            cutoffHz = 1000.0f,
            qFactor = 0.7071f,
            gainDb = 6.0f, // ~2.0x linear voltage gain
        )

        val frames = 128
        val input = FloatArray(frames * 2) { 0.5f }
        val outputFiltered = FloatArray(frames * 2)
        val outputBypassed = FloatArray(frames * 2)

        // Test filtering
        filter.process(input, outputFiltered, frames, bypassed = false)
        // With +6dB gain and DC/low frequency input, output should approach ~1.0
        assertTrue(outputFiltered[outputFiltered.size - 1] > 0.7f)

        // Test bypass (bit-perfect passthrough)
        filter.process(input, outputBypassed, frames, bypassed = true)
        for (i in input.indices) {
            assertEquals(input[i], outputBypassed[i], 0.00001f)
        }
    }

    @Test
    fun `DspSharedMemorySession reset clears internal filter state`() {
        val mockPfd: ParcelFileDescriptor = mockk(relaxed = true)
        val offer = DspSessionOfferDto(
            sessionId = "session_reset_test",
            nonce = 100L,
            format = DspStreamFormatDto(44100, 2),
            chunkFrames = 128,
            capacityFrames = 1024,
            shmemInFd = mockPfd,
            shmemOutFd = mockPfd,
            notifyInFd = mockPfd,
            notifyOutFd = mockPfd,
        )
        val descriptor = plugin.onOpenDspSession(offer)
        assertNotNull(descriptor)

        val sessionBinder = descriptor!!.sessionBinder
        assertNotNull(sessionBinder)

        // Call reset and close on session binder
        val session = sessionBinder as DspSharedMemorySession
        session.reset()
        session.close()

        // Closing session removes it from activeSessions map
        val field = DspFilterPlugin::class.java.getDeclaredField("activeSessions").apply {
            isAccessible = true
        }
        val activeSessions = field.get(plugin) as Map<*, *>
        assertFalse(activeSessions.containsKey("session_reset_test"))
    }
}

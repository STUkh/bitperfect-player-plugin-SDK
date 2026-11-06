package com.bitperfect.player.sdk.plugin.runtime

import com.bitperfect.player.sdk.plugin.IBitperfectPluginV1
import com.bitperfect.player.sdk.plugin.dto.ActionResultDto
import com.bitperfect.player.sdk.plugin.dto.PageDescriptorDto
import com.bitperfect.player.sdk.plugin.dto.StreamDescriptorDto
import com.bitperfect.player.sdk.plugin.dto.TrackRefDto
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

@Timeout(10, unit = TimeUnit.SECONDS)
class PageRequestTest {

    private class Probe : BitperfectSourcePlugin() {
        var page: PageRequest? = null
        var action: ActionRequest? = null

        override fun onResolveStream(track: TrackRefDto, maxBitDepth: Int, maxSampleRate: Int): StreamDescriptorDto? = null

        override suspend fun onRenderPageRequest(request: PageRequest): PageDescriptorDto {
            page = request
            return PageDescriptorDto.pack("{}", 1)
        }

        override suspend fun onDispatchActionRequest(request: ActionRequest): ActionResultDto {
            action = request
            return ActionResultDto(ActionResultDto.KIND_NONE, "{}")
        }
    }

    private val params = """{"_bp_request_id":"r-1","_bp_host_transport":"tiered","id":"42","n":7,"nested":{"a":1}}"""

    @Test
    fun `render hook gets params without any _bp key and the id apart`() {
        val plugin = Probe()
        assertNotNull((plugin.onBind(null) as IBitperfectPluginV1).renderPage("album", params, "50"))

        val request = plugin.page!!
        assertEquals("album", request.routeId)
        assertEquals("50", request.pageToken)
        assertEquals("r-1", request.requestId)
        assertEquals(setOf("id", "n", "nested"), request.params.keys)
        assertEquals("""{"a":1}""", request.params["nested"])
    }

    @Test
    fun `dispatch hook gets params without any _bp key`() {
        val plugin = Probe()
        (plugin.onBind(null) as IBitperfectPluginV1).dispatchAction("play", params)

        assertEquals(setOf("id", "n", "nested"), plugin.action!!.params.keys)
        assertEquals("r-1", plugin.action!!.requestId)
    }

    @Test
    fun `legacy suspend hook still receives the raw json`() {
        val legacy = object : BitperfectSourcePlugin() {
            var raw: String? = null
            override fun onResolveStream(track: TrackRefDto, maxBitDepth: Int, maxSampleRate: Int): StreamDescriptorDto? = null
            override suspend fun onRenderPageSuspend(routeId: String, paramsJson: String?, pageToken: String?): PageDescriptorDto {
                raw = paramsJson
                return PageDescriptorDto.pack("{}", 1)
            }
        }
        (legacy.onBind(null) as IBitperfectPluginV1).renderPage("home", params, null)
        assertEquals(params, legacy.raw)
    }

    @Test
    fun `toParamsMap handles null blank and malformed input`() {
        assertEquals(emptyMap<String, String>(), (null as String?).toParamsMap())
        assertEquals(emptyMap<String, String>(), " ".toParamsMap())
        assertEquals(emptyMap<String, String>(), "[1,2]".toParamsMap())
        assertEquals(mapOf("a" to "x"), """{"a":"x","_bp_k":"y"}""".toParamsMap())
    }

    @Test
    fun `offset token boundaries`() {
        assertEquals(0, OffsetPageToken.parse(null))
        assertEquals(0, OffsetPageToken.parse(""))
        assertEquals(0, OffsetPageToken.parse("abc"))
        assertEquals(0, OffsetPageToken.parse("-5"))
        assertEquals(10, OffsetPageToken.parse("10", max = 100))
        assertEquals(100, OffsetPageToken.parse("100", max = 100))
        assertEquals(100, OffsetPageToken.parse("101", max = 100))
        assertEquals(Int.MAX_VALUE, OffsetPageToken.parse(Int.MAX_VALUE.toString()))
        assertEquals(0, OffsetPageToken.parse("99999999999", max = 100))
    }

    @Test
    fun `next page token boundaries`() {
        assertEquals("50", OffsetPageToken.nextPageToken(0, 50, null))
        assertEquals("50", OffsetPageToken.nextPageToken(0, 50, 51))
        assertNull(OffsetPageToken.nextPageToken(0, 50, 50))
        assertNull(OffsetPageToken.nextPageToken(0, 0, 100))
        assertNull(OffsetPageToken.nextPageToken(50, 10, 60))
        assertNull(OffsetPageToken.nextPageToken(Int.MAX_VALUE - 1, 5, null))
        assertEquals(Int.MAX_VALUE.toString(), OffsetPageToken.nextPageToken(Int.MAX_VALUE - 5, 5, null))
    }

    @Test
    fun `invalidate events keep the wire shape`() {
        assertEquals("""{"type":"pages.invalidate","routes":"*"}""", PageEvents.invalidateAll())
        assertEquals("""{"type":"pages.invalidate","routes":["home","library"]}""", PageEvents.invalidate(listOf("home", "library")))
    }
}

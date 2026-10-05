package com.example.bitperfect.plugin.networksource

import com.bitperfect.player.core.plugin.page.PluginPageParser
import com.bitperfect.player.core.plugin.page.PluginPageParseResult
import com.bitperfect.player.sdk.plugin.dto.ActionResultDto
import com.bitperfect.player.sdk.plugin.dto.TrackRefDto
import com.bitperfect.player.sdk.plugin.runtime.PluginSdk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class NetworkSourcePluginTest {

    private lateinit var plugin: NetworkSourcePlugin

    @BeforeEach
    fun setUp() {
        plugin = NetworkSourcePlugin()
    }

    @Test
    fun `capabilities returns SOURCE, UI_TABS, and PAGES`() {
        val expected = PluginSdk.CAPABILITY_SOURCE or PluginSdk.CAPABILITY_UI_TABS or PluginSdk.CAPABILITY_PAGES
        assertEquals(expected, plugin.capabilities())
        assertEquals(265, plugin.capabilities())
    }

    @Test
    fun `onResolveStream resolves FLAC stream with headers and expiration`() {
        val trackRef = TrackRefDto("com.example.bitperfect.plugin.networksource", "rp_main", "rp_main", "Radio Paradise", "Rock", 0L)
        val descriptor = plugin.onResolveStream(trackRef, maxBitDepth = 24, maxSampleRate = 192000)

        assertNotNull(descriptor)
        assertEquals("https://stream.radioparadise.com/flac", descriptor!!.url)
        assertEquals("flac", descriptor.codec)
        assertEquals(16, descriptor.bitDepth)
        assertEquals(44100, descriptor.sampleRate)
        assertFalse(descriptor.encrypted)
        assertNull(descriptor.trackKey)

        // Verify headers
        assertTrue(descriptor.headers.containsKey("User-Agent"))
        assertTrue(descriptor.headers.containsKey("Accept"))
        assertEquals("1", descriptor.headers["Icy-MetaData"])

        // Expiration must be in the future
        assertTrue(descriptor.expiresAtEpochMs > System.currentTimeMillis())
    }

    @Test
    fun `onResolveStream falls back to MP3 when FLAC is not available`() {
        val trackRef = TrackRefDto("com.example.bitperfect.plugin.networksource", "soma_groovesalad", "soma_groovesalad", "Groove Salad", "Ambient", 0L)
        val descriptor = plugin.onResolveStream(trackRef, maxBitDepth = 24, maxSampleRate = 192000)

        assertNotNull(descriptor)
        assertEquals("https://ice1.somafm.com/groovesalad-256-mp3", descriptor!!.url)
        assertEquals("mp3", descriptor.codec)
    }

    @Test
    fun `onResolveStream returns null for unknown track`() {
        val trackRef = TrackRefDto("com.example.bitperfect.plugin.networksource", "non_existent_id", null, "Unknown", "Unknown", 0L)
        val descriptor = plugin.onResolveStream(trackRef, maxBitDepth = 24, maxSampleRate = 192000)
        assertNull(descriptor)
    }

    @Test
    fun `onRenderPage catalog featured produces valid PluginPage schema`() {
        val pageDto = plugin.onRenderPage("catalog", """{"tab":"featured"}""", null)
        assertNotNull(pageDto)
        assertEquals(1, pageDto!!.baseVersion)

        val parseResult = PluginPageParser.parse(pageDto.json)
        assertTrue(parseResult is PluginPageParseResult.Ok, "Failed to parse: $parseResult")

        val page = (parseResult as PluginPageParseResult.Ok).page
        assertEquals("Open Audio Catalog", page.title)
        assertNotNull(page.tabs)
        assertEquals("featured", page.tabs!!.selectedId)
        assertTrue(page.sections.isNotEmpty())
    }

    @Test
    fun `onRenderPage catalog genres produces genre chips and grid`() {
        val pageDto = plugin.onRenderPage("catalog", """{"tab":"genres","genre":"Classical"}""", null)
        assertNotNull(pageDto)

        val parseResult = PluginPageParser.parse(pageDto!!.json)
        assertTrue(parseResult is PluginPageParseResult.Ok)

        val page = (parseResult as PluginPageParseResult.Ok).page
        val chipsSection = page.sections.find { it.kind == "CHIPS" }
        assertNotNull(chipsSection)
        assertEquals("Classical", chipsSection!!.selectedId)
    }

    @Test
    fun `onRenderPage catalog stations handles pagination with nextPageToken`() {
        val pageDto = plugin.onRenderPage("catalog", """{"tab":"stations"}""", "page_1")
        assertNotNull(pageDto)

        val parseResult = PluginPageParser.parse(pageDto!!.json)
        assertTrue(parseResult is PluginPageParseResult.Ok)

        val page = (parseResult as PluginPageParseResult.Ok).page
        val listSection = page.sections.find { it.kind == "LIST" }
        assertNotNull(listSection)
        assertEquals(5, listSection!!.items.size)
        assertEquals("page_2", listSection.nextPageToken)
    }

    @Test
    fun `onRenderPage station detail generates hero and telemetry`() {
        val pageDto = plugin.onRenderPage("station_detail", """{"stationId":"venice_classic"}""", null)
        assertNotNull(pageDto)

        val parseResult = PluginPageParser.parse(pageDto!!.json)
        assertTrue(parseResult is PluginPageParseResult.Ok)

        val page = (parseResult as PluginPageParseResult.Ok).page
        assertEquals("Venice Classic Radio", page.title)
        assertNotNull(page.hero)
        assertEquals("Venice Classic Radio", page.hero!!.title)
        val telemetrySection = page.sections.find { it.kind == "KEY_VALUE" }
        assertNotNull(telemetrySection)
    }

    @Test
    fun `onRenderPage settings includes SWITCH and SLIDER controls`() {
        val pageDto = plugin.onRenderPage("settings", null, null)
        assertNotNull(pageDto)

        val parseResult = PluginPageParser.parse(pageDto!!.json)
        assertTrue(parseResult is PluginPageParseResult.Ok)

        val page = (parseResult as PluginPageParseResult.Ok).page
        val switchSection = page.sections.find { it.kind == "SWITCH" }
        val sliderSection = page.sections.find { it.kind == "SLIDER" }
        assertNotNull(switchSection)
        assertNotNull(sliderSection)
    }

    @Test
    fun `onDispatchAction handles favorite and preferences correctly`() {
        // Toggle favorite
        val favResult = plugin.onDispatchAction("toggle_favorite", """{"stationId":"rp_main"}""")
        assertEquals(ActionResultDto.KIND_TOAST, favResult.kind)
        assertTrue(favResult.json?.contains("Added to Favorites") == true)

        val unfavResult = plugin.onDispatchAction("toggle_favorite", """{"stationId":"rp_main"}""")
        assertEquals(ActionResultDto.KIND_TOAST, unfavResult.kind)
        assertTrue(unfavResult.json?.contains("Removed from Favorites") == true)

        // Set FLAC preference
        val flacResult = plugin.onDispatchAction("set_flac_pref", """{"checked":"false"}""")
        assertEquals(ActionResultDto.KIND_REFRESH, flacResult.kind)

        // Verify FLAC preference now resolves MP3 for rp_main
        val descriptor = plugin.onResolveStream(TrackRefDto("com.example.bitperfect.plugin.networksource", "rp_main", "rp_main", "Radio Paradise", "Rock", 0L), 24, 192000)
        assertNotNull(descriptor)
        assertEquals("mp3", descriptor!!.codec)

        // Set buffer depth
        val bufferResult = plugin.onDispatchAction("set_buffer_depth", """{"value":"8.0"}""")
        assertEquals(ActionResultDto.KIND_NONE, bufferResult.kind)
    }

    @Test
    fun `onSearch filters stations by title and genre`() {
        val searchResult = plugin.onSearch("Classical", null)
        assertTrue(searchResult.tracks.isNotEmpty())
        assertTrue(searchResult.tracks.any { it.title.contains("Venice") || it.title.contains("Goldberg") })
    }

    @Test
    fun `onBrowse navigates root and genre folders`() {
        val rootBrowse = plugin.onBrowse(null, null)
        assertEquals(3, rootBrowse.items.size)

        val genreBrowse = plugin.onBrowse("folder:genres", null)
        assertTrue(genreBrowse.items.size >= 3)
    }

    @Test
    fun `onParseUrl matches streaming url`() {
        val trackRef = plugin.onParseUrl("https://stream.radioparadise.com/flac")
        assertNotNull(trackRef)
        assertEquals("rp_main", trackRef!!.trackId)
    }
}

package com.bitperfect.player.core.plugin.page

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PluginPageParserTest {

    private val sampleJson = """
        { "v": 1, "title": "Sample", "subtitle": null,
          "search": { "placeholder": "Search Sample", "routeId": "search", "paramKey": "q", "minChars": 2 },
          "sections": [
            { "id": "recent", "kind": "SHELF", "header": { "title": "Recently played", "action": null },
              "items": [ { "id": "t1", "title": "Song", "subtitle": "Artist", "image": { "url": "https://x", "shape": "SQUARE" },
                           "badge": "Hi-Res", "enabled": true,
                           "action": { "type": "navigate", "routeId": "album", "params": { "id": "123" } },
                           "track": { "trackId": "1", "albumId": "123", "title": "Song", "artist": "A", "album": "B",
                                      "durationMs": 1000, "artworkUrl": null, "quality": null } } ],
              "nextPageToken": null } ],
          "empty": { "title": "Nothing here", "message": null } }
    """.trimIndent()

    @Test
    fun parsesSample() {
        val result = PluginPageParser.parse(sampleJson)
        assertIs<PluginPageParseResult.Ok>(result)
        assertEquals("Sample", result.page.title)
        assertEquals(1, result.page.sections.size)
        val section = result.page.sections.single()
        assertEquals("SHELF", section.kind)
        val item = section.items.single()
        assertEquals("navigate", item.action?.type)
        assertEquals("album", item.action?.routeId)
        assertEquals("1", item.track?.trackId)
    }

    @Test
    fun unknownTopLevelFieldIsIgnored() {
        val result = PluginPageParser.parse(
            """{ "v": 1, "title": "T", "extra": "nope" }""",
        )
        assertIs<PluginPageParseResult.Ok>(result)
        assertEquals("T", result.page.title)
    }

    @Test
    fun unknownSectionKindIsDropped() {
        val result = PluginPageParser.parse(page(sections = section(kind = "CAROUSEL")))
        assertIs<PluginPageParseResult.Ok>(result)
        assertTrue(result.page.sections.isEmpty())
    }

    @Test
    fun rejectedPresentActionMarksItemRejected() {
        val result = PluginPageParser.parse(
            page(sections = section(items = item(action = """{ "type": "smash" }"""))),
        )
        assertIs<PluginPageParseResult.Ok>(result)
        val parsed = result.page.sections.single().items.single()
        assertNull(parsed.action)
        assertTrue(parsed.actionRejected)
    }

    @Test
    fun absentActionRetainsDirectPlayEligibility() {
        val result = PluginPageParser.parse(
            page(sections = section(items = """{ "id": "i", "title": "I", "track": { "trackId": "t", "title": "T", "artist": "A" } }""")),
        )
        assertIs<PluginPageParseResult.Ok>(result)
        val parsed = result.page.sections.single().items.single()
        assertNull(parsed.action)
        assertFalse(parsed.actionRejected)
        assertEquals("t", parsed.track?.trackId)
    }

    @Test
    fun sizeOverLimitIsError() {
        val hugeTitle = "x".repeat(PluginPageLimits.MAX_JSON_BYTES + 1)
        val result = PluginPageParser.parse(page(title = hugeTitle))
        assertIs<PluginPageParseResult.Error>(result)
    }

    @Test
    fun sizeAtLimitParses() {
        val overhead = page(title = "").encodeToByteArray().size
        val budget = PluginPageLimits.MAX_JSON_BYTES - overhead
        val title = "x".repeat(maxOf(budget, 0))
        val result = PluginPageParser.parse(page(title = title))
        assertIs<PluginPageParseResult.Ok>(result)
    }

    @Test
    fun depthOverLimitIsError() {
        val deep = nestedArray(PluginPageLimits.MAX_DEPTH + 4)
        val result = PluginPageParser.parse(page(title = "T", extraTrailing = deep))
        assertIs<PluginPageParseResult.Error>(result)
    }

    @Test
    fun depthAtLimitParses() {
        // outer object contributes depth 1, so a "pad" array nest of MAX_DEPTH - 1 stays at the cap.
        val atLimit = nestedArray(PluginPageLimits.MAX_DEPTH - 1)
        val result = PluginPageParser.parse(page(title = "T", extraTrailing = atLimit))
        assertIs<PluginPageParseResult.Ok>(result)
    }

    @Test
    fun sectionsOverLimitAreTruncated() {
        val sections = (0 until PluginPageLimits.MAX_SECTIONS + 5).joinToString(",") {
            section(id = "s$it", kind = "LIST")
        }
        val result = PluginPageParser.parse(page(sections = sections))
        assertIs<PluginPageParseResult.Ok>(result)
        assertEquals(PluginPageLimits.MAX_SECTIONS, result.page.sections.size)
    }

    @Test
    fun itemsOverLimitAreTruncated() {
        val items = (0 until PluginPageLimits.MAX_ITEMS_PER_SECTION + 5).joinToString(",") {
            item(id = "i$it")
        }
        val result = PluginPageParser.parse(page(sections = section(items = items)))
        assertIs<PluginPageParseResult.Ok>(result)
        assertEquals(PluginPageLimits.MAX_ITEMS_PER_SECTION, result.page.sections.single().items.size)
    }

    @Test
    fun tracksOverLimitAreTruncated() {
        val tracks = (0 until PluginPageLimits.MAX_TRACKS_PER_ACTION + 5).joinToString(",") { track(id = "t$it") }
        val action = """{ "type": "play", "tracks": [ $tracks ] }"""
        val result = PluginPageParser.parse(page(sections = section(items = item(action = action))))
        assertIs<PluginPageParseResult.Ok>(result)
        assertEquals(PluginPageLimits.MAX_TRACKS_PER_ACTION, result.page.sections.single().items.single().action?.tracks?.size)
    }

    @Test
    fun textOverLimitIsTruncated() {
        val longTitle = "y".repeat(PluginPageLimits.MAX_TEXT + 50)
        val result = PluginPageParser.parse(page(sections = section(items = item(title = longTitle))))
        assertIs<PluginPageParseResult.Ok>(result)
        assertEquals(PluginPageLimits.MAX_TEXT, result.page.sections.single().items.single().title.length)
    }

    @Test
    fun httpImageUrlIsCleared() {
        val result = PluginPageParser.parse(
            page(sections = section(items = item(imageUrl = "http://insecure"))),
        )
        assertIs<PluginPageParseResult.Ok>(result)
        assertNull(result.page.sections.single().items.single().image)
    }

    @Test
    fun overlongImageUrlIsCleared() {
        val longUrl = "https://" + "a".repeat(PluginPageLimits.MAX_URL)
        val result = PluginPageParser.parse(page(sections = section(items = item(imageUrl = longUrl))))
        assertIs<PluginPageParseResult.Ok>(result)
        assertNull(result.page.sections.single().items.single().image)
    }

    @Test
    fun trackArtworkRequiresBoundedHttpsUrl() {
        val overlongUrl = "https://" + "a".repeat(PluginPageLimits.MAX_URL)
        val tracks = """
            { "trackId": "http", "title": "T", "artist": "A", "artworkUrl": "http://insecure" },
            { "trackId": "long", "title": "T", "artist": "A", "artworkUrl": "$overlongUrl" },
            { "trackId": "https", "title": "T", "artist": "A", "artworkUrl": "https://secure" }
        """.trimIndent()
        val action = """{ "type": "play", "tracks": [ $tracks ] }"""
        val result = PluginPageParser.parse(page(sections = section(items = item(action = action))))
        assertIs<PluginPageParseResult.Ok>(result)
        val parsed = result.page.sections.single().items.single().action?.tracks
        assertNull(parsed?.get(0)?.artworkUrl)
        assertNull(parsed?.get(1)?.artworkUrl)
        assertEquals("https://secure", parsed?.get(2)?.artworkUrl)
    }

    @Test
    fun invalidRouteIdDropsAction() {
        val action = """{ "type": "navigate", "routeId": "bad route!" }"""
        val result = PluginPageParser.parse(page(sections = section(items = item(action = action))))
        assertIs<PluginPageParseResult.Ok>(result)
        assertNull(result.page.sections.single().items.single().action)
    }

    @Test
    fun validRouteIdKeepsAction() {
        val action = """{ "type": "navigate", "routeId": "album_1.2-3" }"""
        val result = PluginPageParser.parse(page(sections = section(items = item(action = action))))
        assertIs<PluginPageParseResult.Ok>(result)
        assertEquals("album_1.2-3", result.page.sections.single().items.single().action?.routeId)
    }

    @Test
    fun malformedJsonIsError() {
        val result = PluginPageParser.parse("{ not json")
        assertIs<PluginPageParseResult.Error>(result)
    }

    @Test
    fun formatTierAllowlistInItemAndTrack() {
        for (tier in listOf("HI_RES", "LOSSLESS", "DSD", "LOSSY")) {
            val json = page(sections = section(items = """{ "id": "i", "title": "I", "formatTier": "$tier", "track": { "trackId": "t", "title": "T", "artist": "A", "formatTier": "$tier" } }"""))
            val result = PluginPageParser.parse(json)
            assertIs<PluginPageParseResult.Ok>(result)
            val item = result.page.sections.single().items.single()
            assertEquals(tier, item.formatTier)
            assertEquals(tier, item.track?.formatTier)
        }
    }

    @Test
    fun unknownFormatTierFallsBackToNull() {
        val json = page(sections = section(items = """{ "id": "i", "title": "I", "formatTier": "UNKNOWN_TIER", "track": { "trackId": "t", "title": "T", "artist": "A", "formatTier": "SUPER_AUDIO" } }"""))
        val result = PluginPageParser.parse(json)
        assertIs<PluginPageParseResult.Ok>(result)
        val item = result.page.sections.single().items.single()
        assertNull(item.formatTier)
        assertNull(item.track?.formatTier)
    }

    @Test
    fun discNumberCoercedInItemAndTrack() {
        // Valid discNumber
        val validJson = page(sections = section(items = """{ "id": "i", "title": "I", "discNumber": 2, "track": { "trackId": "t", "title": "T", "artist": "A", "discNumber": 3 } }"""))
        val validResult = PluginPageParser.parse(validJson)
        assertIs<PluginPageParseResult.Ok>(validResult)
        val validItem = validResult.page.sections.single().items.single()
        assertEquals(2, validItem.discNumber)
        assertEquals(3, validItem.track?.discNumber)

        // Disc number < 1 coerced to 1
        val lowJson = page(sections = section(items = """{ "id": "i", "title": "I", "discNumber": 0, "track": { "trackId": "t", "title": "T", "artist": "A", "discNumber": -5 } }"""))
        val lowResult = PluginPageParser.parse(lowJson)
        assertIs<PluginPageParseResult.Ok>(lowResult)
        val lowItem = lowResult.page.sections.single().items.single()
        assertEquals(1, lowItem.discNumber)
        assertEquals(1, lowItem.track?.discNumber)

        // Disc number > 99 coerced to 99
        val highJson = page(sections = section(items = """{ "id": "i", "title": "I", "discNumber": 150, "track": { "trackId": "t", "title": "T", "artist": "A", "discNumber": 100 } }"""))
        val highResult = PluginPageParser.parse(highJson)
        assertIs<PluginPageParseResult.Ok>(highResult)
        val highItem = highResult.page.sections.single().items.single()
        assertEquals(99, highItem.discNumber)
        assertEquals(99, highItem.track?.discNumber)
    }

    @Test
    fun parseItemAndParseTrackHelpers() {
        val item = PluginItem(id = "i", title = "Title", formatTier = "HI_RES", discNumber = 0)
        val parsedItem = PluginPageParser.parseItem(item)
        assertEquals("HI_RES", parsedItem.formatTier)
        assertEquals(1, parsedItem.discNumber)

        val track = PluginTrack(trackId = "t", title = "T", artist = "A", formatTier = "INVALID", discNumber = 200)
        val parsedTrack = PluginPageParser.parseTrack(track)
        assertNull(parsedTrack.formatTier)
        assertEquals(99, parsedTrack.discNumber)
    }

    @Test
    fun sliderFieldsParsedAndSanitized() {
        val item = PluginItem(
            id = "s",
            title = "Gain",
            value = 0.75f,
            minValue = -10f,
            maxValue = 10f,
            step = 0.5f,
        )
        val parsed = PluginPageParser.parseItem(item)
        assertEquals(0.75f, parsed.value)
        assertEquals(-10f, parsed.minValue)
        assertEquals(10f, parsed.maxValue)
        assertEquals(0.5f, parsed.step)

        val invalidItem = PluginItem(
            id = "s2",
            title = "Invalid",
            value = Float.NaN,
            minValue = Float.NEGATIVE_INFINITY,
            maxValue = Float.POSITIVE_INFINITY,
            step = -1f,
        )
        val sanitizedInvalid = PluginPageParser.parseItem(invalidItem)
        assertNull(sanitizedInvalid.value)
        assertNull(sanitizedInvalid.minValue)
        assertNull(sanitizedInvalid.maxValue)
        assertNull(sanitizedInvalid.step)
    }

    // --- fixture builders ---

    private fun page(title: String = "T", sections: String = "", extraTrailing: String = ""): String =
        """{ "v": 1, "title": "$title", "sections": [ $sections ] $extraTrailing }"""

    private fun section(id: String = "s1", kind: String = "LIST", items: String = ""): String =
        """{ "id": "$id", "kind": "$kind", "items": [ $items ] }"""

    private fun item(
        id: String = "i1",
        title: String = "Item",
        imageUrl: String? = null,
        action: String? = null,
    ): String {
        val imageField = if (imageUrl != null) """, "image": { "url": "$imageUrl" }""" else ""
        val actionField = if (action != null) """, "action": $action""" else ""
        return """{ "id": "$id", "title": "$title"$imageField$actionField }"""
    }

    private fun track(id: String): String =
        """{ "trackId": "$id", "title": "T", "artist": "A" }"""

    private fun nestedArray(depth: Int): String {
        var s = ", \"pad\": "
        repeat(depth) { s += "[" }
        s += "1"
        repeat(depth) { s += "]" }
        return s
    }
}

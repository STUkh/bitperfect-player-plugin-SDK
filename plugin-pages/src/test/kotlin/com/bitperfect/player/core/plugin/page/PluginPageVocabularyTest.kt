package com.bitperfect.player.core.plugin.page

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PluginPageVocabularyTest {

    private fun page(extra: String): String = """{ "v": 1, "title": "T" $extra }"""

    @Test
    fun chipsSectionParses() {
        val json = page(""", "sections": [ { "id": "s", "kind": "CHIPS", "selectedId": "a",
            "items": [ { "id": "a", "title": "A" } ] } ]""")
        val result = PluginPageParser.parse(json)
        assertIs<PluginPageParseResult.Ok>(result)
        assertEquals("CHIPS", result.page.sections.single().kind)
        assertEquals("a", result.page.sections.single().selectedId)
    }

    @Test
    fun radioSectionParses() {
        val json = page(""", "sections": [ { "id": "s", "kind": "RADIO", "selectedId": "b",
            "items": [ { "id": "b", "title": "B" } ] } ]""")
        val result = PluginPageParser.parse(json)
        assertIs<PluginPageParseResult.Ok>(result)
        assertEquals("RADIO", result.page.sections.single().kind)
    }

    @Test
    fun textSectionParses() {
        val json = page(""", "sections": [ { "id": "s", "kind": "TEXT",
            "items": [ { "id": "b", "title": "long paragraph" } ] } ]""")
        val result = PluginPageParser.parse(json)
        assertIs<PluginPageParseResult.Ok>(result)
        assertEquals("TEXT", result.page.sections.single().kind)
    }

    @Test
    fun buttonsSectionParses() {
        val json = page(""", "sections": [ { "id": "s", "kind": "BUTTONS",
            "items": [ { "id": "b", "title": "Go", "action": { "type": "navigate", "routeId": "home" } } ] } ]""")
        val result = PluginPageParser.parse(json)
        assertIs<PluginPageParseResult.Ok>(result)
        assertEquals("BUTTONS", result.page.sections.single().kind)
    }

    @Test
    fun switchSectionParses() {
        val json = page(""", "sections": [ { "id": "s", "kind": "SWITCH",
            "items": [ { "id": "sw", "title": "Toggle", "enabled": true } ] } ]""")
        val result = PluginPageParser.parse(json)
        assertIs<PluginPageParseResult.Ok>(result)
        assertEquals("SWITCH", result.page.sections.single().kind)
    }

    @Test
    fun keyValueSectionParses() {
        val json = page(""", "sections": [ { "id": "s", "kind": "KEY_VALUE",
            "items": [ { "id": "kv", "title": "Version", "meta": "1.0.0" } ] } ]""")
        val result = PluginPageParser.parse(json)
        assertIs<PluginPageParseResult.Ok>(result)
        assertEquals("KEY_VALUE", result.page.sections.single().kind)
    }

    @Test
    fun sliderSectionParses() {
        val json = page(""", "sections": [ { "id": "s", "kind": "SLIDER",
            "items": [ { "id": "sl", "title": "Volume", "value": 0.5, "minValue": 0.0, "maxValue": 1.0, "step": 0.05 } ] } ]""")
        val result = PluginPageParser.parse(json)
        assertIs<PluginPageParseResult.Ok>(result)
        val section = result.page.sections.single()
        assertEquals("SLIDER", section.kind)
        val item = section.items.single()
        assertEquals(0.5f, item.value)
        assertEquals(0.0f, item.minValue)
        assertEquals(1.0f, item.maxValue)
        assertEquals(0.05f, item.step)
    }

    @Test
    fun toolbarOverLimitIsTruncated() {
        val buttons = (0 until PluginPageLimits.MAX_TOOLBAR + 3).joinToString(",") {
            """{ "id": "b$it", "label": "B$it", "action": { "type": "navigate", "routeId": "home" } }"""
        }
        val json = page(""", "toolbar": [ $buttons ]""")
        val result = PluginPageParser.parse(json)
        assertIs<PluginPageParseResult.Ok>(result)
        assertEquals(PluginPageLimits.MAX_TOOLBAR, result.page.toolbar.size)
    }

    @Test
    fun tabsOverLimitAreTruncated() {
        val tabs = (0 until PluginPageLimits.MAX_TABS + 3).joinToString(",") { """{ "id": "t$it", "label": "T$it" }""" }
        val json = page(""", "tabs": { "selectedId": "t0", "tabs": [ $tabs ] }""")
        val result = PluginPageParser.parse(json)
        assertIs<PluginPageParseResult.Ok>(result)
        assertEquals(PluginPageLimits.MAX_TABS, result.page.tabs?.tabs?.size)
    }

    @Test
    fun menuOverLimitIsTruncated() {
        val menu = (0 until PluginPageLimits.MAX_MENU + 3).joinToString(",") {
            """{ "label": "M$it", "action": { "type": "navigate", "routeId": "home" } }"""
        }
        val json = page(""", "sections": [ { "id": "s", "kind": "LIST",
            "items": [ { "id": "i", "title": "I", "menu": [ $menu ] } ] } ]""")
        val result = PluginPageParser.parse(json)
        assertIs<PluginPageParseResult.Ok>(result)
        assertEquals(PluginPageLimits.MAX_MENU, result.page.sections.single().items.single().menu.size)
    }

    @Test
    fun chooseOverLimitIsTruncatedByActionResultParser() {
        val options = (0 until PluginPageLimits.MAX_CHOOSE + 5).joinToString(",") {
            """{ "label": "O$it", "action": { "type": "navigate", "routeId": "home" } }"""
        }
        val result = PluginActionResultParser.parse("CHOOSE", """{ "title": "Pick", "options": [ $options ] }""")
        assertIs<PluginActionResult.Choose>(result)
        assertEquals(PluginPageLimits.MAX_CHOOSE, result.options.size)
    }

    @Test
    fun textTitleAtLongLimitParses() {
        val title = "x".repeat(PluginPageLimits.MAX_TEXT_LONG)
        val json = page(""", "sections": [ { "id": "s", "kind": "TEXT",
            "items": [ { "id": "i", "title": "$title" } ] } ]""")
        val result = PluginPageParser.parse(json)
        assertIs<PluginPageParseResult.Ok>(result)
        assertEquals(PluginPageLimits.MAX_TEXT_LONG, result.page.sections.single().items.single().title.length)
    }

    @Test
    fun textTitleOverLongLimitIsTruncated() {
        val title = "x".repeat(PluginPageLimits.MAX_TEXT_LONG + 50)
        val json = page(""", "sections": [ { "id": "s", "kind": "TEXT",
            "items": [ { "id": "i", "title": "$title" } ] } ]""")
        val result = PluginPageParser.parse(json)
        assertIs<PluginPageParseResult.Ok>(result)
        assertEquals(PluginPageLimits.MAX_TEXT_LONG, result.page.sections.single().items.single().title.length)
    }

    @Test
    fun openUrlHttpIsDropped() {
        val json = page(""", "sections": [ { "id": "s", "kind": "LIST",
            "items": [ { "id": "i", "title": "I", "action": { "type": "openUrl", "url": "http://x.com" } } ] } ]""")
        val result = PluginPageParser.parse(json)
        assertIs<PluginPageParseResult.Ok>(result)
        assertNull(result.page.sections.single().items.single().action)
    }

    @Test
    fun openUrlHttpsIsKept() {
        val json = page(""", "sections": [ { "id": "s", "kind": "LIST",
            "items": [ { "id": "i", "title": "I", "action": { "type": "openUrl", "url": "https://x.com" } } ] } ]""")
        val result = PluginPageParser.parse(json)
        assertIs<PluginPageParseResult.Ok>(result)
        assertEquals("https://x.com", result.page.sections.single().items.single().action?.url)
    }

    @Test
    fun openUrlOverlongIsDropped() {
        val longUrl = "https://" + "a".repeat(PluginPageLimits.MAX_URL)
        val json = page(""", "sections": [ { "id": "s", "kind": "LIST",
            "items": [ { "id": "i", "title": "I", "action": { "type": "openUrl", "url": "$longUrl" } } ] } ]""")
        val result = PluginPageParser.parse(json)
        assertIs<PluginPageParseResult.Ok>(result)
        assertNull(result.page.sections.single().items.single().action)
    }

    @Test
    fun openActivityBlankComponentIsDropped() {
        val json = page(""", "sections": [ { "id": "s", "kind": "LIST",
            "items": [ { "id": "i", "title": "I", "action": { "type": "openActivity", "component": "" } } ] } ]""")
        val result = PluginPageParser.parse(json)
        assertIs<PluginPageParseResult.Ok>(result)
        assertNull(result.page.sections.single().items.single().action)
    }

    @Test
    fun openActivityWithComponentIsKept() {
        val json = page(""", "sections": [ { "id": "s", "kind": "LIST",
            "items": [ { "id": "i", "title": "I", "action": { "type": "openActivity", "component": "com.x.Y" } } ] } ]""")
        val result = PluginPageParser.parse(json)
        assertIs<PluginPageParseResult.Ok>(result)
        assertEquals("com.x.Y", result.page.sections.single().items.single().action?.component)
    }

    @Test
    fun confirmAndPromptRoundTripOnDispatchAction() {
        val json = page(""", "sections": [ { "id": "s", "kind": "LIST",
            "items": [ { "id": "i", "title": "I", "action": { "type": "dispatch", "actionId": "rename",
              "confirm": { "title": "Sure?", "destructive": true },
              "prompt": { "label": "Name", "initial": "Old" } } } ] } ]""")
        val result = PluginPageParser.parse(json)
        assertIs<PluginPageParseResult.Ok>(result)
        val action = result.page.sections.single().items.single().action
        assertEquals("rename", action?.actionId)
        assertEquals("Sure?", action?.confirm?.title)
        assertTrue(action?.confirm?.destructive == true)
        assertEquals("Name", action?.prompt?.label)
        assertEquals("Old", action?.prompt?.initial)
    }

    @Test
    fun authPageParses() {
        val json = page(""", "auth": { "message": "Sign in", "activity": "com.x.Login" }""")
        val result = PluginPageParser.parse(json)
        assertIs<PluginPageParseResult.Ok>(result)
        assertEquals("Sign in", result.page.auth?.message)
        assertEquals("com.x.Login", result.page.auth?.activity)
    }

    @Test
    fun actionResultParsesNone() {
        assertIs<PluginActionResult.None>(PluginActionResultParser.parse("NONE", "{}"))
    }

    @Test
    fun actionResultParsesRefresh() {
        assertIs<PluginActionResult.Refresh>(PluginActionResultParser.parse("REFRESH", "{}"))
    }

    @Test
    fun actionResultParsesNavigate() {
        val result = PluginActionResultParser.parse("NAVIGATE", """{ "routeId": "home", "params": { "a": "1" } }""")
        assertIs<PluginActionResult.Navigate>(result)
        assertEquals("home", result.routeId)
        assertEquals("1", result.params["a"])
    }

    @Test
    fun actionResultNavigateWithBadRouteIdIsNone() {
        val result = PluginActionResultParser.parse("NAVIGATE", """{ "routeId": "bad route!" }""")
        assertIs<PluginActionResult.None>(result)
    }

    @Test
    fun actionResultParsesChoose() {
        val result = PluginActionResultParser.parse(
            "CHOOSE",
            """{ "title": "Pick", "options": [ { "label": "A", "action": { "type": "navigate", "routeId": "home" } } ] }""",
        )
        assertIs<PluginActionResult.Choose>(result)
        assertEquals("Pick", result.title)
        assertEquals(1, result.options.size)
    }

    @Test
    fun actionResultDropsChooseOptionsWithRejectedActions() {
        val result = PluginActionResultParser.parse(
            "CHOOSE",
            """{ "title": "Pick", "options": [
                { "label": "Safe", "action": { "type": "navigate", "routeId": "home" } },
                { "label": "Rejected", "action": { "type": "openUrl", "url": "http://insecure" } }
            ] }""",
        )
        assertIs<PluginActionResult.Choose>(result)
        assertEquals(listOf("Safe"), result.options.map { it.label })
    }

    @Test
    fun rejectedToolbarButtonIsNotExposed() {
        val json = page(""", "toolbar": [
            { "id": "safe", "label": "Safe", "action": { "type": "navigate", "routeId": "home" } },
            { "id": "rejected", "label": "Rejected", "action": { "type": "openUrl", "url": "http://insecure" } }
        ]""")
        val result = PluginPageParser.parse(json)
        assertIs<PluginPageParseResult.Ok>(result)
        assertEquals(listOf("safe"), result.page.toolbar.map { it.id })
    }

    @Test
    fun actionResultParsesToast() {
        val result = PluginActionResultParser.parse("TOAST", """{ "text": "Done" }""")
        assertIs<PluginActionResult.Toast>(result)
        assertEquals("Done", result.text)
    }

    @Test
    fun actionResultParsesAuthRequired() {
        val result = PluginActionResultParser.parse("AUTH_REQUIRED", """{ "message": "Signed out", "activity": "com.x.Login" }""")
        assertIs<PluginActionResult.AuthRequired>(result)
        assertEquals("Signed out", result.message)
        assertEquals("com.x.Login", result.activity)
    }

    @Test
    fun actionResultUnknownKindIsNone() {
        assertIs<PluginActionResult.None>(PluginActionResultParser.parse("WAT", "{}"))
    }

    @Test
    fun actionResultMalformedJsonIsNone() {
        assertIs<PluginActionResult.None>(PluginActionResultParser.parse("TOAST", "{ not json"))
    }

    @Test
    fun heroParsesAndTruncatesActions() {
        val actions = (0 until PluginPageLimits.MAX_HERO_ACTIONS + 2).joinToString(",") {
            """{ "id": "b$it", "label": "B$it", "action": { "type": "navigate", "routeId": "home" }, "role": "play" }"""
        }
        val json = page(""", "hero": { "title": "H", "actions": [ $actions ] }""")
        val result = PluginPageParser.parse(json)
        assertIs<PluginPageParseResult.Ok>(result)
        assertEquals("H", result.page.hero?.title)
        assertEquals(PluginPageLimits.MAX_HERO_ACTIONS, result.page.hero?.actions?.size)
        assertEquals("play", result.page.hero?.actions?.first()?.role)
    }

    @Test
    fun rootLayoutParses() {
        val json = page(""", "layout": "ROOT"""")
        val result = PluginPageParser.parse(json)
        assertIs<PluginPageParseResult.Ok>(result)
        assertEquals("ROOT", result.page.layout)
    }

    @Test
    fun rootNavigationDefaultsToVisibleAndCanBeHidden() {
        val default = PluginPageParser.parse(page(""", "layout": "ROOT""""))
        val hidden = PluginPageParser.parse(page(""", "layout": "ROOT", "showRootNavigation": false"""))

        assertIs<PluginPageParseResult.Ok>(default)
        assertTrue(default.page.showRootNavigation)
        assertIs<PluginPageParseResult.Ok>(hidden)
        assertFalse(hidden.page.showRootNavigation)
    }

    @Test
    fun detailLayoutParses() {
        val json = page(""", "layout": "DETAIL"""")
        val result = PluginPageParser.parse(json)
        assertIs<PluginPageParseResult.Ok>(result)
        assertEquals("DETAIL", result.page.layout)
    }

    @Test
    fun unknownLayoutDefaultsToDetail() {
        val json = page(""", "layout": "WAT"""")
        val result = PluginPageParser.parse(json)
        assertIs<PluginPageParseResult.Ok>(result)
        assertEquals("DETAIL", result.page.layout)
    }

    @Test
    fun itemFieldsRoundTrip() {
        val json = page(""", "sections": [ { "id": "s", "kind": "LIST",
            "items": [ { "id": "i", "title": "I", "trackNumber": 3, "year": "2024", "count": 12,
              "meta": "42 min", "favourite": true } ] } ]""")
        val result = PluginPageParser.parse(json)
        assertIs<PluginPageParseResult.Ok>(result)
        val item = result.page.sections.single().items.single()
        assertEquals(3, item.trackNumber)
        assertEquals("2024", item.year)
        assertEquals(12, item.count)
        assertEquals("42 min", item.meta)
        assertEquals(true, item.favourite)
    }

    @Test
    fun quickPlaySectionIsAcceptedAndTruncatedAt27() {
        val items = (0 until PluginPageLimits.MAX_QUICK_PLAY + 3).joinToString(",") { """{ "id": "i$it", "title": "I$it" }""" }
        val json = page(""", "sections": [ { "id": "s", "kind": "QUICK_PLAY", "items": [ $items ] } ]""")
        val result = PluginPageParser.parse(json)
        assertIs<PluginPageParseResult.Ok>(result)
        assertEquals("QUICK_PLAY", result.page.sections.single().kind)
        assertEquals(PluginPageLimits.MAX_QUICK_PLAY, result.page.sections.single().items.size)
    }

    @Test
    fun heroCarouselSectionIsAccepted() {
        val json = page(""", "sections": [ { "id": "s", "kind": "HERO_CAROUSEL",
            "items": [ { "id": "a", "title": "A" } ] } ]""")
        val result = PluginPageParser.parse(json)
        assertIs<PluginPageParseResult.Ok>(result)
        assertEquals("HERO_CAROUSEL", result.page.sections.single().kind)
    }

    @Test
    fun unknownRoleIsNulled() {
        val json = page(""", "toolbar": [ { "id": "b", "label": "B",
            "action": { "type": "navigate", "routeId": "home" }, "role": "wat" } ]""")
        val result = PluginPageParser.parse(json)
        assertIs<PluginPageParseResult.Ok>(result)
        assertNull(result.page.toolbar.single().role)
    }

    @Test
    fun unknownFormatTierIsNulled() {
        val json = page(""", "sections": [ { "id": "s", "kind": "LIST",
            "items": [ { "id": "i", "title": "I", "track": { "trackId": "1", "title": "T", "artist": "A", "formatTier": "wat" } } ] } ]""")
        val result = PluginPageParser.parse(json)
        assertIs<PluginPageParseResult.Ok>(result)
        assertNull(result.page.sections.single().items.single().track?.formatTier)
    }

    @Test
    fun knownFormatTierRoundTrips() {
        val json = page(""", "sections": [ { "id": "s", "kind": "LIST",
            "items": [ { "id": "i", "title": "I", "track": { "trackId": "1", "title": "T", "artist": "A", "formatTier": "HI_RES" } } ] } ]""")
        val result = PluginPageParser.parse(json)
        assertIs<PluginPageParseResult.Ok>(result)
        assertEquals("HI_RES", result.page.sections.single().items.single().track?.formatTier)
    }

    @Test
    fun phase0SampleStillParsesUnchanged() {
        val json = """
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
        val result = PluginPageParser.parse(json)
        assertIs<PluginPageParseResult.Ok>(result)
        assertEquals("DETAIL", result.page.layout)
        assertNull(result.page.hero)
        assertEquals("SHELF", result.page.sections.single().kind)
        assertEquals("1", result.page.sections.single().items.single().track?.trackId)
    }
}

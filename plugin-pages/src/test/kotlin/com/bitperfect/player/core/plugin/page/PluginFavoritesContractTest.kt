package com.bitperfect.player.core.plugin.page

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PluginFavoritesContractTest {
    @Test
    fun `PluginFavoriteType fromWire matches known aliases and case variants`() {
        assertEquals(PluginFavoriteType.TRACK, PluginFavoriteType.fromWire("track"))
        assertEquals(PluginFavoriteType.TRACK, PluginFavoriteType.fromWire("tracks"))
        assertEquals(PluginFavoriteType.TRACK, PluginFavoriteType.fromWire("song"))
        assertEquals(PluginFavoriteType.TRACK, PluginFavoriteType.fromWire("Songs"))
        assertEquals(PluginFavoriteType.ALBUM, PluginFavoriteType.fromWire("album"))
        assertEquals(PluginFavoriteType.ALBUM, PluginFavoriteType.fromWire("ALBUMS"))
        assertEquals(PluginFavoriteType.ARTIST, PluginFavoriteType.fromWire("artist"))
        assertEquals(PluginFavoriteType.ARTIST, PluginFavoriteType.fromWire("Artists"))
        assertEquals(PluginFavoriteType.PLAYLIST, PluginFavoriteType.fromWire("playlist"))
        assertEquals(PluginFavoriteType.PLAYLIST, PluginFavoriteType.fromWire("PLAYLISTS"))
        assertNull(PluginFavoriteType.fromWire("podcast"))
        assertNull(PluginFavoriteType.fromWire(null))
    }

    @Test
    fun `PluginCatalogCapabilities checks support and round-trips through json`() {
        val caps = PluginCatalogCapabilities(
            supportedFavorites = setOf(PluginFavoriteType.TRACK, PluginFavoriteType.ARTIST),
            routes = mapOf("favorites" to "my_favs"),
        )
        assertTrue(caps.supports(PluginFavoriteType.TRACK))
        assertTrue(caps.supports(PluginFavoriteType.ARTIST))
        assertFalse(caps.supports(PluginFavoriteType.ALBUM))
        assertFalse(caps.supports(PluginFavoriteType.PLAYLIST))

        val json = Json.encodeToString(caps)
        val decoded = Json.decodeFromString<PluginCatalogCapabilities>(json)
        assertEquals(caps, decoded)
    }

    @Test
    fun `pluginFavoriteAction builds standard dispatch action`() {
        val action = pluginFavoriteAction(PluginFavoriteType.ALBUM, "alb_42", targetOn = true)
        assertEquals("dispatch", action.type)
        assertEquals(ACTION_FAVORITE, action.actionId)
        assertEquals(
            mapOf(
                PARAM_KIND to "album",
                PARAM_ID to "alb_42",
                PARAM_ON to "true",
            ),
            action.params,
        )
    }

    @Test
    fun `pluginFavoriteButton configures button based on favorite state`() {
        val favBtn = pluginFavoriteButton(PluginFavoriteType.ARTIST, "art_1", isFavorite = true)
        assertEquals("fav_artist_art_1", favBtn.id)
        assertEquals("Favourite", favBtn.label)
        assertEquals("HEART_FILLED", favBtn.icon)
        assertEquals("favourite", favBtn.role)
        assertEquals("false", favBtn.action.params?.get(PARAM_ON))

        val unfavBtn = pluginFavoriteButton(PluginFavoriteType.ARTIST, "art_1", isFavorite = false, label = "Like")
        assertEquals("Like", unfavBtn.label)
        assertEquals("HEART", unfavBtn.icon)
        assertEquals("favourite", unfavBtn.role)
        assertEquals("true", unfavBtn.action.params?.get(PARAM_ON))
    }
}

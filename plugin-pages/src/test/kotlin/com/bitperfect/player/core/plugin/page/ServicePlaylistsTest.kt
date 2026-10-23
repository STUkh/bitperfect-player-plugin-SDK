package com.bitperfect.player.core.plugin.page

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ServicePlaylistsTest {
    @Test
    fun `codec round-trips quotes and a null count`() {
        val list = listOf(ServicePlaylist("a", "He said \"hi\"\n", 3), ServicePlaylist("b", "B"))
        val json = ServicePlaylistCodec.encode(list)
        assertFalse(json.contains("\"count\":null"))
        assertEquals(list, ServicePlaylistCodec.decode(json))
    }

    @Test
    fun `decode of a non-list body is null`() {
        assertNull(ServicePlaylistCodec.decode(""))
        assertNull(ServicePlaylistCodec.decode("""{"on":true}"""))
    }

    @Test
    fun `choose options are playlists plus new playlist and capped`() {
        val few = servicePlaylistChooseOptions(listOf(ServicePlaylist("a", "A")), "t1")
        assertEquals(listOf("A", "New playlist"), few.map { it.label })
        assertEquals("t1", few.last().action.params?.get(ServicePlaylistActions.PARAM_TRACK_ID))
        val many = servicePlaylistChooseOptions(List(100) { ServicePlaylist("$it", "P$it") }, "t1")
        assertEquals(PluginPageLimits.MAX_CHOOSE, many.size)
        assertEquals("New playlist", many.last().label)
    }
}

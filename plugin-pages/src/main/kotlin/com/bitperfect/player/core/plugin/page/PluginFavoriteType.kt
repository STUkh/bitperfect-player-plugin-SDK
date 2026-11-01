package com.bitperfect.player.core.plugin.page

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class PluginFavoriteType(val wireName: String) {
    @SerialName("track") TRACK("track"),
    @SerialName("album") ALBUM("album"),
    @SerialName("artist") ARTIST("artist"),
    @SerialName("playlist") PLAYLIST("playlist");

    companion object {
        fun fromWire(wire: String?): PluginFavoriteType? = when (wire?.lowercase()) {
            "track", "tracks", "song", "songs" -> TRACK
            "album", "albums" -> ALBUM
            "artist", "artists" -> ARTIST
            "playlist", "playlists" -> PLAYLIST
            else -> null
        }
    }
}

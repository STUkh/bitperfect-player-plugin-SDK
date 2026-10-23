package com.bitperfect.player.core.plugin.page

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Host-native "Add to playlist" contract between the host and a source plugin. */
object ServicePlaylistActions {
    const val LIST = "playlist.list"
    const val ADD = "playlist.add"
    const val CREATE = "playlist.create"
    const val PARAM_PLAYLIST_ID = "playlistId"
    const val PARAM_TRACK_ID = "trackId"
    const val PARAM_NAME = "input"
}

@Serializable
data class ServicePlaylist(val id: String, val title: String, val count: Int? = null)

object ServicePlaylistCodec {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private class Body(val playlists: List<ServicePlaylist>)

    /** `{"playlists":[…]}`; `count` is omitted when null. */
    fun encode(playlists: List<ServicePlaylist>): String = json.encodeToString(Body(playlists))

    /** Null when [json] is not a `playlist.list` body. */
    fun decode(json: String): List<ServicePlaylist>? =
        runCatching { this.json.decodeFromString<Body>(json).playlists }.getOrNull()
}

/** Choose options for hosts without the native sheet: one entry per playlist, then "New playlist"; capped to [PluginPageLimits.MAX_CHOOSE]. */
fun servicePlaylistChooseOptions(playlists: List<ServicePlaylist>, trackId: String): List<PluginMenuEntry> {
    val existing = playlists.take(PluginPageLimits.MAX_CHOOSE - 1).map {
        PluginMenuEntry(
            label = it.title,
            action = PluginAction(
                type = "dispatch",
                actionId = ServicePlaylistActions.ADD,
                params = mapOf(ServicePlaylistActions.PARAM_PLAYLIST_ID to it.id, ServicePlaylistActions.PARAM_TRACK_ID to trackId),
            ),
        )
    }
    val create = PluginMenuEntry(
        label = "New playlist",
        action = PluginAction(
            type = "dispatch",
            actionId = ServicePlaylistActions.CREATE,
            params = mapOf(ServicePlaylistActions.PARAM_TRACK_ID to trackId),
            prompt = PluginPrompt(label = "Playlist name"),
        ),
    )
    return existing + create
}

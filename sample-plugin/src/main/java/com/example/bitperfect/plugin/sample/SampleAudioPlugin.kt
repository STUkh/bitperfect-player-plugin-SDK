package com.example.bitperfect.plugin.sample

import com.bitperfect.player.sdk.plugin.dto.ActionResultDto
import com.bitperfect.player.sdk.plugin.dto.AlbumDto
import com.bitperfect.player.sdk.plugin.dto.BrowseResultDto
import com.bitperfect.player.sdk.plugin.dto.PageDescriptorDto
import com.bitperfect.player.sdk.plugin.dto.SearchResultDto
import com.bitperfect.player.sdk.plugin.dto.StreamDescriptorDto
import com.bitperfect.player.sdk.plugin.dto.TrackRefDto
import com.bitperfect.player.sdk.plugin.runtime.BitperfectSourcePlugin
import com.bitperfect.player.sdk.plugin.runtime.PluginSdk

/**
 * Sample implementation of a Bitperfect Player Audio Source Plugin.
 *
 * Demonstrates:
 * 1. Stream resolution for bit-perfect audio playback.
 * 2. Server-Driven UI (Pages API v1) with catalog views, switches, sliders, and telemetry.
 * 3. Search integration.
 * 4. Action dispatching without IPC latency.
 */
class SampleAudioPlugin : BitperfectSourcePlugin() {

    override fun capabilities(): Int =
        PluginSdk.CAPABILITY_SOURCE or PluginSdk.CAPABILITY_UI_TABS or PluginSdk.CAPABILITY_PAGES

    /**
     * Resolves a direct audio stream for the given track.
     * The host handles downloading, buffering, Bit-Perfect decoding, and hardware DAC output.
     */
    override fun onResolveStream(
        track: TrackRefDto,
        maxBitDepth: Int,
        maxSampleRate: Int,
    ): StreamDescriptorDto {
        return StreamDescriptorDto(
            url = "https://example.com/audio/${track.trackId}.flac",
            headers = mapOf("User-Agent" to "BitperfectSamplePlugin/1.0"),
            encrypted = false,
            trackKey = null,
            cipher = null,
            codec = "FLAC",
            bitDepth = 24.coerceAtMost(maxBitDepth),
            sampleRate = 96000.coerceAtMost(maxSampleRate),
            expiresAtEpochMs = System.currentTimeMillis() + 3600_000L,
        )
    }

    /**
     * Renders declarative pages via Pages API v1.
     * The host renders the interface natively adhering to the Bitperfect Player Flat-Dark design system.
     */
    override fun onRenderPage(
        routeId: String,
        paramsJson: String?,
        pageToken: String?,
    ): PageDescriptorDto? {
        val pageJson = when (routeId) {
            "settings" -> buildSettingsPage()
            "home", "" -> buildHomePage()
            else -> null
        } ?: return null

        return PageDescriptorDto(contractVersion = 1, json = pageJson)
    }

    /**
     * Dispatches user actions triggered from declarative pages.
     */
    override fun onDispatchAction(actionId: String, paramsJson: String?): ActionResultDto {
        return when (actionId) {
            "toggle_lossless" -> {
                // Handle switch toggle event (params contains {"checked": "true"|"false"})
                ActionResultDto(kind = "TOAST", json = """{"text":"Lossless setting updated"}""")
            }
            "set_gain" -> {
                // Handle slider release event (params contains {"value": "<float>"})
                ActionResultDto(kind = "NONE", json = "")
            }
            else -> ActionResultDto(kind = "NONE", json = "")
        }
    }

    override fun onSearch(query: String, pageToken: String?): SearchResultDto {
        val tracks = listOf(
            TrackRefDto(
                pluginId = "com.example.bitperfect.plugin.sample",
                trackId = "track_search_1",
                albumId = "album_demo",
                title = "Acoustic Horizon (Search: $query)",
                artist = "Bitperfect Studio",
                durationMs = 245000L,
            ),
        )
        return SearchResultDto(tracks = tracks, nextPageToken = null)
    }

    override fun onBrowse(folderId: String?, pageToken: String?): BrowseResultDto? = null
    override fun onGetAlbum(albumId: String): AlbumDto? = null
    override fun onParseUrl(url: String): TrackRefDto? = null

    private fun buildHomePage(): String = """
    {
      "v": 1,
      "title": "Sample Hi-Res Music",
      "subtitle": "High-Fidelity Audio Source",
      "layout": "DETAIL",
      "sections": [
        {
          "id": "featured_list",
          "kind": "LIST",
          "header": { "title": "Master Recordings (96kHz / 24-bit)" },
          "items": [
            {
              "id": "track_101",
              "title": "Acoustic Horizon",
              "subtitle": "Bitperfect Studio • 96kHz / 24-bit FLAC",
              "badge": "HI_RES",
              "action": {
                "type": "play",
                "tracks": [
                  {
                    "trackId": "track_101",
                    "title": "Acoustic Horizon",
                    "artist": "Bitperfect Studio",
                    "durationMs": 245000
                  }
                ]
              }
            },
            {
              "id": "track_102",
              "title": "Analog Resonance",
              "subtitle": "Audiophile Masters • 192kHz / 24-bit FLAC",
              "badge": "HI_RES",
              "action": {
                "type": "play",
                "tracks": [
                  {
                    "trackId": "track_102",
                    "title": "Analog Resonance",
                    "artist": "Audiophile Masters",
                    "durationMs": 312000
                  }
                ]
              }
            }
          ]
        }
      ]
    }
    """.trimIndent()

    private fun buildSettingsPage(): String = """
    {
      "v": 1,
      "title": "Plugin Settings",
      "subtitle": "Sample Audio Plugin Configuration",
      "layout": "DETAIL",
      "sections": [
        {
          "id": "switch_section",
          "kind": "SWITCH",
          "header": { "title": "Audio Stream Preferences" },
          "items": [
            {
              "id": "prefer_hi_res",
              "title": "Prefer Hi-Res 24-bit FLAC",
              "subtitle": "Request maximum sample rate and bit depth from CDN",
              "enabled": true,
              "action": {
                "type": "dispatch",
                "actionId": "toggle_lossless",
                "params": { "checked": "true" }
              }
            }
          ]
        },
        {
          "id": "slider_section",
          "kind": "SLIDER",
          "header": { "title": "Gain Adjustment" },
          "items": [
            {
              "id": "preamp",
              "title": "Pre-Amp Gain",
              "meta": "-1.5 dB",
              "value": -1.5,
              "minValue": -12.0,
              "maxValue": 6.0,
              "step": 0.5,
              "action": {
                "type": "dispatch",
                "actionId": "set_gain",
                "params": { "value": "-1.5" }
              }
            }
          ]
        },
        {
          "id": "telemetry_section",
          "kind": "KEY_VALUE",
          "header": { "title": "Plugin Status" },
          "items": [
            {
              "id": "api_level",
              "title": "SDK Protocol Version",
              "meta": "API Level 6"
            },
            {
              "id": "sandbox_mode",
              "title": "Isolation Boundary",
              "meta": "Out-of-Process Linux Service"
            }
          ]
        }
      ]
    }
    """.trimIndent()
}

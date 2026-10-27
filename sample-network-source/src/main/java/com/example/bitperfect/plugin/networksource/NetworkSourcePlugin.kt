package com.example.bitperfect.plugin.networksource

import android.os.SystemClock
import com.bitperfect.player.core.plugin.page.*
import com.bitperfect.player.sdk.plugin.dto.*
import com.bitperfect.player.sdk.plugin.runtime.BitperfectSourcePlugin
import com.bitperfect.player.sdk.plugin.runtime.PluginSdk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * High-quality, ethical Network Streaming Source Plugin demonstrating:
 * 1. CAPABILITY_SOURCE and CAPABILITY_PAGES (Server-Driven UI via Pages API v1)
 * 2. Public, open-source audio streaming catalog (Lossless FLAC and high-bitrate community streams)
 * 3. Resolving genuine streaming URLs with custom headers, user-agent, and expiration
 * 4. Catalog browsing, tabs, search, and pagination
 * 5. Action dispatch handling (favorites, stream quality preference, buffer adjustment)
 */
class NetworkSourcePlugin : BitperfectSourcePlugin() {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    // User preferences (can be modified via Server-Driven UI actions)
    private val preferFlac = AtomicBoolean(true)
    private val enableIcyMetadata = AtomicBoolean(true)
    private val bufferTargetSeconds = AtomicReference(5.0f)
    private val favoriteStationIds = mutableSetOf<String>()

    override fun capabilities(): Int =
        PluginSdk.CAPABILITY_SOURCE or PluginSdk.CAPABILITY_UI_TABS or PluginSdk.CAPABILITY_PAGES

    /**
     * Resolves direct audio stream descriptors for the host player.
     * The host handles downloading, network buffering, bit-perfect decoding, and hardware DAC output.
     */
    override fun onResolveStream(
        track: TrackRefDto,
        maxBitDepth: Int,
        maxSampleRate: Int,
    ): StreamDescriptorDto? {
        val station = OpenAudioCatalog.findById(track.trackId) ?: return null

        val useFlac = preferFlac.get() &&
            station.streamUrlFlac != null &&
            station.bitDepth <= maxBitDepth &&
            station.sampleRate <= maxSampleRate

        val flacUrl = station.streamUrlFlac
        val streamUrl = if (useFlac && flacUrl != null) flacUrl else station.streamUrlMp3
        val codec = if (useFlac) "flac" else "mp3"
        val mimeType = if (useFlac) "audio/flac" else "audio/mpeg"
        val bitDepth = if (useFlac) station.bitDepth else 16
        val sampleRate = station.sampleRate

        val headers = mutableMapOf(
            "User-Agent" to "BitperfectPlayer-OpenCatalog/1.2 (Android; AudiophileStream)",
            "Accept" to if (useFlac) "audio/flac, audio/x-flac, */*" else "audio/mpeg, */*",
        )
        if (enableIcyMetadata.get()) {
            headers["Icy-MetaData"] = "1"
        }

        // Stream URL valid for 24 hours
        val expiresAt = System.currentTimeMillis() + 86_400_000L

        return StreamDescriptorDto(
            url = streamUrl,
            headers = headers,
            encrypted = false,
            trackKey = null,
            cipher = null,
            codec = codec,
            bitDepth = bitDepth,
            sampleRate = sampleRate,
            expiresAtEpochMs = expiresAt,
            mimeType = mimeType,
        )
    }

    /**
     * Renders Server-Driven UI pages for catalog browsing, station details, and settings.
     */
    override fun onRenderPage(
        routeId: String,
        paramsJson: String?,
        pageToken: String?,
    ): PageDescriptorDto? {
        val page = when (routeId) {
            "catalog", "home" -> buildCatalogPage(paramsJson, pageToken)
            "station_detail" -> buildStationDetailPage(paramsJson)
            "settings" -> buildSettingsPage()
            else -> buildCatalogPage(paramsJson, pageToken)
        } ?: return null

        return page.toDescriptorDto(json = json)
    }

    /**
     * Handles UI action dispatches triggered by user interactions in the Server-Driven UI.
     */
    override fun onDispatchAction(actionId: String, paramsJson: String?): ActionResultDto {
        val params = try {
            paramsJson?.let { json.parseToJsonElement(it).jsonObject }
        } catch (_: Exception) {
            null
        }

        return when (actionId) {
            "toggle_favorite" -> {
                val stationId = params?.get("stationId")?.jsonPrimitive?.content ?: ""
                val isFav = synchronized(favoriteStationIds) {
                    if (favoriteStationIds.contains(stationId)) {
                        favoriteStationIds.remove(stationId)
                        false
                    } else {
                        favoriteStationIds.add(stationId)
                        true
                    }
                }
                val msg = if (isFav) "Added to Favorites" else "Removed from Favorites"
                ActionResultDto(ActionResultDto.KIND_TOAST, """{"text":"$msg"}""")
            }

            "set_flac_pref" -> {
                val checked = params?.get("checked")?.jsonPrimitive?.content?.toBoolean() ?: true
                preferFlac.set(checked)
                ActionResultDto(ActionResultDto.KIND_REFRESH, "")
            }

            "set_icy_pref" -> {
                val checked = params?.get("checked")?.jsonPrimitive?.content?.toBoolean() ?: true
                enableIcyMetadata.set(checked)
                ActionResultDto(ActionResultDto.KIND_REFRESH, "")
            }

            "set_buffer_depth" -> {
                val value = params?.get("value")?.jsonPrimitive?.content?.toFloatOrNull() ?: 5.0f
                bufferTargetSeconds.set(value)
                ActionResultDto(ActionResultDto.KIND_NONE, "")
            }

            "refresh_catalog" -> {
                ActionResultDto(ActionResultDto.KIND_REFRESH, "")
            }

            else -> ActionResultDto(ActionResultDto.KIND_NONE, "")
        }
    }

    override fun onSearch(query: String, pageToken: String?): SearchResultDto {
        val stations = OpenAudioCatalog.search(query)
        val tracks = stations.map { it.toTrackRef() }
        return SearchResultDto(
            tracks = tracks,
            nextPageToken = null,
        )
    }

    override fun onBrowse(folderId: String?, pageToken: String?): BrowseResultDto {
        val items = mutableListOf<BrowseItemDto>()
        if (folderId == null || folderId.isEmpty()) {
            items.add(BrowseItemDto(id = "folder:featured", title = "Featured FLAC Streams", subtitle = null, isContainer = true, artUrl = null, track = null))
            items.add(BrowseItemDto(id = "folder:genres", title = "Browse by Genre", subtitle = null, isContainer = true, artUrl = null, track = null))
            items.add(BrowseItemDto(id = "folder:all", title = "All Radio Stations", subtitle = null, isContainer = true, artUrl = null, track = null))
        } else if (folderId == "folder:featured") {
            OpenAudioCatalog.featured().forEach { station ->
                items.add(BrowseItemDto(id = station.id, title = station.title, subtitle = station.genre, isContainer = false, artUrl = station.artworkUrl, track = station.toTrackRef()))
            }
        } else if (folderId == "folder:genres") {
            OpenAudioCatalog.genres().forEach { genre ->
                items.add(BrowseItemDto(id = "genre:$genre", title = genre, subtitle = null, isContainer = true, artUrl = null, track = null))
            }
        } else if (folderId.startsWith("genre:")) {
            val genre = folderId.removePrefix("genre:")
            OpenAudioCatalog.byGenre(genre).forEach { station ->
                items.add(BrowseItemDto(id = station.id, title = station.title, subtitle = station.genre, isContainer = false, artUrl = station.artworkUrl, track = station.toTrackRef()))
            }
        } else {
            OpenAudioCatalog.STATIONS.forEach { station ->
                items.add(BrowseItemDto(id = station.id, title = station.title, subtitle = station.genre, isContainer = false, artUrl = station.artworkUrl, track = station.toTrackRef()))
            }
        }
        return BrowseResultDto(items = items, nextPageToken = null)
    }

    override fun onGetAlbum(albumId: String): AlbumDto? {
        val station = OpenAudioCatalog.findById(albumId) ?: return null
        return station.toAlbumDto()
    }

    override fun onParseUrl(url: String): TrackRefDto? {
        val station = OpenAudioCatalog.STATIONS.firstOrNull {
            it.streamUrlFlac == url || it.streamUrlMp3 == url
        } ?: return null
        return station.toTrackRef()
    }

    // --- Server-Driven UI Page Builders ---

    private fun buildCatalogPage(paramsJson: String?, pageToken: String?): PluginPage {
        val params = try {
            paramsJson?.let { json.parseToJsonElement(it).jsonObject }
        } catch (_: Exception) {
            null
        }

        val activeTab = params?.get("tab")?.jsonPrimitive?.content ?: "featured"
        val searchQuery = params?.get("q")?.jsonPrimitive?.content

        // If search query is present, render search results view
        if (!searchQuery.isNullOrBlank()) {
            val searchResults = OpenAudioCatalog.search(searchQuery)
            return PluginPage(
                title = "Search: $searchQuery",
                subtitle = "${searchResults.size} stations found",
                layout = "DETAIL",
                search = PluginSearchBox(
                    placeholder = "Search open streams & stations...",
                    routeId = "catalog",
                    paramKey = "q",
                    minChars = 2,
                ),
                sections = listOf(
                    PluginSection(
                        id = "search_results",
                        kind = "LIST",
                        header = PluginSectionHeader(title = "Matching Stations"),
                        items = searchResults.map { it.toPluginItem() },
                    )
                )
            )
        }

        val tabStrip = PluginTabStrip(
            selectedId = activeTab,
            tabs = listOf(
                PluginTab(id = "featured", label = "Featured", params = mapOf("tab" to "featured")),
                PluginTab(id = "genres", label = "Genres", params = mapOf("tab" to "genres")),
                PluginTab(id = "stations", label = "All Stations", params = mapOf("tab" to "stations")),
            )
        )

        val sections = mutableListOf<PluginSection>()

        when (activeTab) {
            "featured" -> {
                // Shelf of Lossless FLAC streams
                val flacStations = OpenAudioCatalog.featured()
                sections.add(
                    PluginSection(
                        id = "flac_shelf",
                        kind = "SHELF",
                        header = PluginSectionHeader(title = "Bit-Perfect Lossless Streams"),
                        items = flacStations.map { it.toPluginItem() },
                    )
                )

                // Grid of Community Favorites
                val communityStations = OpenAudioCatalog.STATIONS.take(6)
                sections.add(
                    PluginSection(
                        id = "community_grid",
                        kind = "GRID",
                        header = PluginSectionHeader(title = "Popular Community Stations"),
                        items = communityStations.map { it.toPluginItem() },
                    )
                )
            }

            "genres" -> {
                val selectedGenre = params?.get("genre")?.jsonPrimitive?.content ?: OpenAudioCatalog.genres().first()
                val genreChips = OpenAudioCatalog.genres().map { genre ->
                    PluginItem(
                        id = genre,
                        title = genre,
                        action = PluginAction(
                            type = "navigate",
                            routeId = "catalog",
                            params = mapOf("tab" to "genres", "genre" to genre),
                        ),
                    )
                }
                sections.add(
                    PluginSection(
                        id = "genre_chips",
                        kind = "CHIPS",
                        selectedId = selectedGenre,
                        items = genreChips,
                    )
                )

                val genreStations = OpenAudioCatalog.byGenre(selectedGenre)
                sections.add(
                    PluginSection(
                        id = "genre_stations",
                        kind = "GRID",
                        header = PluginSectionHeader(title = "$selectedGenre Stations"),
                        items = genreStations.map { it.toPluginItem() },
                    )
                )
            }

            "stations" -> {
                val pageNumber = pageToken?.removePrefix("page_")?.toIntOrNull() ?: 1
                val (stations, nextToken) = OpenAudioCatalog.paginated(pageNumber, pageSize = 5)

                sections.add(
                    PluginSection(
                        id = "stations_list",
                        kind = "LIST",
                        header = PluginSectionHeader(title = "Open Audio Broadcast Directory"),
                        items = stations.map { it.toPluginItem() },
                        nextPageToken = nextToken,
                    )
                )
            }
        }

        return PluginPage(
            title = "Open Audio Catalog",
            subtitle = "Legal, Public Domain & Community Lossless Audio Streams",
            layout = "ROOT",
            tabs = tabStrip,
            search = PluginSearchBox(
                placeholder = "Search open streams & stations...",
                routeId = "catalog",
                paramKey = "q",
                minChars = 2,
            ),
            sections = sections,
        )
    }

    private fun buildStationDetailPage(paramsJson: String?): PluginPage? {
        val params = try {
            paramsJson?.let { json.parseToJsonElement(it).jsonObject }
        } catch (_: Exception) {
            null
        }
        val stationId = params?.get("stationId")?.jsonPrimitive?.content ?: return null
        val station = OpenAudioCatalog.findById(stationId) ?: return null

        val isFav = synchronized(favoriteStationIds) { favoriteStationIds.contains(stationId) }

        return PluginPage(
            title = station.title,
            subtitle = station.genre,
            layout = "DETAIL",
            hero = PluginHero(
                title = station.title,
                subtitle = "${station.genre} • ${station.codec.uppercase()}",
                meta = "${station.sampleRate / 1000}kHz / ${station.bitDepth}-bit",
                image = PluginImage(url = station.artworkUrl),
                actions = listOf(
                    PluginButton(
                        id = "hero_play",
                        label = "Listen Live",
                        style = "PRIMARY",
                        action = PluginAction(
                            type = "play",
                            tracks = listOf(station.toPluginTrack()),
                        ),
                    ),
                    PluginButton(
                        id = "hero_fav",
                        label = if (isFav) "Favorited" else "Favorite",
                        style = "SECONDARY",
                        action = PluginAction(
                            type = "dispatch",
                            actionId = "toggle_favorite",
                            params = mapOf("stationId" to station.id),
                        ),
                    )
                ),
            ),
            sections = listOf(
                PluginSection(
                    id = "telemetry",
                    kind = "KEY_VALUE",
                    header = PluginSectionHeader(title = "Broadcast Stream Telemetry"),
                    items = listOf(
                        PluginItem(id = "fmt", title = "Audio Format", meta = "${station.codec.uppercase()} (${station.sampleRate} Hz)"),
                        PluginItem(id = "res", title = "Bit Resolution", meta = "${station.bitDepth}-bit uncompressed"),
                        PluginItem(id = "srv", title = "Streaming Protocol", meta = station.serverType),
                        PluginItem(id = "lic", title = "Licensing & Origin", meta = station.licenseType),
                    ),
                ),
                PluginSection(
                    id = "description",
                    kind = "TEXT",
                    header = PluginSectionHeader(title = "Station Overview"),
                    items = listOf(
                        PluginItem(id = "desc_text", title = station.description),
                    ),
                ),
                PluginSection(
                    id = "actions",
                    kind = "BUTTONS",
                    items = listOf(
                        PluginItem(
                            id = "visit_web",
                            title = "Open Station Homepage",
                            subtitle = station.homepageUrl,
                            action = PluginAction(
                                type = "openUrl",
                                url = station.homepageUrl,
                            ),
                        ),
                    ),
                ),
            ),
        )
    }

    private fun buildSettingsPage(): PluginPage {
        val bufferSecs = bufferTargetSeconds.get()
        return PluginPage(
            title = "Open Audio Stream Settings",
            subtitle = "Network buffering, stream formats & telemetry",
            layout = "DETAIL",
            sections = listOf(
                PluginSection(
                    id = "preferences_switch",
                    kind = "SWITCH",
                    header = PluginSectionHeader(title = "Audio Stream Preferences"),
                    items = listOf(
                        PluginItem(
                            id = "pref_flac",
                            title = "Prefer Lossless FLAC Streams",
                            subtitle = "Stream bit-perfect 16-bit FLAC whenever available instead of MP3",
                            enabled = preferFlac.get(),
                            action = PluginAction(
                                type = "dispatch",
                                actionId = "set_flac_pref",
                            ),
                        ),
                        PluginItem(
                            id = "pref_icy",
                            title = "Parse ICY Stream Metadata",
                            subtitle = "Extract live song titles and artist names from broadcast headers",
                            enabled = enableIcyMetadata.get(),
                            action = PluginAction(
                                type = "dispatch",
                                actionId = "set_icy_pref",
                            ),
                        ),
                    ),
                ),
                PluginSection(
                    id = "buffer_slider",
                    kind = "SLIDER",
                    header = PluginSectionHeader(title = "Pre-Buffer Depth"),
                    items = listOf(
                        PluginItem(
                            id = "slider_buffer",
                            title = "Network Buffer Target",
                            subtitle = "Adjust buffer duration to prevent audio dropouts on unstable connections",
                            value = bufferSecs,
                            minValue = 2.0f,
                            maxValue = 15.0f,
                            step = 1.0f,
                            meta = "${bufferSecs.toInt()} seconds",
                            action = PluginAction(
                                type = "dispatch",
                                actionId = "set_buffer_depth",
                            ),
                        ),
                    ),
                ),
                PluginSection(
                    id = "info_table",
                    kind = "KEY_VALUE",
                    header = PluginSectionHeader(title = "Catalog Telemetry"),
                    items = listOf(
                        PluginItem(id = "cat_ver", title = "Catalog Engine", meta = "v1.2 (Open Audio)"),
                        PluginItem(id = "cat_count", title = "Indexed Stations", meta = "${OpenAudioCatalog.STATIONS.size} streams"),
                        PluginItem(id = "cat_lic", title = "Compliance", meta = "100% Ethical & Public Domain"),
                    ),
                ),
            ),
        )
    }

    private fun AudioStation.toTrackRef(): TrackRefDto =
        TrackRefDto(
            pluginId = "com.example.bitperfect.plugin.networksource",
            trackId = id,
            albumId = id,
            title = title,
            artist = genre,
            durationMs = 0L,
        )

    private fun AudioStation.toPlaybackTrack(): PlaybackTrackDto =
        PlaybackTrackDto(
            trackId = id,
            albumId = id,
            title = title,
            artist = genre,
            album = "Open Audio Broadcast",
            durationMs = 0L, // Live stream has unbounded duration
            artworkUrl = artworkUrl,
            quality = if (streamUrlFlac != null) "FLAC 44.1kHz" else "MP3 320k",
        )

    private fun AudioStation.toAlbumDto(): AlbumDto =
        AlbumDto(
            albumId = id,
            title = title,
            artist = genre,
            artUrl = artworkUrl,
            tracks = listOf(toTrackRef()),
        )
    private fun AudioStation.toPluginTrack(): PluginTrack =
        PluginTrack(
            trackId = id,
            albumId = id,
            title = title,
            artist = genre,
            album = "Open Audio Stream",
            durationMs = 0L,
            artworkUrl = artworkUrl,
            formatTier = if (streamUrlFlac != null) "LOSSLESS" else "LOSSY",
        )

    private fun AudioStation.toPluginItem(): PluginItem =
        PluginItem(
            id = id,
            title = title,
            subtitle = "$genre • ${codec.uppercase()}",
            image = PluginImage(url = artworkUrl),
            badge = if (streamUrlFlac != null) "LOSSLESS" else "320K",
            formatTier = if (streamUrlFlac != null) "LOSSLESS" else "LOSSY",
            action = PluginAction(
                type = "play",
                tracks = listOf(toPluginTrack()),
            ),
            menu = listOf(
                PluginMenuEntry(
                    label = "Station Details",
                    action = PluginAction(
                        type = "navigate",
                        routeId = "station_detail",
                        params = mapOf("stationId" to id),
                    ),
                ),
                PluginMenuEntry(
                    label = "Toggle Favorite",
                    action = PluginAction(
                        type = "dispatch",
                        actionId = "toggle_favorite",
                        params = mapOf("stationId" to id),
                    ),
                    role = "favourite",
                ),
            ),
        )
}

package com.bitperfect.player.core.plugin.page

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

object PluginPageLimits {
    // Was 256 KiB; a user-owned playlist with ~250+ tracks (each item carries a full PluginTrack
    // plus 3 menu entries) already crossed that with realistic content. 512 KiB stays well under
    // Binder's ~1 MiB transaction limit and pairs with MAX_ITEMS_PER_SECTION (500).
    const val MAX_JSON_BYTES = 512 * 1024
    const val MAX_DEPTH = 12
    const val MAX_SECTIONS = 40
    const val MAX_ITEMS_PER_SECTION = 500
    const val MAX_TRACKS_PER_ACTION = 500
    const val MAX_TEXT = 200
    const val MAX_TEXT_LONG = 4000
    const val MAX_URL = 2048
    const val MAX_MENU = 12
    const val MAX_TOOLBAR = 4
    const val MAX_TABS = 8
    const val MAX_CHOOSE = 40
    const val MAX_HERO_ACTIONS = 3
    const val MAX_QUICK_PLAY = 27
}

sealed interface PluginPageParseResult {
    data class Ok(val page: PluginPage) : PluginPageParseResult
    data class Error(val reason: String) : PluginPageParseResult
}

private val PLUGIN_ROUTE_ID = Regex("[A-Za-z0-9._-]{1,64}")
private val pageJson = Json { ignoreUnknownKeys = true; isLenient = false }

object PluginPageParser {

    fun parse(json: String): PluginPageParseResult {
        val byteSize = json.encodeToByteArray().size
        if (byteSize > PluginPageLimits.MAX_JSON_BYTES) return PluginPageParseResult.Error("size")
        if (bracketDepth(json) > PluginPageLimits.MAX_DEPTH) return PluginPageParseResult.Error("depth")

        val page = try {
            pageJson.decodeFromString(PluginPage.serializer(), json)
        } catch (e: SerializationException) {
            return PluginPageParseResult.Error("malformed: ${e.message}")
        } catch (e: IllegalArgumentException) {
            return PluginPageParseResult.Error("malformed: ${e.message}")
        }

        return PluginPageParseResult.Ok(sanitize(page))
    }

    // Cheap depth pre-scan: counts nested {/[ ignoring chars inside JSON strings.
    private fun bracketDepth(raw: String): Int {
        var depth = 0
        var maxDepth = 0
        var inString = false
        var escaped = false
        for (c in raw) {
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '{', '[' -> {
                    depth++
                    if (depth > maxDepth) maxDepth = depth
                }
                '}', ']' -> depth--
            }
        }
        return maxDepth
    }

    private fun sanitize(page: PluginPage): PluginPage = page.copy(
        title = page.title.truncate(),
        subtitle = page.subtitle?.truncate(),
        search = page.search?.sanitize(),
        sections = page.sections.take(PluginPageLimits.MAX_SECTIONS)
            .filter { it.kind in KNOWN_SECTION_KINDS }
            .map { it.sanitize() },
        empty = page.empty?.sanitize(),
        toolbar = page.toolbar.take(PluginPageLimits.MAX_TOOLBAR).mapNotNull { it.sanitize() },
        tabs = page.tabs?.sanitize(),
        auth = page.auth?.sanitize(),
        layout = if (page.layout in KNOWN_LAYOUTS) page.layout else "DETAIL",
        accentColor = page.accentColor?.takeIf { STRICT_RGB_HEX.matches(it) },
        hero = page.hero?.sanitize(),
        contentType = page.contentType.takeIf { it in KNOWN_CONTENT_TYPES },
    )

    private fun PluginHero.sanitize(): PluginHero = copy(
        title = title.truncate(),
        subtitle = subtitle?.truncate(),
        meta = meta?.truncate(),
        image = image?.sanitize(),
        actions = actions.take(PluginPageLimits.MAX_HERO_ACTIONS).mapNotNull { it.sanitize() },
    )

    private fun PluginButton.sanitize(): PluginButton? {
        val sanitizedAction = action.sanitize() ?: return null
        return copy(label = label.truncate(), action = sanitizedAction, role = PluginPageSanitizer.sanitizeRole(role))
    }

    private fun PluginTabStrip.sanitize(): PluginTabStrip = copy(tabs = tabs.take(PluginPageLimits.MAX_TABS).map { it.sanitize() })

    private fun PluginTab.sanitize(): PluginTab = copy(label = label.truncate())

    private fun PluginAuthRequired.sanitize(): PluginAuthRequired = copy(message = message.truncate(), label = label.truncate())

    private fun PluginSearchBox.sanitize(): PluginSearchBox = copy(placeholder = placeholder.truncate())

    private fun PluginEmptyState.sanitize(): PluginEmptyState = copy(
        title = title.truncate(),
        message = message?.truncate(),
        action = action?.sanitize(),
    )

    private fun PluginSection.sanitize(): PluginSection {
        val itemCap = if (kind == "QUICK_PLAY") PluginPageLimits.MAX_QUICK_PLAY else PluginPageLimits.MAX_ITEMS_PER_SECTION
        return copy(
            header = header?.sanitize(),
            items = items.take(itemCap).map { it.sanitize(longText = kind == "TEXT") },
            empty = empty?.sanitize(),
            error = error?.sanitize(),
        )
    }

    private fun PluginSectionHeader.sanitize(): PluginSectionHeader = copy(
        title = title.truncate(),
        action = action?.sanitize(),
    )

    private fun PluginItem.sanitize(longText: Boolean = false): PluginItem {
        val sanitizedAction = action?.sanitize()
        return copy(
            title = if (longText) title.truncate(PluginPageLimits.MAX_TEXT_LONG) else title.truncate(),
            subtitle = subtitle?.truncate(),
            image = image?.sanitize(),
            badge = badge?.truncate(),
            action = sanitizedAction,
            actionRejected = action != null && sanitizedAction == null,
            menu = menu.take(PluginPageLimits.MAX_MENU).mapNotNull { it.sanitize() },
            track = track?.sanitize(),
            contentType = contentType.takeIf { it in KNOWN_CONTENT_TYPES },
            year = year?.truncate(),
            meta = meta?.truncate(),
            formatTier = PluginPageSanitizer.sanitizeFormatTier(formatTier),
            discNumber = PluginPageSanitizer.sanitizeDiscNumber(discNumber),
            value = value?.takeIf { !it.isNaN() && !it.isInfinite() },
            minValue = minValue?.takeIf { !it.isNaN() && !it.isInfinite() },
            maxValue = maxValue?.takeIf { !it.isNaN() && !it.isInfinite() },
            step = step?.takeIf { !it.isNaN() && !it.isInfinite() && it >= 0f },
        )
    }

    fun parseItem(item: PluginItem, longText: Boolean = false): PluginItem = item.sanitize(longText)

    fun parseItem(json: String, longText: Boolean = false): PluginItem? = runCatching {
        pageJson.decodeFromString(PluginItem.serializer(), json).sanitize(longText)
    }.getOrNull()

    fun parseTrack(track: PluginTrack): PluginTrack = track.sanitize()

    fun parseTrack(json: String): PluginTrack? = runCatching {
        pageJson.decodeFromString(PluginTrack.serializer(), json).sanitize()
    }.getOrNull()

    private fun PluginTrack.sanitize(): PluginTrack = PluginPageSanitizer.sanitizeTrack(this)

    private fun PluginMenuEntry.sanitize(): PluginMenuEntry? = PluginPageSanitizer.sanitizeMenuEntry(this)

    private fun PluginImage.sanitize(): PluginImage? = takeIf { PluginPageSanitizer.isValidImageUrl(url) }

    private fun PluginAction.sanitize(): PluginAction? = PluginPageSanitizer.sanitizeAction(this)

    private fun String.truncate(limit: Int = PluginPageLimits.MAX_TEXT): String = PluginPageSanitizer.truncate(this, limit)

    private val KNOWN_SECTION_KINDS = setOf("LIST", "GRID", "SHELF", "CHIPS", "RADIO", "TEXT", "BUTTONS", "QUICK_PLAY", "HERO_CAROUSEL", "SWITCH", "KEY_VALUE", "SLIDER")
    private val KNOWN_LAYOUTS = setOf("ROOT", "DETAIL")
    private val KNOWN_CONTENT_TYPES = setOf("TRACK", "ALBUM", "ARTIST", "PLAYLIST")
    private val STRICT_RGB_HEX = Regex("#[0-9A-Fa-f]{6}")
}

internal object PluginPageSanitizer {
    fun sanitizeTrack(track: PluginTrack): PluginTrack = track.copy(
        title = truncate(track.title),
        artist = truncate(track.artist),
        album = truncate(track.album),
        artworkUrl = track.artworkUrl?.takeIf(::isValidImageUrl),
        quality = track.quality?.let { truncate(it) },
        formatTier = sanitizeFormatTier(track.formatTier),
        discNumber = sanitizeDiscNumber(track.discNumber),
    )

    fun sanitizeMenuEntry(entry: PluginMenuEntry): PluginMenuEntry? {
        val action = sanitizeAction(entry.action) ?: return null
        return entry.copy(label = truncate(entry.label), action = action, role = sanitizeRole(entry.role))
    }

    fun sanitizeAction(action: PluginAction): PluginAction? {
        if (action.type !in KNOWN_ACTION_TYPES) return null
        if (action.routeId != null && !PLUGIN_ROUTE_ID.matches(action.routeId)) return null
        if (action.type == "openUrl" && (action.url == null || !isValidImageUrl(action.url))) return null
        if (action.type == "openActivity" && action.component.isNullOrBlank()) return null
        return action.copy(tracks = action.tracks?.take(PluginPageLimits.MAX_TRACKS_PER_ACTION)?.map(::sanitizeTrack))
    }

    fun sanitizeRole(role: String?): String? = role?.takeIf { it in KNOWN_ROLES }

    fun isValidImageUrl(url: String): Boolean =
        url.startsWith("https://") && url.length <= PluginPageLimits.MAX_URL

    fun truncate(value: String, limit: Int = PluginPageLimits.MAX_TEXT): String =
        if (value.length > limit) value.take(limit) else value

    private val PLUGIN_ROUTE_ID = Regex("[A-Za-z0-9._-]{1,64}")
    private val KNOWN_ACTION_TYPES = setOf("navigate", "play", "enqueue", "playNext", "dispatch", "openUrl", "openActivity")
    fun sanitizeFormatTier(tier: String?): String? = tier?.takeIf { it in KNOWN_FORMAT_TIERS }
    fun sanitizeDiscNumber(discNumber: Int?): Int? = discNumber?.coerceIn(1, 99)

    val KNOWN_FORMAT_TIERS = setOf("HI_RES", "LOSSLESS", "DSD", "LOSSY")
    private val KNOWN_ROLES = setOf("favourite", "addToPlaylist", "remove", "play", "shuffle", "more")
}

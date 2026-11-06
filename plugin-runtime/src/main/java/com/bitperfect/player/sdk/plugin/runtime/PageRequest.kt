package com.bitperfect.player.sdk.plugin.runtime

import com.bitperfect.player.sdk.plugin.dto.PageDescriptorDto
import org.json.JSONObject

/** Parsed renderPage call: host-owned `_bp_*` keys are removed from [params] and [requestId] is split out. */
data class PageRequest(
    val routeId: String,
    val params: Map<String, String>,
    val pageToken: String?,
    val requestId: String?,
    internal val rawParamsJson: String? = null,
)

/** Parsed dispatchAction call; same params rules as [PageRequest]. */
data class ActionRequest(
    val actionId: String,
    val params: Map<String, String>,
    val requestId: String?,
    internal val rawParamsJson: String? = null,
)

private const val HOST_KEY_PREFIX = "_bp_"

/** Params with host `_bp_*` keys removed, and the host request id. One JSON parse per call. */
internal class ParsedParams(val params: Map<String, String>, val requestId: String?)

internal fun parseParams(paramsJson: String?): ParsedParams {
    if (paramsJson.isNullOrBlank()) return ParsedParams(emptyMap(), null)
    return try {
        val obj = JSONObject(paramsJson)
        val requestId = obj.optString(PageDescriptorDto.PARAM_REQUEST_ID).takeIf { it.isNotEmpty() }
        val params = buildMap {
            for (key in obj.keys()) {
                if (!key.startsWith(HOST_KEY_PREFIX)) put(key, obj.get(key).toString())
            }
        }
        ParsedParams(params, requestId)
    } catch (_: Exception) {
        ParsedParams(emptyMap(), null)
    }
}

/** Flat string map of a params JSON object without host `_bp_*` keys; nested values keep their JSON text. Bad input gives empty. */
fun String?.toParamsMap(): Map<String, String> = parseParams(this).params

/** JSON `pages.invalidate` events for `IPluginHostCallbackV1.postEvent`. */
object PageEvents {
    /** Invalidates every cached page of the plugin. */
    fun invalidateAll(): String = """{"type":"pages.invalidate","routes":"*"}"""

    /** Invalidates the named routes only. */
    fun invalidate(routes: Collection<String>): String =
        routes.joinToString(",", """{"type":"pages.invalidate","routes":[""", "]}") { "\"" + it.replace("\\", "\\\\").replace("\"", "\\\"") + "\"" }
}

/** Offset cursor used as a page token: a plain decimal offset. */
object OffsetPageToken {
    /** Offset in 0..[max]; null, blank or malformed gives 0. */
    fun parse(token: String?, max: Int = Int.MAX_VALUE): Int = token?.toIntOrNull()?.coerceIn(0, max) ?: 0

    /** Token for the next page, or null when [count] is 0, the end is reached ([total] known) or the offset would overflow Int. */
    fun nextPageToken(offset: Int, count: Int, total: Int?): String? {
        if (count <= 0) return null
        val next = offset.toLong() + count
        if (next > Int.MAX_VALUE || (total != null && next >= total)) return null
        return next.toString()
    }
}

package com.bitperfect.player.core.plugin.page

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

// ActionResultDto.kind + json deserialized into this — see phase1-interfaces.md.

sealed interface PluginActionResult {
    data object None : PluginActionResult
    data object Refresh : PluginActionResult
    data class Navigate(val routeId: String, val params: Map<String, String>) : PluginActionResult
    data class Choose(val title: String, val options: List<PluginMenuEntry>) : PluginActionResult
    data class Toast(val text: String) : PluginActionResult
    data class AuthRequired(val message: String, val activity: String?) : PluginActionResult
}

@Serializable
private data class NavigateBody(val routeId: String, val params: Map<String, String> = emptyMap())

@Serializable
private data class ChooseBody(val title: String, val options: List<PluginMenuEntry> = emptyList())

@Serializable
private data class ToastBody(val text: String)

@Serializable
private data class AuthRequiredBody(val message: String, val activity: String? = null)

private val PLUGIN_ROUTE_ID = Regex("[A-Za-z0-9._-]{1,64}")
private val actionResultJson = Json { ignoreUnknownKeys = true; isLenient = false }

object PluginActionResultParser {

    fun parse(kind: String, json: String): PluginActionResult = try {
        when (kind) {
            "NONE" -> PluginActionResult.None
            "REFRESH" -> PluginActionResult.Refresh
            "NAVIGATE" -> {
                val body = actionResultJson.decodeFromString(NavigateBody.serializer(), json)
                if (PLUGIN_ROUTE_ID.matches(body.routeId)) {
                    PluginActionResult.Navigate(body.routeId, body.params)
                } else {
                    PluginActionResult.None
                }
            }
            "CHOOSE" -> {
                val body = actionResultJson.decodeFromString(ChooseBody.serializer(), json)
                PluginActionResult.Choose(
                    title = PluginPageSanitizer.truncate(body.title),
                    options = body.options
                        .take(PluginPageLimits.MAX_CHOOSE)
                        .mapNotNull(PluginPageSanitizer::sanitizeMenuEntry),
                )
            }
            "TOAST" -> PluginActionResult.Toast(actionResultJson.decodeFromString(ToastBody.serializer(), json).text)
            "AUTH_REQUIRED" -> {
                val body = actionResultJson.decodeFromString(AuthRequiredBody.serializer(), json)
                PluginActionResult.AuthRequired(body.message, body.activity)
            }
            else -> PluginActionResult.None
        }
    } catch (e: SerializationException) {
        PluginActionResult.None
    } catch (e: IllegalArgumentException) {
        PluginActionResult.None
    }
}

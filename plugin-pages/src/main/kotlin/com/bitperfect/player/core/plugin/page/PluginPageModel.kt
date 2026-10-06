package com.bitperfect.player.core.plugin.page

import com.bitperfect.player.sdk.plugin.dto.PageDescriptorDto
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
// Page JSON v1 wire model — see openspec/specs/plugin-extension-points/spec.md.
const val PARAM_HOST_REFRESH = "_hostRefresh"
const val PARAM_HOST_TRANSPORT = "_bp_host_transport"
const val TRANSPORT_TIERED = "tiered"


@Serializable
data class PluginPage(
    val v: Int = 1,
    val title: String,
    val subtitle: String? = null,
    val search: PluginSearchBox? = null,
    val sections: List<PluginSection> = emptyList(),
    val empty: PluginEmptyState? = null,
    val toolbar: List<PluginButton> = emptyList(),
    val tabs: PluginTabStrip? = null,
    val auth: PluginAuthRequired? = null,
    val layout: String = "DETAIL",
    val showRootNavigation: Boolean = true,
    val hero: PluginHero? = null,
    val accentColor: String? = null,
    val contentType: String? = null,
)

fun PluginPage.toDescriptorDto(baseVersion: Int = 1, json: Json = Json): PageDescriptorDto =
    PageDescriptorDto.pack(json.encodeToString(this), baseVersion)

@Serializable
data class PluginHero(
    val title: String,
    val subtitle: String? = null,
    val meta: String? = null,
    val image: PluginImage? = null,
    val actions: List<PluginButton> = emptyList(),
    val subtitleAction: PluginAction? = null,
)

@Serializable
data class PluginButton(
    val id: String,
    val label: String,
    val icon: String? = null,
    val action: PluginAction,
    val style: String = "SECONDARY",
    val role: String? = null,
)

@Serializable
data class PluginTabStrip(
    val selectedId: String,
    val tabs: List<PluginTab>,
)

@Serializable
data class PluginTab(
    val id: String,
    val label: String,
    val params: Map<String, String> = emptyMap(),
)

@Serializable
data class PluginAuthRequired(
    val message: String,
    val activity: String? = null,
    val label: String = "Sign in",
)

@Serializable
data class PluginSection(
    val id: String,
    val kind: String,
    val header: PluginSectionHeader? = null,
    val items: List<PluginItem> = emptyList(),
    val nextPageToken: String? = null,
    val selectedId: String? = null,
    val empty: PluginEmptyState? = null,
    val compact: Boolean = false,
    val loading: Boolean = false,
    val error: PluginEmptyState? = null,
)

@Serializable
data class PluginSectionHeader(
    val title: String,
    val action: PluginAction? = null,
)

@Serializable
data class PluginItem(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val image: PluginImage? = null,
    val badge: String? = null,
    val action: PluginAction? = null,
    val track: PluginTrack? = null,
    val enabled: Boolean = true,
    val menu: List<PluginMenuEntry> = emptyList(),
    val trackNumber: Int? = null,
    val year: String? = null,
    val count: Int? = null,
    val meta: String? = null,
    val favourite: Boolean? = null,
    val contentType: String? = null,
    val formatTier: String? = null,
    val discNumber: Int? = null,
    val value: Float? = null,
    val minValue: Float? = null,
    val maxValue: Float? = null,
    val step: Float? = null,
    @Transient val actionRejected: Boolean = false,
)

@Serializable
data class PluginMenuEntry(
    val label: String,
    val icon: String? = null,
    val action: PluginAction,
    val role: String? = null,
)

@Serializable
data class PluginImage(
    val url: String,
    val shape: String = "SQUARE",
)

@Serializable
data class PluginTrack(
    val trackId: String,
    val albumId: String? = null,
    val title: String,
    val artist: String,
    val album: String = "",
    val durationMs: Long = 0,
    val artworkUrl: String? = null,
    val quality: String? = null,
    val formatTier: String? = null,
    val discNumber: Int? = null,
)

@Serializable
data class PluginAction(
    val type: String,
    val routeId: String? = null,
    val params: Map<String, String>? = null,
    val tracks: List<PluginTrack>? = null,
    val startIndex: Int = 0,
    val actionId: String? = null,
    val url: String? = null,
    val component: String? = null,
    val confirm: PluginConfirm? = null,
    val prompt: PluginPrompt? = null,
)

@Serializable
data class PluginConfirm(
    val title: String,
    val message: String? = null,
    val destructive: Boolean = false,
    val confirmLabel: String = "OK",
)

@Serializable
data class PluginPrompt(
    val label: String,
    val placeholder: String? = null,
    val initial: String? = null,
    val maxLength: Int = 200,
)

@Serializable
data class PluginSearchBox(
    val placeholder: String,
    val routeId: String,
    val paramKey: String = "q",
    val minChars: Int = 2,
)

@Serializable
data class PluginEmptyState(
    val title: String,
    val message: String? = null,
    val action: PluginButton? = null,
)

package com.bitperfect.player.core.plugin.page

import kotlinx.serialization.Serializable

@Serializable
data class PluginCatalogCapabilities(
    val supportedFavorites: Set<PluginFavoriteType> = emptySet(),
    val routes: Map<String, String> = emptyMap(),
) {
    fun supports(type: PluginFavoriteType): Boolean = type in supportedFavorites
}

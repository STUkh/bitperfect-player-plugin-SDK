package com.bitperfect.player.core.plugin.page

object PluginFavoriteActions {
    const val ACTION_FAVORITE = "favourite"
    const val ACTION_FAVORITE_STATE = "favourite.state"
    const val ACTION_CAPABILITIES = "catalog.capabilities"
    const val PARAM_KIND = "kind"
    const val PARAM_ID = "id"
    const val PARAM_ON = "on"

    const val FAVORITE = ACTION_FAVORITE
    const val FAVORITE_STATE = ACTION_FAVORITE_STATE
    const val CAPABILITIES = ACTION_CAPABILITIES
}

const val ACTION_FAVORITE = PluginFavoriteActions.ACTION_FAVORITE
const val ACTION_FAVORITE_STATE = PluginFavoriteActions.ACTION_FAVORITE_STATE
const val ACTION_CAPABILITIES = PluginFavoriteActions.ACTION_CAPABILITIES
const val PARAM_KIND = PluginFavoriteActions.PARAM_KIND
const val PARAM_ID = PluginFavoriteActions.PARAM_ID
const val PARAM_ON = PluginFavoriteActions.PARAM_ON

fun pluginFavoriteAction(type: PluginFavoriteType, id: String, targetOn: Boolean): PluginAction =
    PluginAction(
        type = "dispatch",
        actionId = ACTION_FAVORITE,
        params = mapOf(
            PARAM_KIND to type.wireName,
            PARAM_ID to id,
            PARAM_ON to targetOn.toString(),
        ),
    )

fun pluginFavoriteButton(
    type: PluginFavoriteType,
    id: String,
    isFavorite: Boolean,
    label: String = "",
    buttonId: String = "fav_${type.wireName}_$id",
): PluginButton =
    PluginButton(
        id = buttonId,
        label = label,
        icon = if (isFavorite) "HEART_FILLED" else "HEART",
        action = pluginFavoriteAction(type = type, id = id, targetOn = !isFavorite),
        role = "favourite",
    )

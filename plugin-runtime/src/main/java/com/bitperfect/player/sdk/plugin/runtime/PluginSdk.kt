package com.bitperfect.player.sdk.plugin.runtime

/** Public constants of the Bitperfect plugin SDK, API level 6. */
object PluginSdk {
    const val API_VERSION = 6

    /** Intent action a plugin service must declare in its intent filter. */
    const val ACTION_BIND_PLUGIN = "com.bitperfect.plugin.BIND_PLUGIN"

    /** Service <meta-data> keys read by the host during discovery. */
    const val META_PLUGIN_ID = "com.bitperfect.plugin.PLUGIN_ID"
    const val META_CAPABILITIES = "com.bitperfect.plugin.CAPABILITIES"
    const val META_MIN_HOST_API = "com.bitperfect.plugin.MIN_HOST_API"

    /** Fully-qualified class name of the plugin's own settings Activity; declared alongside [CAPABILITY_SETTINGS]. */
    const val META_SETTINGS_ACTIVITY = "com.bitperfect.plugin.SETTINGS_ACTIVITY"
    const val META_NAV_EXTENSION_ID = "com.bitperfect.plugin.NAVIGATION_EXTENSION_ID"
    const val META_NAV_TARGET = "com.bitperfect.plugin.NAVIGATION_TARGET"
    const val META_NAV_LABEL = "com.bitperfect.plugin.NAVIGATION_LABEL"
    const val META_NAV_ACTIVITY = "com.bitperfect.plugin.NAVIGATION_ACTIVITY"
    const val META_NAV_WEIGHT = "com.bitperfect.plugin.NAVIGATION_ORDER"
    const val NAV_TARGET_SIDEBAR = "sidebar"

    /** Declarative page route metadata keys, declared alongside [CAPABILITY_PAGES]. */
    const val META_NAV_ROUTE = "com.bitperfect.plugin.NAVIGATION_ROUTE"
    const val META_SETTINGS_ROUTE = "com.bitperfect.plugin.SETTINGS_ROUTE"

    /** Capability bits — must match Capability in the host's plugin-api. */
    const val CAPABILITY_SOURCE = 1
    const val CAPABILITY_METADATA = 1 shl 1
    const val CAPABILITY_OUTPUT_RENDERER = 1 shl 2
    const val CAPABILITY_UI_TABS = 1 shl 3
    const val CAPABILITY_NOW_PLAYING_EXT = 1 shl 4
    const val CAPABILITY_SETTINGS = 1 shl 5
    const val CAPABILITY_AUTH = 1 shl 6

    /** Browsable cloud library. Declared together with [CAPABILITY_SOURCE]. */
    const val CAPABILITY_CLOUD_SOURCE = 1 shl 7

    /** Plugin can render declarative pages via renderPage/dispatchAction. */
    const val CAPABILITY_PAGES = 1 shl 8

    // Plugin performs audio DSP processing in the audio pipeline.
    const val CAPABILITY_DSP = 1 shl 9

    /** Plugin acts as a remote-control receiver / cast target. */
    const val CAPABILITY_RECEIVER = 1 shl 10
}

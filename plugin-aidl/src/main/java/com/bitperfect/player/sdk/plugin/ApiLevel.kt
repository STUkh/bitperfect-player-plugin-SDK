package com.bitperfect.player.sdk.plugin

/** Wire-contract API levels; the host and every plugin compare against these names, never bare numbers. */
object ApiLevel {
    /** Level this SDK build speaks; plugins report it from getApiVersion, the host advertises it. */
    const val CURRENT = 9

    /** Oldest plugin level the host still binds. */
    const val MIN_PLUGIN = 1

    /** Host callback binder (registerHostCallback) exists. */
    const val HOST_CALLBACK = 2

    /** renderPage/dispatchAction exist. */
    const val PAGES = 4

    /** Receiver session, volume and error callbacks. */
    const val RECEIVER = 7

    /** cancelRequest and `_bp_request_id` in paramsJson. */
    const val CANCEL_REQUEST = 9

    /** Level reported by the renderer and DSP bases: their cancelRequest is a no-op, so they do not claim 9. */
    const val RENDERER_DEFAULT = 7
}

/** Host-side page-call limits. Fixed by the contract; a plugin cannot change them. */
object PluginCallLimits {
    const val RENDER_PAGE_MS = 120_000L
    const val DISPATCH_ACTION_MS = 60_000L

    /** Host waits the plugin limit plus this, so the plugin deadline fires first. */
    const val HOST_MARGIN_MS = 10_000L

    /** Hard host limits for plugins below [ApiLevel.CANCEL_REQUEST]: they cannot be told to stop. */
    const val LEGACY_RENDER_PAGE_MS = 10_000L
    const val LEGACY_DISPATCH_ACTION_MS = 15_000L

    /** Real binder calls per plugin at one time. */
    const val MAX_IN_FLIGHT = 4
}

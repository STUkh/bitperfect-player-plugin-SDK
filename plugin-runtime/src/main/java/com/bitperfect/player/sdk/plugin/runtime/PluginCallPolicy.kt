package com.bitperfect.player.sdk.plugin.runtime

import com.bitperfect.player.sdk.plugin.PluginCallLimits

/** Single source of per-call deadlines; the host waits plugin limit + [HOST_MARGIN_MS]. */
data class PluginCallPolicy(
    // Playback-start latency: keep short.
    val resolveStreamMs: Long = 20_000L,
    val browseMs: Long = 60_000L,
    val httpCallMs: Long = 60_000L,
    // Background sync has no overall deadline, only this no-progress watchdog.
    val syncStallMs: Long = 300_000L,
    val rendererMs: Long = 10_000L,
    val rendererControlMs: Long = 5_000L,
) {
    /** Contract-fixed ([PluginCallLimits]), not constructor parameters: a plugin cannot change them. */
    val renderPageMs: Long get() = PluginCallLimits.RENDER_PAGE_MS
    val dispatchActionMs: Long get() = PluginCallLimits.DISPATCH_ACTION_MS

    /** Host-side limit for a plugin-side limit. */
    fun hostLimitMs(pluginMs: Long): Long = pluginMs + HOST_MARGIN_MS

    companion object {
        const val HOST_MARGIN_MS = PluginCallLimits.HOST_MARGIN_MS
        val Default = PluginCallPolicy()
    }
}

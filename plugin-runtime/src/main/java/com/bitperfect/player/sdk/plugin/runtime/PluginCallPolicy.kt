package com.bitperfect.player.sdk.plugin.runtime

/** Single source of per-call deadlines; the host waits plugin limit + [HOST_MARGIN_MS]. */
data class PluginCallPolicy(
    val renderPageMs: Long = 120_000L,
    val dispatchActionMs: Long = 60_000L,
    // Playback-start latency: keep short.
    val resolveStreamMs: Long = 20_000L,
    val browseMs: Long = 60_000L,
    val httpCallMs: Long = 60_000L,
    // Background sync has no overall deadline, only this no-progress watchdog.
    val syncStallMs: Long = 300_000L,
    val rendererMs: Long = 10_000L,
    val rendererControlMs: Long = 5_000L,
) {
    /** Host-side limit for a plugin-side limit. */
    fun hostLimitMs(pluginMs: Long): Long = pluginMs + HOST_MARGIN_MS

    companion object {
        const val HOST_MARGIN_MS = 10_000L
        val Default = PluginCallPolicy()
    }
}

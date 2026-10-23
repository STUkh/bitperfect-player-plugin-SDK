package com.bitperfect.player.sdk.plugin.runtime

import com.bitperfect.player.core.plugin.page.ServicePlaylist
import com.bitperfect.player.core.plugin.page.ServicePlaylistCodec
import com.bitperfect.player.sdk.plugin.dto.ActionResultDto
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** `playlist.list` reply. */
fun List<ServicePlaylist>.toListResult(): ActionResultDto =
    ActionResultDto(ActionResultDto.KIND_NONE, ServicePlaylistCodec.encode(this))

const val SERVICE_PLAYLISTS_TTL_MS = 10 * 60 * 1_000L

/** The account's editable playlists: single-flight load, TTL, write-through updates. */
class ServicePlaylistCache(
    private val ttlMs: Long = SERVICE_PLAYLISTS_TTL_MS,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private val lock = Any()
    private var value: List<ServicePlaylist>? = null
    private var storedAt = 0L
    private var generation = 0

    /** A load that races [invalidate] is returned but not stored. */
    suspend fun get(load: suspend () -> List<ServicePlaylist>): List<ServicePlaylist> = mutex.withLock {
        peek()?.let { return it }
        val started = synchronized(lock) { generation }
        load().also { loaded -> synchronized(lock) { if (generation == started) store(loaded) } }
    }

    fun peek(): List<ServicePlaylist>? = synchronized(lock) { value?.takeIf { clock() - storedAt < ttlMs } }

    fun title(id: String): String? = peek()?.firstOrNull { it.id == id }?.title

    fun seed(playlists: List<ServicePlaylist>) = synchronized(lock) { store(playlists) }

    fun created(playlist: ServicePlaylist) = update { listOf(playlist) + it }

    fun added(id: String, tracks: Int = 1) = update { list ->
        list.map { if (it.id == id) it.copy(count = it.count?.plus(tracks)) else it }
    }

    fun renamed(id: String, title: String) = update { list -> list.map { if (it.id == id) it.copy(title = title) else it } }

    fun removed(id: String) = update { list -> list.filter { it.id != id } }

    fun invalidate() = synchronized(lock) {
        generation++
        value = null
    }

    private fun store(playlists: List<ServicePlaylist>) {
        value = playlists
        storedAt = clock()
    }

    // No-op when empty or expired: the next get reloads.
    private fun update(transform: (List<ServicePlaylist>) -> List<ServicePlaylist>) = synchronized(lock) {
        value?.takeIf { clock() - storedAt < ttlMs }?.let { value = transform(it) }
    }
}

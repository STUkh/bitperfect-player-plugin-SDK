package com.bitperfect.player.sdk.plugin.runtime

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Server view of a list: item count and the newest item id. */
data class LibraryHead(val total: Int, val headId: String?)

/** Remote list, newest first. */
interface LibraryPager<T> {
    suspend fun probe(): LibraryHead

    suspend fun page(offset: Int, limit: Int): List<T>
}

/** Local staging area beside the visible table; [swap] must be one transaction. */
interface StagingStore<T> {
    fun getMeta(key: String): String?

    fun setMeta(key: String, value: String)

    fun deleteMeta(key: String)

    fun stagingCount(): Int

    fun clearStaging()

    /** [chunk] is in server order (newest first); [position] is the slot of its last item, the first item takes the highest. */
    fun stage(chunk: List<T>, position: Int)

    /** Replaces the visible table with staging and clears the cursor. */
    fun swap(total: Int)

    fun isUpToDate(total: Int, head: String?): Boolean
}

/** Resumable list sync: chunks go to staging, one transaction swaps them in; the visible table is never cleared first. */
class StagedLibrarySync<T>(
    private val pager: LibraryPager<T>,
    private val store: StagingStore<T>,
    private val scope: CoroutineScope,
    private val stallMs: Long = PluginCallPolicy.Default.syncStallMs,
    private val pageSize: Int,
    private val cap: Int = Int.MAX_VALUE,
    private val onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
) {
    private val lock = Any()
    private var job: Job? = null

    val isRunning: Boolean get() = synchronized(lock) { job?.isActive == true }

    /** No overall deadline: only a step that makes no progress for [stallMs] fails the run. */
    private suspend fun <R> step(block: suspend () -> R): R = try {
        withTimeout(stallMs) { block() }
    } catch (e: TimeoutCancellationException) {
        throw IllegalStateException("Library sync stalled for $stallMs ms", e)
    }

    /** One job per account; a running job is returned as is, never restarted from zero. */
    fun ensureRunning(): Job = synchronized(lock) {
        job?.takeIf { it.isActive }?.let { return it }
        // LAZY: `job` is set before the body can finish and clear itself.
        val started = scope.launch(start = CoroutineStart.LAZY) { run() }
        job = started
        started.start()
        started
    }

    private suspend fun run() {
        try {
            sync()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Failure keeps staging + cursor for the next ensureRunning; visible table untouched.
        }
    }

    private suspend fun sync() {
        val probe = step { pager.probe() }
        val serverTotal = probe.total
        val head = probe.headId
        currentCoroutineContext().ensureActive()

        val cursorTotal = store.getMeta(KEY_TOTAL)?.toIntOrNull()
        val cursorHead = store.getMeta(KEY_HEAD_ID)
        var offset = store.getMeta(KEY_OFFSET)?.toIntOrNull() ?: 0
        val resumable = cursorTotal == serverTotal && cursorHead == head && offset > 0 &&
            store.stagingCount() == offset
        if (!resumable) {
            if (store.isUpToDate(serverTotal, head)) return
            currentCoroutineContext().ensureActive()
            store.clearStaging()
            offset = 0
            store.setMeta(KEY_TOTAL, serverTotal.toString())
            head?.let { store.setMeta(KEY_HEAD_ID, it) } ?: store.deleteMeta(KEY_HEAD_ID)
            store.setMeta(KEY_OFFSET, "0")
        }

        val target = minOf(serverTotal, cap)
        while (offset < target) {
            val items = step { pager.page(offset, pageSize) }
            if (items.isEmpty()) break
            val chunk = items.take(target - offset)
            currentCoroutineContext().ensureActive()
            store.stage(chunk, target - offset - chunk.size)
            offset += chunk.size
            store.setMeta(KEY_OFFSET, offset.toString())
            onProgress(offset, serverTotal)
        }
        currentCoroutineContext().ensureActive()
        // Short read (server shrank mid-sync): keep the cursor, retry later; never swap a partial set.
        if (offset < target) return
        withContext(NonCancellable) { store.swap(serverTotal) }
        onProgress(offset, serverTotal)
    }

    companion object {
        // Stable keys: renaming them orphans persisted cursors.
        const val KEY_OFFSET = "sync_offset"
        const val KEY_TOTAL = "sync_total"
        const val KEY_HEAD_ID = "sync_head_id"
    }
}

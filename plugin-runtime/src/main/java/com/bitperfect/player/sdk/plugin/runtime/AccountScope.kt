package com.bitperfect.player.sdk.plugin.runtime

import java.io.Closeable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.CoroutineContext

/** Everything that belongs to one signed-in account; cancelling it stops all of it. [close] releases its storage. */
abstract class AccountScope(val userId: String, parent: CoroutineContext) : CoroutineScope, Closeable {
    override val coroutineContext: CoroutineContext = parent + SupervisorJob(parent[Job])

    @Volatile private var fillJob: Job? = null

    /** Replaces the playback fill; the old one is cancelled. */
    fun setFill(job: Job) {
        fillJob?.cancel()
        fillJob = job
    }

    fun cancelFill() {
        val j = fillJob
        j?.cancel()
        if (fillJob === j) fillJob = null
    }

    /** Runs a storage access as a child of this scope: teardown joins it before storage closes. Null when the account is gone. */
    suspend fun <T> guarded(block: () -> T): T? {
        val work = async { block() }
        return try {
            work.await()
        } catch (e: CancellationException) {
            currentCoroutineContext().ensureActive()
            null
        }
    }

    override fun close() = Unit
}

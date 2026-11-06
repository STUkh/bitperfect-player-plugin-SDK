package com.bitperfect.player.sdk.plugin.runtime

import com.bitperfect.player.sdk.plugin.dto.PageDescriptorDto
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

/** Tracks in-flight page requests by host-supplied id so the host can cancel them across AIDL. */
internal class PluginRequestRegistry(private val scope: CoroutineScope) {
    private val jobs = ConcurrentHashMap<String, Deferred<*>>()

    val size: Int get() = jobs.size

    /** Runs [block] in [scope] under [timeoutMs]; null on timeout, failure or [cancel]. */
    suspend fun <T> await(requestId: String?, timeoutMs: Long, block: suspend () -> T?): T? {
        // timeoutMs <= 0 = no deadline (withTimeout(0) would fail instantly).
        val work = scope.async { if (timeoutMs > 0L) withTimeout(timeoutMs) { block() } else block() }
        if (requestId != null) jobs[requestId] = work
        return try {
            work.await()
        } catch (e: CancellationException) {
            currentCoroutineContext().ensureActive()
            null
        } catch (_: Throwable) {
            null
        } finally {
            work.cancel()
            if (requestId != null) jobs.remove(requestId, work)
        }
    }

    /** The only runBlocking on a Binder thread: blocks it until [await] returns. */
    fun <T> runCancellable(requestId: String?, timeoutMs: Long, block: suspend () -> T?): T? =
        runBlocking { await(requestId, timeoutMs, block) }

    fun cancel(requestId: String) {
        jobs.remove(requestId)?.cancel()
    }

    companion object {
        fun requestIdOf(paramsJson: String?): String? {
            if (paramsJson == null || !paramsJson.contains(KEY)) return null
            return try {
                JSONObject(paramsJson).optString(KEY).takeIf { it.isNotEmpty() }
            } catch (_: Throwable) {
                null
            }
        }

        private const val KEY = PageDescriptorDto.PARAM_REQUEST_ID
    }
}

package com.bitperfect.player.sdk.plugin.runtime

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/** Thrown by [AccountScopes.require] when no account is signed in. */
class AuthRequiredException : IllegalStateException("Sign-in required")

/** Owns the single live [AccountScope]; any account change tears the old one down first. */
class AccountScopes<S : AccountScope>(
    private val parent: CoroutineContext,
    private val factory: (userId: String) -> S,
    private val deleteStorage: (userId: String) -> Unit,
    legacyCleanup: () -> Unit = {},
    private val authRequired: () -> Throwable = { AuthRequiredException() },
) {
    private val _current = MutableStateFlow<S?>(null)
    val current: StateFlow<S?> = _current.asStateFlow()

    private val lock = Any()
    private val logoutMutex = Mutex()
    private val teardownScope = CoroutineScope(parent + NonCancellable)
    private var pendingUserId: String? = null
    private var pending: Job? = null

    init {
        // Pre-account shared storage held the previous user's data; nothing reads it any more.
        runCatching(legacyCleanup)
    }

    fun require(): S = _current.value ?: throw authRequired()

    /** Opens [userId]'s scope; a different live account is torn down first. */
    suspend fun open(userId: String): S {
        val waitFor: Job?
        synchronized(lock) {
            _current.value?.takeIf { it.userId == userId }?.let { return it }
            detachLocked()
            waitFor = pending?.takeIf { pendingUserId == userId }
        }
        // Same account reopening: its files may still be going away.
        waitFor?.join()
        return synchronized(lock) {
            _current.value?.takeIf { it.userId == userId } ?: factory(userId).also { _current.value = it }
        }
    }

    /** Non-blocking: reference nulled now, cancel + join + delete runs in the background. */
    fun detach(): Job? = synchronized(lock) { detachLocked() }

    /** Full sign-out: waits for the teardown, then [clear] runs even if the teardown failed. */
    suspend fun logout(clear: suspend () -> Unit = {}) = logoutMutex.withLock {
        try {
            detach()?.join() ?: pending?.join()
        } finally {
            try {
                // The caller may itself be a child of the scope being torn down.
                withContext(NonCancellable) { clear() }
            } catch (e: Exception) {
                Log.w(TAG, "storage clear failed", e)
            }
        }
    }

    private fun detachLocked(): Job? {
        val old = _current.value ?: return null
        _current.value = null
        val job = teardownScope.launch {
            try {
                old.coroutineContext[Job]?.let { it.cancel(); it.join() }
            } finally {
                try {
                    old.close()
                } catch (e: Exception) {
                    Log.w(TAG, "scope close failed", e)
                }
                runCatching { deleteStorage(old.userId) }
            }
        }
        pending = job
        pendingUserId = old.userId
        return job
    }

    private companion object {
        const val TAG = "AccountScopes"
    }
}

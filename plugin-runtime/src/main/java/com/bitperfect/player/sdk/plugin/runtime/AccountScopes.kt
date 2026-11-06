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
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.coroutines.CoroutineContext

/** Thrown by [AccountScopes.require] when no account is signed in. */
class AuthRequiredException : IllegalStateException("Sign-in required")

/** Persisted id of the account whose data sits in the plugin's single storage. */
interface StorageOwner {
    fun read(): String?
    fun write(userId: String?)
}

/** In-memory [StorageOwner]; for tests and plugins without persistence. */
class MemoryStorageOwner(private var id: String? = null) : StorageOwner {
    override fun read() = id
    override fun write(userId: String?) { id = userId }
}

/** File-backed [StorageOwner]; atomic temp + rename write, null deletes the file. */
class FileStorageOwner(private val file: File) : StorageOwner {
    override fun read(): String? = runCatching { file.readText().trim().ifEmpty { null } }.getOrNull()

    override fun write(userId: String?) {
        if (userId == null) {
            Files.deleteIfExists(file.toPath())
            return
        }
        val tmp = File(file.path + ".tmp")
        tmp.writeText(userId)
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }
}

/** Leftover per-user DB names of the previous build: `<prefix><id>.db`, never the live [keep] name. */
fun legacyPerUserDatabases(names: List<String>, prefix: String, keep: String): List<String> =
    names.filter { it.startsWith(prefix) && it.endsWith(".db") && it != keep }

/**
 * Owns the single live [AccountScope]; any account change tears the old one down first.
 * One storage per plugin: [owner] names its account; a mismatch on open wipes it before the factory runs.
 */
class AccountScopes<S : AccountScope>(
    private val parent: CoroutineContext,
    private val factory: (userId: String) -> S,
    private val deleteStorage: () -> Unit,
    private val owner: StorageOwner,
    legacyCleanup: () -> Unit = {},
    private val authRequired: () -> Throwable = { AuthRequiredException() },
) {
    private val _current = MutableStateFlow<S?>(null)
    val current: StateFlow<S?> = _current.asStateFlow()

    private val lock = Any()
    private val logoutMutex = Mutex()
    private val teardownScope = CoroutineScope(parent + NonCancellable)
    private var pending: Job? = null

    init {
        // Per-user files of the previous build; nothing reads them any more.
        runCatching(legacyCleanup)
    }

    fun require(): S = _current.value ?: throw authRequired()

    /** Opens [userId]'s scope; a different live account is torn down first. */
    suspend fun open(userId: String): S {
        while (true) {
            val waitFor: Job?
            val opened: S? = synchronized(lock) {
                _current.value?.takeIf { it.userId == userId }?.let { return it }
                detachLocked()
                waitFor = pending?.takeIf { it.isActive }
                if (waitFor != null) null else openLocked(userId)
            }
            if (opened != null) return opened
            // Teardown of the old account may still be deleting the storage.
            waitFor?.join()
        }
    }

    // Owner is written only after the wipe; data is written only by a scope opened after it.
    private fun openLocked(userId: String): S {
        if (owner.read() != userId) {
            deleteStorage()
            owner.write(userId)
        }
        return factory(userId).also { _current.value = it }
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
                runCatching { deleteStorage(); owner.write(null) }
            }
        }
        pending = job
        return job
    }

    private companion object {
        const val TAG = "AccountScopes"
    }
}

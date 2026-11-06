package com.bitperfect.player.sdk.plugin.runtime

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

@Timeout(10, unit = TimeUnit.SECONDS)
class AccountScopesTest {
    private val events = mutableListOf<String>()
    private val made = HashMap<String, FakeScope>()

    private inner class FakeScope(id: String, parent: CoroutineContext) : AccountScope(id, parent) {
        var closed = false
        override fun close() { closed = true; events += "close:$userId" }
    }

    private val owner = MemoryStorageOwner()

    private fun TestScope.scopes(legacy: () -> Unit = {}, owner: StorageOwner = this@AccountScopesTest.owner) = AccountScopes(
        parent = StandardTestDispatcher(testScheduler),
        factory = { id -> FakeScope(id, StandardTestDispatcher(testScheduler)).also { made[id] = it } },
        deleteStorage = { events += "delete" },
        owner = owner,
        legacyCleanup = legacy,
    )

    @Test
    fun `logout cancels and joins work before close delete and clear`() = runTest {
        val scopes = scopes()
        val acct = scopes.open("1")
        val fill = acct.launch { try { awaitCancellation() } finally { events += "fill-finished" } }
        acct.setFill(fill)
        runCurrent()
        assertTrue(fill.isActive)

        scopes.logout { events += "clear" }

        assertTrue(fill.isCancelled)
        assertEquals(listOf("delete", "fill-finished", "close:1", "delete", "clear"), events)
        assertNull(scopes.current.value)
    }

    @Test
    fun `clear still runs and logout finishes when close fails`() = runTest {
        val scopes = AccountScopes(
            parent = StandardTestDispatcher(testScheduler),
            factory = { id -> object : AccountScope(id, StandardTestDispatcher(testScheduler)) {
                override fun close() = throw IllegalStateException("boom")
            } },
            deleteStorage = { events += "delete" },
            owner = owner,
        )
        scopes.open("1")

        scopes.logout { events += "clear"; throw IllegalStateException("clear boom") }

        assertEquals(listOf("delete", "delete", "clear"), events)
    }

    @Test
    fun `guarded work is joined and nothing lands after logout`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val scopes = scopes()
        val acct = scopes.open("1")
        var late: String? = "unset"
        acct.launch { gate.await(); late = acct.guarded { events += "write-after"; "x" } }
        runCurrent()

        scopes.logout()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals("unset", late)
        assertFalse("write-after" in events)
        assertNull(acct.guarded { "x" })
    }

    @Test
    fun `require throws after logout and uses the error factory`() = runTest {
        val scopes = scopes()
        scopes.open("1")
        assertEquals("1", scopes.require().userId)
        scopes.logout()
        assertThrows(AuthRequiredException::class.java) { scopes.require() }

        val custom = AccountScopes<FakeScope>(StandardTestDispatcher(testScheduler), { error("n/a") }, {}, MemoryStorageOwner(), authRequired = { IllegalArgumentException("x") })
        assertThrows(IllegalArgumentException::class.java) { custom.require() }
    }

    @Test
    fun `same user returns the same scope and a new user tears the old one down`() = runTest {
        val scopes = scopes()
        val first = scopes.open("1")
        assertSame(first, scopes.open("1"))

        val second = scopes.open("2")
        advanceUntilIdle()

        assertNotSame(first, second)
        assertFalse(first.isActive)
        assertTrue(made.getValue("1").closed)
        assertEquals(listOf("delete", "close:1", "delete", "delete"), events)
    }

    @Test
    fun `detach is non-blocking and legacy cleanup runs once at start`() = runTest {
        var legacy = 0
        val scopes = scopes(legacy = { legacy++ })
        scopes.open("1")
        val job = scopes.detach()

        assertNull(scopes.current.value)
        assertTrue(job != null && !job.isCompleted)
        advanceUntilIdle()
        assertTrue(job!!.isCompleted)
        assertEquals(1, legacy)
    }

    @Test
    fun `owner mismatch wipes storage before the factory runs`() = runTest {
        owner.write("old")
        val scopes = scopes()
        scopes.open("1")

        assertEquals(listOf("delete"), events)
        assertEquals("1", owner.read())
    }

    @Test
    fun `same owner keeps storage`() = runTest {
        owner.write("1")
        val scopes = scopes()
        scopes.open("1")

        assertTrue(events.isEmpty())
    }

    @Test
    fun `teardown deletes storage then writes a null owner`() = runTest {
        val scopes = scopes()
        scopes.open("1")
        events.clear()
        scopes.logout()

        assertEquals(listOf("close:1", "delete"), events)
        assertNull(owner.read())
    }

    @Test
    fun `crash between delete and owner write still wipes on the next open`() = runTest {
        val crashing = object : StorageOwner {
            override fun read() = owner.read()
            override fun write(userId: String?) = throw IllegalStateException("killed")
        }
        owner.write("old")
        val crashed = runCatching { scopes(owner = crashing).open("1") }.exceptionOrNull()
        assertTrue(crashed is IllegalStateException)
        assertEquals(listOf("delete"), events)
        assertEquals("old", owner.read())

        // Next process: owner still mismatches, so the storage is wiped again.
        scopes().open("1")
        assertEquals(listOf("delete", "delete"), events)
        assertEquals("1", owner.read())
    }

    @Test
    fun `logout then another user leaves a single storage wiped once per change`() = runTest {
        val scopes = scopes()
        scopes.open("1")
        scopes.logout()
        scopes.open("2")

        assertEquals(listOf("delete", "close:1", "delete", "delete"), events)
        assertEquals("2", owner.read())
    }

    @Test
    fun `legacy per-user names exclude the live database`() {
        val names = listOf("x_1.db", "x_cache.db", "x_9.db-journal", "y_2.db", "x_a.db")
        assertEquals(listOf("x_1.db", "x_a.db"), legacyPerUserDatabases(names, "x_", "x_cache.db"))
    }

    @Test
    fun `file owner round trips and null deletes the file`(@org.junit.jupiter.api.io.TempDir dir: java.io.File) {
        val o = FileStorageOwner(java.io.File(dir, "o"))
        assertNull(o.read())
        o.write("7")
        assertEquals("7", o.read())
        o.write("8")
        assertEquals("8", o.read())
        o.write(null)
        assertNull(o.read())
        assertEquals(0, dir.listFiles()!!.size)
    }
}

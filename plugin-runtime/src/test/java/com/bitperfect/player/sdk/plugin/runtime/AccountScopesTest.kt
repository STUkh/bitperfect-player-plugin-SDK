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

    private fun TestScope.scopes(legacy: () -> Unit = {}) = AccountScopes(
        parent = StandardTestDispatcher(testScheduler),
        factory = { id -> FakeScope(id, StandardTestDispatcher(testScheduler)).also { made[id] = it } },
        deleteStorage = { events += "delete:$it" },
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
        assertEquals(listOf("fill-finished", "close:1", "delete:1", "clear"), events)
        assertNull(scopes.current.value)
    }

    @Test
    fun `clear still runs and logout finishes when close fails`() = runTest {
        val scopes = AccountScopes(
            parent = StandardTestDispatcher(testScheduler),
            factory = { id -> object : AccountScope(id, StandardTestDispatcher(testScheduler)) {
                override fun close() = throw IllegalStateException("boom")
            } },
            deleteStorage = { events += "delete:$it" },
        )
        scopes.open("1")

        scopes.logout { events += "clear"; throw IllegalStateException("clear boom") }

        assertEquals(listOf("delete:1", "clear"), events)
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

        val custom = AccountScopes<FakeScope>(StandardTestDispatcher(testScheduler), { error("n/a") }, {}, authRequired = { IllegalArgumentException("x") })
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
        assertTrue("delete:1" in events)
        assertFalse("delete:2" in events)
    }

    @Test
    fun `detach is non-blocking and legacy cleanup runs once at start`() = runTest {
        var legacy = 0
        val scopes = scopes { legacy++ }
        scopes.open("1")
        val job = scopes.detach()

        assertNull(scopes.current.value)
        assertTrue(job != null && !job.isCompleted)
        advanceUntilIdle()
        assertTrue(job!!.isCompleted)
        assertEquals(1, legacy)
    }
}

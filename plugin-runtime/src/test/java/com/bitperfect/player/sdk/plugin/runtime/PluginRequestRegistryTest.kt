package com.bitperfect.player.sdk.plugin.runtime

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

@Timeout(10, unit = TimeUnit.SECONDS)
class PluginRequestRegistryTest {

    private fun TestScope.registry() = PluginRequestRegistry(CoroutineScope(StandardTestDispatcher(testScheduler)))

    @Test
    fun `cancelRequest cancels the block and the call returns null`() = runTest {
        val registry = registry()
        var cancelled = false
        val started = CompletableDeferred<Unit>()
        val call = async {
            registry.await<String>("r1", 60_000L) {
                started.complete(Unit)
                try { awaitCancellation() } finally { cancelled = true }
            }
        }
        started.await()
        assertEquals(1, registry.size)
        registry.cancel("r1")
        assertNull(call.await())
        runCurrent()
        assertTrue(cancelled)
        assertEquals(0, registry.size)
    }

    @Test
    fun `timeout on virtual time returns null and cancels the block`() = runTest {
        val registry = registry()
        var cancelled = false
        val call = async {
            registry.await<String>("r2", 1_000L) {
                try { delay(10_000L); "late" } finally { cancelled = true }
            }
        }
        assertNull(call.await())
        runCurrent()
        assertTrue(cancelled)
        assertEquals(0, registry.size)
    }

    @Test
    fun `zero timeout means no deadline`() = runTest {
        assertEquals("ok", registry().await(null, 0L) { "ok" })
    }

    @Test
    fun `registry is empty after normal completion and after failure`() = runTest {
        val registry = registry()
        assertEquals("ok", registry.await("r3", 1_000L) { "ok" })
        assertNull(registry.await<String>("r4", 1_000L) { error("boom") })
        assertEquals(0, registry.size)
    }

    @Test
    fun `requestIdOf reads the host param and tolerates junk`() {
        assertEquals("abc", PluginRequestRegistry.requestIdOf("""{"_bp_request_id":"abc","x":"1"}"""))
        assertNull(PluginRequestRegistry.requestIdOf("""{"x":"1"}"""))
        assertNull(PluginRequestRegistry.requestIdOf("not json _bp_request_id"))
        assertNull(PluginRequestRegistry.requestIdOf(null))
    }
}

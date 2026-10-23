package com.bitperfect.player.sdk.plugin.runtime

import com.bitperfect.player.core.plugin.page.ServicePlaylist
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ServicePlaylistCacheTest {
    private var now = 0L
    private val cache = ServicePlaylistCache(ttlMs = 100, clock = { now })
    private val a = ServicePlaylist("a", "A", 1)

    @Test
    fun `second get within ttl does not reload`() = runBlocking {
        var loads = 0
        cache.get { loads++; listOf(a) }
        cache.get { loads++; listOf(a) }
        assertEquals(1, loads)
    }

    @Test
    fun `expired entry reloads`() = runBlocking {
        var loads = 0
        cache.get { loads++; listOf(a) }
        now = 100
        cache.get { loads++; listOf(a) }
        assertEquals(2, loads)
    }

    @Test
    fun `invalidate during a load discards the stale result`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val first = async { cache.get { gate.await(); listOf(a) } }
        yield()
        cache.invalidate()
        gate.complete(Unit)
        assertEquals(listOf(a), first.await())
        var loads = 0
        cache.get { loads++; emptyList() }
        assertEquals(1, loads)
    }

    @Test
    fun `write-through updates`() {
        cache.seed(listOf(a))
        cache.created(ServicePlaylist("b", "B", 0))
        cache.added("a", 2)
        cache.renamed("b", "B2")
        assertEquals(listOf(ServicePlaylist("b", "B2", 0), ServicePlaylist("a", "A", 3)), cache.peek())
        assertEquals("B2", cache.title("b"))
        cache.removed("b")
        assertEquals(listOf(ServicePlaylist("a", "A", 3)), cache.peek())
    }
}

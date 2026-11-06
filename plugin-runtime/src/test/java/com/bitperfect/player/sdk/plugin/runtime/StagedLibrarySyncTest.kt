package com.bitperfect.player.sdk.plugin.runtime

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@Timeout(10, unit = TimeUnit.SECONDS)
class StagedLibrarySyncTest {
    private class FakeStore : StagingStore<Int> {
        val meta = HashMap<String, String>()
        var visible = listOf<Int>()
        val staging = mutableListOf<Int>()
        var swaps = 0
        var upToDate = false
        override fun getMeta(key: String) = meta[key]
        override fun setMeta(key: String, value: String) { meta[key] = value }
        override fun deleteMeta(key: String) { meta.remove(key) }
        override fun stagingCount() = staging.size
        override fun clearStaging() = staging.clear()
        override fun stage(chunk: List<Int>, position: Int) { staging += chunk }
        override fun swap(total: Int) {
            swaps++
            visible = staging.toList(); staging.clear()
            listOf(StagedLibrarySync.KEY_OFFSET, StagedLibrarySync.KEY_TOTAL, StagedLibrarySync.KEY_HEAD_ID).forEach { meta.remove(it) }
        }
        override fun isUpToDate(total: Int, head: String?) = upToDate
    }

    private class FakePager(var total: Int = 2000, var head: String? = "h") : LibraryPager<Int> {
        val offsets = mutableListOf<Int>()
        var stallAt: Int? = null
        var perCallDelay = 0L
        override suspend fun probe() = LibraryHead(total, head)
        override suspend fun page(offset: Int, limit: Int): List<Int> {
            if (stallAt == offset) awaitCancellation()
            if (perCallDelay > 0) delay(perCallDelay)
            offsets += offset
            return (offset until minOf(offset + limit, total)).toList()
        }
    }

    private val store = FakeStore()
    private val pager = FakePager()

    private fun TestScope.sync(
        stallMs: Long = 60_000L,
        cap: Int = Int.MAX_VALUE,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ) = StagedLibrarySync(
        pager, store, CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)),
        stallMs = stallMs, pageSize = 500, cap = cap, onProgress = onProgress,
    )

    @Test
    fun `cancel at 1500 then ensureRunning resumes from 1500`() = runTest {
        var running: Job? = null
        val sync = sync { done, _ -> if (done == 1500) running?.cancel() }
        running = sync.ensureRunning()
        advanceUntilIdle()
        assertTrue(running.isCancelled)
        assertEquals(1500, store.stagingCount())
        assertEquals("1500", store.meta[StagedLibrarySync.KEY_OFFSET])
        assertEquals(0, store.swaps)

        pager.offsets.clear()
        val resumed = sync.ensureRunning()
        advanceUntilIdle()

        assertTrue(resumed.isCompleted && !resumed.isCancelled)
        assertEquals(listOf(1500), pager.offsets)
        assertEquals(2000, store.visible.size)
        assertNull(store.meta[StagedLibrarySync.KEY_OFFSET])
    }

    @Test
    fun `changed head clears staging and restarts from zero`() = runTest {
        var running: Job? = null
        val sync = sync { done, _ -> if (done == 1000) running?.cancel() }
        running = sync.ensureRunning()
        advanceUntilIdle()
        assertEquals(1000, store.stagingCount())

        pager.head = "h2"
        pager.offsets.clear()
        sync.ensureRunning()
        advanceUntilIdle()

        assertEquals(listOf(0, 500, 1000, 1500), pager.offsets)
        assertEquals(1, store.swaps)
    }

    @Test
    fun `two ensureRunning calls share one job`() = runTest {
        val sync = sync()
        val a = sync.ensureRunning()
        assertSame(a, sync.ensureRunning())
        advanceUntilIdle()
        assertEquals(listOf(0, 500, 1000, 1500), pager.offsets)
        assertEquals(1, store.swaps)
    }

    @Test
    fun `visible table stays unchanged until the swap`() = runTest {
        store.visible = listOf(-1)
        val seen = mutableListOf<List<Int>>()
        sync { done, total -> if (done < total) seen += store.visible }.ensureRunning()
        advanceUntilIdle()

        assertEquals(3, seen.size)
        assertTrue(seen.all { it == listOf(-1) })
        assertEquals(2000, store.visible.size)
    }

    @Test
    fun `up to date list is not fetched`() = runTest {
        store.upToDate = true
        sync().ensureRunning()
        advanceUntilIdle()
        assertTrue(pager.offsets.isEmpty())
        assertEquals(0, store.swaps)
    }

    @Test
    fun `cap bounds the fetched items`() = runTest {
        sync(cap = 700).ensureRunning()
        advanceUntilIdle()
        assertEquals(700, store.visible.size)
        assertEquals(listOf(0, 500), pager.offsets)
    }

    @Test
    fun `a stalled call fails the run after stallMs and keeps the cursor`() = runTest {
        pager.stallAt = 1000
        val job = sync(stallMs = 1_000L).ensureRunning()
        advanceUntilIdle()

        assertTrue(job.isCompleted && !job.isCancelled)
        assertEquals(1000, store.stagingCount())
        assertEquals("1000", store.meta[StagedLibrarySync.KEY_OFFSET])
        assertEquals(0, store.swaps)
    }

    @Test
    fun `a slow but progressing sync has no overall deadline`() = runTest {
        pager.perCallDelay = 900L
        val job = sync(stallMs = 1_000L).ensureRunning()
        advanceUntilIdle()

        assertTrue(job.isCompleted && !job.isCancelled)
        assertEquals(1, store.swaps)
        assertEquals(2000, store.visible.size)
    }

    @Test
    fun `short read keeps the cursor and never swaps a partial set`() = runTest {
        val short = object : LibraryPager<Int> {
            override suspend fun probe() = LibraryHead(2000, "h")
            override suspend fun page(offset: Int, limit: Int) = if (offset >= 1000) emptyList() else (offset until offset + limit).toList()
        }
        StagedLibrarySync(short, store, CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)), pageSize = 500).ensureRunning()
        advanceUntilIdle()

        assertEquals(0, store.swaps)
        assertEquals("1000", store.meta[StagedLibrarySync.KEY_OFFSET])
    }
}

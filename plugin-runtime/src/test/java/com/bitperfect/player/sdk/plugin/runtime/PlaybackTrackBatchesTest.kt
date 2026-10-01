package com.bitperfect.player.sdk.plugin.runtime

import com.bitperfect.player.sdk.plugin.dto.PlaybackTrackDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Parcel isn't available on this module's plain JVM test runner; inject sizes via [measureByDuration] instead. */
class PlaybackTrackBatchesTest {
    private fun track(id: String, sizeBytes: Int) =
        PlaybackTrackDto(id, null, "title", "artist", "album", sizeBytes.toLong(), null, null)

    private val measureByDuration: (PlaybackTrackDto) -> Int = { it.durationMs.toInt() }

    @Test
    fun `uniform tracks pack up to the byte budget`() {
        val tracks = (0 until 10).map { track("t$it", 30 * 1024) }
        val batches = PlaybackTrackBatches.split(tracks, maxBytes = 64 * 1024, measure = measureByDuration)
        assertEquals(5, batches.size)
        batches.forEach { assertEquals(2, it.size) }
    }

    @Test
    fun `an oversized track goes alone between two small ones`() {
        val tracks = listOf(track("small1", 10 * 1024), track("big", 100 * 1024), track("small2", 10 * 1024))
        val batches = PlaybackTrackBatches.split(tracks, measure = measureByDuration)
        assertEquals(3, batches.size)
        assertEquals(listOf("small1"), batches[0].map { it.trackId })
        assertEquals(listOf("big"), batches[1].map { it.trackId })
        assertEquals(listOf("small2"), batches[2].map { it.trackId })
    }

    @Test
    fun `maxCount splits a byte-cheap page into 500 and 100`() {
        val tracks = (0 until 600).map { track("t$it", 1) }
        val batches = PlaybackTrackBatches.split(tracks, measure = measureByDuration)
        assertEquals(2, batches.size)
        assertEquals(500, batches[0].size)
        assertEquals(100, batches[1].size)
    }

    @Test
    fun `empty input yields no batches`() {
        assertEquals(emptyList<List<PlaybackTrackDto>>(), PlaybackTrackBatches.split(emptyList(), measure = measureByDuration))
    }
}

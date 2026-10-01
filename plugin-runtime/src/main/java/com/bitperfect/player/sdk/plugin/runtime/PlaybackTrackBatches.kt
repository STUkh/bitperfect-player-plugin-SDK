package com.bitperfect.player.sdk.plugin.runtime

import android.os.Parcel
import com.bitperfect.player.sdk.plugin.dto.PlaybackTrackDto

/**
 * Splits a page into Binder-safe batches: each parcels to at most [maxBytes] (default 64 KiB;
 * the transaction buffer is 1 MiB per process, shared by all in-flight calls).
 */
object PlaybackTrackBatches {
    const val DEFAULT_MAX_BYTES = 64 * 1024

    fun split(
        tracks: List<PlaybackTrackDto>,
        maxBytes: Int = DEFAULT_MAX_BYTES,
        maxCount: Int = 500,
        measure: (PlaybackTrackDto) -> Int = { it.parcelSize() },
    ): List<List<PlaybackTrackDto>> {
        if (tracks.isEmpty()) return emptyList()
        val batches = mutableListOf<List<PlaybackTrackDto>>()
        var batchStart = 0
        var batchBytes = 0L
        for (i in tracks.indices) {
            val size = measure(tracks[i])
            // A batch always takes at least one track, even an oversized one — it just goes alone.
            if (i > batchStart && (batchBytes + size > maxBytes || i - batchStart >= maxCount)) {
                batches += tracks.subList(batchStart, i)
                batchStart = i
                batchBytes = 0L
            }
            batchBytes += size
        }
        batches += tracks.subList(batchStart, tracks.size)
        return batches
    }

    private fun PlaybackTrackDto.parcelSize(): Int {
        val parcel = Parcel.obtain()
        return try {
            parcel.setDataPosition(0)
            writeToParcel(parcel, 0)
            parcel.dataSize()
        } finally {
            parcel.recycle()
        }
    }
}

package com.bitperfect.player.sdk.plugin.runtime

import com.bitperfect.player.core.plugin.page.PluginAction
import com.bitperfect.player.core.plugin.page.PluginImage
import com.bitperfect.player.core.plugin.page.PluginItem
import com.bitperfect.player.core.plugin.page.PluginMenuEntry
import com.bitperfect.player.core.plugin.page.PluginPage
import com.bitperfect.player.core.plugin.page.PluginSection
import com.bitperfect.player.core.plugin.page.PluginTrack
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import kotlin.system.measureNanoTime

/**
 * Benchmark and architectural spike for transporting large declarative plugin pages
 * across Android Binder IPC without TransactionTooLargeException.
 *
 * Evaluates:
 * 1. Transparent compression (GZIP vs Deflate L1/L6/L9) on realistic 100, 500, 1000, 2000 track JSON.
 * 2. Shared streaming / pipe transfer (raw vs compressed pipe, buffer sizes, broken pipe handling).
 * 3. Production-ready Hybrid Thresholded Strategy (<=16KB inline, 16-512KB compress, >512KB pipe).
 * 4. Backward/forward compatibility contract invariants.
 */
class PluginPageTransportBenchmarkTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = false }

    /**
     * Generates a realistic PluginPage matching a typical streaming catalog response:
     * Each track carries artwork URL, badges, format tier, playback action, and 5 contextual menu items.
     */
    private fun createRealisticPluginPage(trackCount: Int): PluginPage {
        val items = (1..trackCount).map { i ->
            val trackId = "track_${1000000 + i}"
            val albumId = "album_${200000 + (i / 12)}"
            val artistId = "artist_${3000 + (i / 20)}"
            val title = "Track #$i - Symphony No. ${(i % 9) + 1} in Allegro Maestoso (2024 Remaster)"
            val artist = "Artist Name ${(i % 50) + 1} & Philharmonic Orchestra"
            val album = "Complete Works Collection Vol. ${(i / 12) + 1}"
            val artUrl = "https://e-cdns-images.dzcdn.net/images/cover/4b5b7e2832bc9d0d3c40134b281f6216/500x500.jpg"

            val track = PluginTrack(
                trackId = trackId,
                albumId = albumId,
                title = title,
                artist = artist,
                album = album,
                durationMs = 210000L + (i * 137 % 180000),
                artworkUrl = artUrl,
                quality = "FLAC 24-bit / 96.0 kHz",
                formatTier = "HI_RES",
                discNumber = 1,
            )

            PluginItem(
                id = trackId,
                title = title,
                subtitle = artist,
                image = PluginImage(url = artUrl, shape = "SQUARE"),
                badge = "24-bit/96kHz",
                action = PluginAction(
                    type = "play",
                    params = mapOf("id" to trackId, "format" to "flac"),
                    startIndex = i - 1,
                ),
                track = track,
                trackNumber = (i % 24) + 1,
                contentType = "TRACK",
                formatTier = "HI_RES",
                menu = listOf(
                    PluginMenuEntry(
                        label = "Add to favourites",
                        action = PluginAction(type = "dispatch", actionId = "favourite", params = mapOf("id" to trackId, "kind" to "track")),
                        role = "favourite",
                    ),
                    PluginMenuEntry(
                        label = "Add to local playlist",
                        action = PluginAction(type = "dispatch", actionId = "playlist.pick", params = mapOf("trackId" to trackId)),
                        role = "addToPlaylist",
                    ),
                    PluginMenuEntry(
                        label = "Start Track Mix",
                        action = PluginAction(type = "navigate", routeId = "track_mix", params = mapOf("id" to trackId, "title" to title)),
                        icon = "RADIO",
                    ),
                    PluginMenuEntry(
                        label = "Go to Album",
                        action = PluginAction(type = "navigate", routeId = "album", params = mapOf("id" to albumId)),
                        icon = "ALBUM",
                    ),
                    PluginMenuEntry(
                        label = "Go to Artist",
                        action = PluginAction(type = "navigate", routeId = "artist", params = mapOf("id" to artistId)),
                        icon = "ARTIST",
                    ),
                ),
            )
        }

        return PluginPage(
            v = 1,
            title = "Playlist with $trackCount High-Resolution Tracks",
            subtitle = "Bitperfect Audiophile Catalog Master",
            contentType = "PLAYLIST",
            sections = listOf(
                PluginSection(
                    id = "main_tracks",
                    kind = "LIST",
                    items = items,
                ),
            ),
        )
    }

    // --- Compression helpers ---

    private fun compressGzip(data: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream(data.size / 4)
        GZIPOutputStream(bos).use { it.write(data) }
        return bos.toByteArray()
    }

    private fun decompressGzip(compressed: ByteArray): ByteArray {
        return GZIPInputStream(ByteArrayInputStream(compressed)).use { it.readBytes() }
    }

    private fun compressDeflate(data: ByteArray, level: Int): ByteArray {
        val deflater = Deflater(level, false)
        return try {
            val bos = ByteArrayOutputStream(data.size / 4)
            DeflaterOutputStream(bos, deflater).use { it.write(data) }
            bos.toByteArray()
        } finally {
            deflater.end()
        }
    }

    private fun decompressDeflate(compressed: ByteArray): ByteArray {
        val inflater = Inflater(false)
        return try {
            InflaterInputStream(ByteArrayInputStream(compressed), inflater).use { it.readBytes() }
        } finally {
            inflater.end()
        }
    }

    @Test
    fun `benchmark compression ratios and latencies on realistic plugin pages`() {
        val trackCounts = listOf(100, 500, 1000, 2000)

        // Warm up JIT
        val warmupPage = createRealisticPluginPage(50)
        val warmupBytes = json.encodeToString(warmupPage).toByteArray(StandardCharsets.UTF_8)
        repeat(20) {
            val gz = compressGzip(warmupBytes)
            decompressGzip(gz)
            val df1 = compressDeflate(warmupBytes, Deflater.BEST_SPEED)
            decompressDeflate(df1)
            val df6 = compressDeflate(warmupBytes, Deflater.DEFAULT_COMPRESSION)
            decompressDeflate(df6)
            val df9 = compressDeflate(warmupBytes, Deflater.BEST_COMPRESSION)
            decompressDeflate(df9)
        }

        val results = mutableListOf<String>()
        results.add("\n=======================================================================================================")
        results.add(String.format("%-8s | %-14s | %-18s | %-8s | %-16s | %-16s", "Tracks", "Algorithm", "Raw -> Compressed", "Ratio", "Compress Latency", "Decompress Latency"))
        results.add("-------------------------------------------------------------------------------------------------------")

        for (count in trackCounts) {
            val page = createRealisticPluginPage(count)
            val rawJson = json.encodeToString(page)
            val rawBytes = rawJson.toByteArray(StandardCharsets.UTF_8)
            val rawKb = rawBytes.size / 1024.0

            // 1. GZIP (Standard header + CRC32, level 6 default)
            val (gzipBytes, gzipCompMs, gzipDecompMs) = benchmarkAlgorithm(rawBytes) { b ->
                compressGzip(b) to { c: ByteArray -> decompressGzip(c) }
            }
            val gzipRatio = (1.0 - (gzipBytes.size.toDouble() / rawBytes.size)) * 100.0
            results.add(String.format("%-8d | %-14s | %6.1f -> %6.1f KB | %6.1f%% | %13.3f ms | %13.3f ms",
                count, "GZIP (L6)", rawKb, gzipBytes.size / 1024.0, gzipRatio, gzipCompMs, gzipDecompMs))

            // 2. Deflate Best Speed (Level 1)
            val (df1Bytes, df1CompMs, df1DecompMs) = benchmarkAlgorithm(rawBytes) { b ->
                compressDeflate(b, Deflater.BEST_SPEED) to { c: ByteArray -> decompressDeflate(c) }
            }
            val df1Ratio = (1.0 - (df1Bytes.size.toDouble() / rawBytes.size)) * 100.0
            results.add(String.format("%-8d | %-14s | %6.1f -> %6.1f KB | %6.1f%% | %13.3f ms | %13.3f ms",
                count, "Deflate (L1)", rawKb, df1Bytes.size / 1024.0, df1Ratio, df1CompMs, df1DecompMs))

            // 3. Deflate Default (Level 6)
            val (df6Bytes, df6CompMs, df6DecompMs) = benchmarkAlgorithm(rawBytes) { b ->
                compressDeflate(b, Deflater.DEFAULT_COMPRESSION) to { c: ByteArray -> decompressDeflate(c) }
            }
            val df6Ratio = (1.0 - (df6Bytes.size.toDouble() / rawBytes.size)) * 100.0
            results.add(String.format("%-8d | %-14s | %6.1f -> %6.1f KB | %6.1f%% | %13.3f ms | %13.3f ms",
                count, "Deflate (L6)", rawKb, df6Bytes.size / 1024.0, df6Ratio, df6CompMs, df6DecompMs))

            // 4. Deflate Best Compression (Level 9)
            val (df9Bytes, df9CompMs, df9DecompMs) = benchmarkAlgorithm(rawBytes) { b ->
                compressDeflate(b, Deflater.BEST_COMPRESSION) to { c: ByteArray -> decompressDeflate(c) }
            }
            val df9Ratio = (1.0 - (df9Bytes.size.toDouble() / rawBytes.size)) * 100.0
            results.add(String.format("%-8d | %-14s | %6.1f -> %6.1f KB | %6.1f%% | %13.3f ms | %13.3f ms",
                count, "Deflate (L9)", rawKb, df9Bytes.size / 1024.0, df9Ratio, df9CompMs, df9DecompMs))

            // Round-trip verification for integrity
            val roundTrip = decompressGzip(gzipBytes).toString(StandardCharsets.UTF_8)
            assertEquals(rawJson.length, roundTrip.length)
            assertEquals(rawJson, roundTrip)
        }

        results.add("=======================================================================================================\n")
        println(results.joinToString("\n"))
    }

    private fun benchmarkAlgorithm(
        rawBytes: ByteArray,
        factory: (ByteArray) -> Pair<ByteArray, (ByteArray) -> ByteArray>,
    ): Triple<ByteArray, Double, Double> {
        val iterations = 5
        var compTotalNs = 0L
        var decompTotalNs = 0L
        var compressed = ByteArray(0)

        repeat(iterations) {
            val (comp, decompressor) = factory(rawBytes)
            compressed = comp
            val compNs = measureNanoTime { factory(rawBytes) }
            val decompNs = measureNanoTime { decompressor(comp) }
            compTotalNs += compNs
            decompTotalNs += decompNs
        }

        val avgCompMs = (compTotalNs.toDouble() / iterations) / 1_000_000.0
        val avgDecompMs = (decompTotalNs.toDouble() / iterations) / 1_000_000.0
        return Triple(compressed, avgCompMs, avgDecompMs)
    }

    @Test
    fun `benchmark pipe transfer for large payloads and evaluate buffer sizes`() {
        val page = createRealisticPluginPage(1500)
        val rawJson = json.encodeToString(page)
        val rawBytes = rawJson.toByteArray(StandardCharsets.UTF_8)
        val executor = Executors.newSingleThreadExecutor()

        try {
            val pipeSizes = listOf(4 * 1024, 16 * 1024, 64 * 1024)
            println("\n--- Pipe Streaming Benchmark (1,500 tracks, ${String.format("%.2f", rawBytes.size / (1024.0 * 1024.0))} MB) ---")

            for (pipeSize in pipeSizes) {
                // Test: Raw pipe streaming with pipeSize buffer
                val rawPipeOut = PipedOutputStream()
                val rawPipeIn = PipedInputStream(rawPipeOut, pipeSize)

                val rawWriteFuture = executor.submit<Long> {
                    measureNanoTime {
                        rawPipeOut.use { it.write(rawBytes) }
                    }
                }

                var receivedRawBytes: ByteArray
                val rawReadNs = measureNanoTime {
                    receivedRawBytes = rawPipeIn.use { it.readBytes() }
                }
                val rawWriteNs = rawWriteFuture.get()

                assertEquals(rawBytes.size, receivedRawBytes.size)
                val throughputMbS = (rawBytes.size / (1024.0 * 1024.0)) / ((rawReadNs / 1_000_000_000.0))
                println(String.format("Pipe buffer %2d KB: Write = %5.2f ms, Read = %5.2f ms, Throughput = %6.1f MB/s",
                    pipeSize / 1024, rawWriteNs / 1_000_000.0, rawReadNs / 1_000_000.0, throughputMbS))
            }

            // Test: Compressed streaming over pipe
            val gzPipeOut = PipedOutputStream()
            val gzPipeIn = PipedInputStream(gzPipeOut, 64 * 1024)

            val gzWriteFuture = executor.submit<Long> {
                measureNanoTime {
                    GZIPOutputStream(gzPipeOut).use { it.write(rawBytes) }
                }
            }

            var receivedGzBytes: ByteArray
            val gzReadNs = measureNanoTime {
                receivedGzBytes = GZIPInputStream(gzPipeIn).use { it.readBytes() }
            }
            val gzWriteNs = gzWriteFuture.get()

            assertEquals(rawBytes.size, receivedGzBytes.size)
            println(String.format("Compressed Pipe: Write+Compress = %5.2f ms, Read+Decompress = %5.2f ms\n",
                gzWriteNs / 1_000_000.0, gzReadNs / 1_000_000.0))

        } finally {
            executor.shutdown()
        }
    }

    @Test
    fun `verify lifecycle safety and broken pipe error isolation`() {
        val executor = Executors.newSingleThreadExecutor()
        try {
            val pipeOut = PipedOutputStream()
            val pipeIn = PipedInputStream(pipeOut, 64 * 1024)

            val writerEncounteredBrokenPipe = AtomicBoolean(false)
            val largeData = ByteArray(512 * 1024) { 0x42 }

            // Writer thread pumps data
            val writerFuture = executor.submit {
                try {
                    pipeOut.use { out ->
                        while (true) {
                            out.write(largeData)
                            out.flush()
                        }
                    }
                } catch (e: IOException) {
                    // Expected when reader closes pipe abruptly
                    writerEncounteredBrokenPipe.set(true)
                }
            }

            // Reader reads a tiny chunk and abruptly closes early (simulating UI screen back / cancellation)
            val smallChunk = ByteArray(1024)
            val bytesRead = pipeIn.read(smallChunk)
            assertTrue(bytesRead > 0)
            pipeIn.close() // Abrupt consumer close

            // Wait for writer future to complete with caught IOException
            runCatching { writerFuture.get(2, TimeUnit.SECONDS) }
            assertTrue(writerEncounteredBrokenPipe.get(), "Writer must cleanly catch Broken Pipe IOException without crashing process")
        } finally {
            executor.shutdownNow()
        }
    }

    // --- Production Hybrid Strategy Implementation Spike ---

    companion object {
        const val CONTRACT_VERSION_BASE = 1

        // Transport flags embedded in upper 16 bits of contractVersion
        const val FLAG_PFD_STREAM = 1 shl 13          // 0x2000 (ParcelFileDescriptor stream)
        const val FLAG_COMPRESSED_DEFLATE = 1 shl 14  // 0x4000 (ZLIB Deflate)
        const val FLAG_COMPRESSED_GZIP = 1 shl 15     // 0x8000 (GZIP stream)
        const val VERSION_MASK = 0x00FF               // Lower 8 bits for base API version
    }

    enum class TransportTier {
        INLINE_STRING,     // <= 16 KB: raw inline string (100% backward compatible)
        COMPRESSED_BLOB,   // 16 KB - 512 KB: GZIP / Deflate in-parcel byte blob
        SHARED_DESCRIPTOR, // > 512 KB: ParcelFileDescriptor / SharedMemory streaming
    }

    /**
     * Architectural spike model of the production-ready PageDescriptorDtoV2.
     * Demonstrates transparent lazy resolution of `json`, backward-compatible constructor,
     * and bitmask-based transport.
     */
    data class PageDescriptorDtoSpike(
        val contractVersion: Int,
        val rawJson: String? = null,
        val compressedBytes: ByteArray? = null,
        val streamSupplier: (() -> InputStream)? = null,
    ) {
        /**
         * Transparent resolution: host UI calls `descriptor.json` exactly as it does today.
         * The DTO transparently resolves from inline string, decompresses blob, or reads stream.
         */
        val json: String by lazy(LazyThreadSafetyMode.NONE) {
            when {
                contractVersion and FLAG_PFD_STREAM != 0 -> {
                    checkNotNull(streamSupplier) { "Missing stream supplier for PFD transport" }
                    streamSupplier.invoke().use { stream ->
                        if (contractVersion and FLAG_COMPRESSED_GZIP != 0) {
                            GZIPInputStream(stream).use { it.readBytes().toString(StandardCharsets.UTF_8) }
                        } else {
                            stream.readBytes().toString(StandardCharsets.UTF_8)
                        }
                    }
                }
                contractVersion and FLAG_COMPRESSED_GZIP != 0 -> {
                    checkNotNull(compressedBytes) { "Missing compressed payload for GZIP transport" }
                    GZIPInputStream(ByteArrayInputStream(compressedBytes)).use {
                        it.readBytes().toString(StandardCharsets.UTF_8)
                    }
                }
                contractVersion and FLAG_COMPRESSED_DEFLATE != 0 -> {
                    checkNotNull(compressedBytes) { "Missing compressed payload for Deflate transport" }
                    val inflater = Inflater(false)
                    try {
                        InflaterInputStream(ByteArrayInputStream(compressedBytes), inflater).use {
                            it.readBytes().toString(StandardCharsets.UTF_8)
                        }
                    } finally {
                        inflater.end()
                    }
                }
                else -> {
                    // Base contract v1: raw inline string
                    rawJson.orEmpty()
                }
            }
        }
    }

    object HybridTransportStrategy {
        const val INLINE_THRESHOLD_BYTES = 16 * 1024        // 16 KB
        const val COMPRESSION_THRESHOLD_BYTES = 512 * 1024  // 512 KB

        fun pack(rawJson: String): Pair<TransportTier, PageDescriptorDtoSpike> {
            val bytes = rawJson.toByteArray(StandardCharsets.UTF_8)
            return when {
                bytes.size <= INLINE_THRESHOLD_BYTES -> {
                    TransportTier.INLINE_STRING to PageDescriptorDtoSpike(
                        contractVersion = CONTRACT_VERSION_BASE,
                        rawJson = rawJson,
                    )
                }
                bytes.size <= COMPRESSION_THRESHOLD_BYTES -> {
                    // Deflate L1 (or GZIP): compress for safe, compact Binder transit
                    val bos = ByteArrayOutputStream(bytes.size / 4)
                    GZIPOutputStream(bos).use { it.write(bytes) }
                    TransportTier.COMPRESSED_BLOB to PageDescriptorDtoSpike(
                        contractVersion = CONTRACT_VERSION_BASE or FLAG_COMPRESSED_GZIP,
                        compressedBytes = bos.toByteArray(),
                    )
                }
                else -> {
                    // Over 512 KB: stream via pipe / shared memory (raw or compressed)
                    // Streaming raw is ultra-fast (1.5 ms for 2.3 MB) and avoids CPU overhead
                    val byteStreamSupplier: () -> InputStream = { ByteArrayInputStream(bytes) }
                    TransportTier.SHARED_DESCRIPTOR to PageDescriptorDtoSpike(
                        contractVersion = CONTRACT_VERSION_BASE or FLAG_PFD_STREAM,
                        streamSupplier = byteStreamSupplier,
                    )
                }
            }
        }
    }

    @Test
    fun `verify hybrid threshold strategy routing and transparent resolution`() {
        // Tier 1: Small page (5 tracks, ~6 KB) -> Inline String
        val smallPage = createRealisticPluginPage(5)
        val smallJson = json.encodeToString(smallPage)
        val smallSize = smallJson.toByteArray(StandardCharsets.UTF_8).size
        assertTrue(smallSize <= HybridTransportStrategy.INLINE_THRESHOLD_BYTES)

        val (smallTier, smallDto) = HybridTransportStrategy.pack(smallJson)
        assertEquals(TransportTier.INLINE_STRING, smallTier)
        assertEquals(CONTRACT_VERSION_BASE, smallDto.contractVersion)
        assertEquals(smallJson, smallDto.rawJson)
        assertEquals(smallJson, smallDto.json)

        // Tier 2: Medium page (150 tracks, ~235 KB) -> Compressed GZIP Blob
        val mediumPage = createRealisticPluginPage(150)
        val mediumJson = json.encodeToString(mediumPage)
        val mediumSize = mediumJson.toByteArray(StandardCharsets.UTF_8).size
        assertTrue(mediumSize in (HybridTransportStrategy.INLINE_THRESHOLD_BYTES + 1)..HybridTransportStrategy.COMPRESSION_THRESHOLD_BYTES)

        val (mediumTier, mediumDto) = HybridTransportStrategy.pack(mediumJson)
        assertEquals(TransportTier.COMPRESSED_BLOB, mediumTier)
        assertTrue(mediumDto.contractVersion and FLAG_COMPRESSED_GZIP != 0)
        assertNotNull(mediumDto.compressedBytes)
        // Verify compressed size is safely under 30 KB (< 3% of Binder transaction buffer)
        assertTrue(mediumDto.compressedBytes!!.size < 30 * 1024)
        assertEquals(mediumJson, mediumDto.json)

        // Tier 3: Large page (1000 tracks, ~1.5 MB) -> Shared Descriptor
        val largePage = createRealisticPluginPage(1000)
        val largeJson = json.encodeToString(largePage)
        val largeSize = largeJson.toByteArray(StandardCharsets.UTF_8).size
        assertTrue(largeSize > HybridTransportStrategy.COMPRESSION_THRESHOLD_BYTES)

        val (largeTier, largeDto) = HybridTransportStrategy.pack(largeJson)
        assertEquals(TransportTier.SHARED_DESCRIPTOR, largeTier)
        assertTrue(largeDto.contractVersion and FLAG_PFD_STREAM != 0)
        assertNotNull(largeDto.streamSupplier)
        assertEquals(largeJson, largeDto.json)
    }

    @Test
    fun `verify backward and forward compatibility contracts`() {
        val testJson = "{\"v\":1,\"title\":\"Test Album\",\"sections\":[]}"

        // Legacy plugin (contractVersion = 1, raw json string) -> V2 Host
        val legacyPluginDto = PageDescriptorDtoSpike(
            contractVersion = 1,
            rawJson = testJson,
        )
        assertEquals(1, legacyPluginDto.contractVersion and VERSION_MASK)
        assertEquals(testJson, legacyPluginDto.json)

        // V2 Plugin with GZIP -> V2 Host
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(testJson.toByteArray(StandardCharsets.UTF_8)) }
        val v2GzipDto = PageDescriptorDtoSpike(
            contractVersion = 1 or FLAG_COMPRESSED_GZIP,
            compressedBytes = bos.toByteArray(),
        )
        assertEquals(1, v2GzipDto.contractVersion and VERSION_MASK)
        assertEquals(testJson, v2GzipDto.json)

        // V2 Plugin with Deflate -> V2 Host
        val deflater = Deflater(Deflater.BEST_SPEED, false)
        val defBos = ByteArrayOutputStream()
        DeflaterOutputStream(defBos, deflater).use { it.write(testJson.toByteArray(StandardCharsets.UTF_8)) }
        deflater.end()
        val v2DeflateDto = PageDescriptorDtoSpike(
            contractVersion = 1 or FLAG_COMPRESSED_DEFLATE,
            compressedBytes = defBos.toByteArray(),
        )
        assertEquals(1, v2DeflateDto.contractVersion and VERSION_MASK)
        assertEquals(testJson, v2DeflateDto.json)
    }
}

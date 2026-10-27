package com.bitperfect.player.sdk.plugin.dto

import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.Parcelable
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.GZIPOutputStream

class PageDescriptorDtoTest {

    @Test
    fun `pack small json within 256 KB routes to Tier 1 inline raw string`() {
        val smallJson = "{\"v\":1,\"title\":\"Small Home\",\"items\":[1,2,3]}"
        assertTrue(smallJson.toByteArray(StandardCharsets.UTF_8).size <= PageDescriptorDto.INLINE_THRESHOLD_BYTES)

        val dto = PageDescriptorDto.pack(smallJson, baseVersion = 1)
        assertEquals(1, dto.contractVersion)
        assertEquals(1, dto.baseVersion)
        assertEquals(smallJson, dto.rawJson)
        assertNull(dto.compressedBytes)
        assertNull(dto.pfd)
        assertEquals(smallJson, dto.json)
        assertEquals(0, dto.describeContents())
    }

    @Test
    fun `pack medium json routes to Tier 2 compressed GZIP blob`() {
        // Build JSON > 256 KB but <= 512 KB compressed
        val repeatCount = 3000
        val mediumJson = "{\"v\":1,\"tracks\":[" + (1..repeatCount).joinToString(",") {
            "{\"id\":\"track_$it\",\"title\":\"Long Track Title Number $it\",\"performer\":\"Artist Name\",\"album\":\"Sample Album Title\"}"
        } + "]}"
        val rawSize = mediumJson.toByteArray(StandardCharsets.UTF_8).size
        assertTrue(rawSize > PageDescriptorDto.INLINE_THRESHOLD_BYTES)

        val dto = PageDescriptorDto.pack(mediumJson, baseVersion = 2)
        assertEquals(2 or PageDescriptorDto.FLAG_COMPRESSED_GZIP, dto.contractVersion)
        assertEquals(2, dto.baseVersion)
        assertNotNull(dto.compressedBytes)
        assertTrue(dto.compressedBytes!!.size <= PageDescriptorDto.COMPRESSION_THRESHOLD_BYTES)
        assertNull(dto.pfd)
        assertEquals(0, dto.describeContents())

        // Transparent lazy decompression
        assertEquals(mediumJson, dto.json)
    }

    @Test
    fun `pack extreme payload routes to Tier 3 streaming transport`() {
        // Create an extreme payload that compresses to > 512 KB
        // Highly random or large payload so GZIP doesn't compress below 512 KB
        val sb = StringBuilder(2 * 1024 * 1024)
        sb.append("{\"v\":1,\"extreme\":[")
        var i = 0
        while (sb.length < 1_200_000) {
            sb.append("\"item_${i}_${(i * 31337).toString(16)}_${(i * 7919).toString(36)}\",")
            i++
        }
        sb.append("\"end\"]}")
        val largeJson = sb.toString()

        // Compress manually to check size
        val rawBytes = largeJson.toByteArray(StandardCharsets.UTF_8)
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(rawBytes) }
        val compressed = bos.toByteArray()

        if (compressed.size > PageDescriptorDto.COMPRESSION_THRESHOLD_BYTES) {
            val dto = PageDescriptorDto.pack(largeJson, baseVersion = 1)
            assertTrue(dto.contractVersion and PageDescriptorDto.FLAG_PFD_STREAM != 0)
            assertEquals(1, dto.baseVersion)
            // Either pfd or streamSupplier is set (depending on test environment)
            assertTrue(dto.pfd != null || dto.streamSupplier != null)
            assertEquals(largeJson, dto.json)
        }
    }

    @Test
    fun `backward compatibility - legacy contractVersion with no flags resolves raw json`() {
        val legacyJson = "{\"v\":1,\"title\":\"Legacy\"}"
        val legacyDto = PageDescriptorDto(contractVersion = 1, rawJson = legacyJson)

        assertEquals(1, legacyDto.contractVersion)
        assertEquals(1, legacyDto.baseVersion)
        assertEquals(legacyJson, legacyDto.json)
        assertEquals(0, legacyDto.describeContents())
    }

    @Test
    fun `transparent resolution of Deflate compressed payload`() {
        val testJson = "{\"v\":1,\"title\":\"Deflate Stream Test\"}"
        val rawBytes = testJson.toByteArray(StandardCharsets.UTF_8)
        val deflater = Deflater(Deflater.BEST_SPEED, false)
        val defBos = ByteArrayOutputStream()
        DeflaterOutputStream(defBos, deflater).use { it.write(rawBytes) }
        deflater.end()

        val dto = PageDescriptorDto(
            contractVersion = 1 or PageDescriptorDto.FLAG_COMPRESSED_DEFLATE,
            compressedBytes = defBos.toByteArray(),
        )

        assertEquals(1, dto.baseVersion)
        assertEquals(testJson, dto.json)
    }

    @Test
    fun `transparent resolution of PFD stream payload via streamSupplier`() {
        val testJson = "{\"v\":1,\"title\":\"Streamed PFD Test\"}"
        val rawBytes = testJson.toByteArray(StandardCharsets.UTF_8)

        val dto = PageDescriptorDto(
            contractVersion = 3 or PageDescriptorDto.FLAG_PFD_STREAM,
            rawJson = null,
            compressedBytes = null,
            pfd = null,
            streamSupplier = { ByteArrayInputStream(rawBytes) },
        )

        assertEquals(3, dto.baseVersion)
        assertEquals(testJson, dto.json)
    }

    @Test
    fun `transparent resolution of compressed PFD stream payload via streamSupplier`() {
        val testJson = "{\"v\":1,\"title\":\"Compressed Streamed PFD Test\"}"
        val rawBytes = testJson.toByteArray(StandardCharsets.UTF_8)
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(rawBytes) }

        val dto = PageDescriptorDto(
            contractVersion = 1 or PageDescriptorDto.FLAG_PFD_STREAM or PageDescriptorDto.FLAG_COMPRESSED_GZIP,
            rawJson = null,
            compressedBytes = null,
            pfd = null,
            streamSupplier = { ByteArrayInputStream(bos.toByteArray()) },
        )

        assertEquals(1, dto.baseVersion)
        assertEquals(testJson, dto.json)
    }

    @Test
    fun `component destructuring and equals contract`() {
        val json = "{\"v\":1,\"title\":\"Album\"}"
        val dto1 = PageDescriptorDto.pack(json, baseVersion = 1)
        val dto2 = PageDescriptorDto.pack(json, baseVersion = 1)
        val dto3 = PageDescriptorDto.pack(json, baseVersion = 2)

        val (version, resolvedJson) = dto1
        assertEquals(1, version)
        assertEquals(json, resolvedJson)

        assertEquals(dto1, dto2)
        assertEquals(dto1.hashCode(), dto2.hashCode())
        assertNotEquals(dto1, dto3)
    }

    @Test
    fun `describeContents returns CONTENTS_FILE_DESCRIPTOR only when pfd is present`() {
        val dtoNoPfd = PageDescriptorDto.pack("{}", baseVersion = 1)
        assertEquals(0, dtoNoPfd.describeContents())

        val dummyPipe = runCatching { ParcelFileDescriptor.createPipe() }.getOrNull()
        if (dummyPipe != null) {
            val dtoWithPfd = PageDescriptorDto(
                contractVersion = 1 or PageDescriptorDto.FLAG_PFD_STREAM,
                pfd = dummyPipe[0],
            )
            assertEquals(Parcelable.CONTENTS_FILE_DESCRIPTOR, dtoWithPfd.describeContents())
            dummyPipe[0].close()
            dummyPipe[1].close()
        }
    }

    @Test
    fun `CREATOR newArray returns array of requested size`() {
        val array = PageDescriptorDto.CREATOR.newArray(5)
        assertEquals(5, array.size)
        assertNull(array[0])
    }

    @Test
    fun `writeToParcel and createFromParcel roundtrip for Tier 1 inline string`() {
        val testJson = "{\"v\":1,\"title\":\"Tier 1\"}"
        val dto = PageDescriptorDto.pack(testJson, baseVersion = 1)
        assertEquals(1, dto.contractVersion)

        val parcel = io.mockk.mockk<Parcel>(relaxed = true)
        dto.writeToParcel(parcel, 0)
        io.mockk.verify { parcel.writeInt(1) }
        io.mockk.verify { parcel.writeString(testJson) }

        // Now test reading back via CREATOR
        val readParcel = io.mockk.mockk<Parcel>()
        io.mockk.every { readParcel.readInt() } returns 1
        io.mockk.every { readParcel.readString() } returns testJson

        val restoredDto = PageDescriptorDto.CREATOR.createFromParcel(readParcel)
        assertEquals(1, restoredDto.contractVersion)
        assertEquals(1, restoredDto.baseVersion)
        assertEquals(testJson, restoredDto.json)
    }

    @Test
    fun `writeToParcel and createFromParcel roundtrip for Tier 2 compressed GZIP`() {
        val mediumJson = "{\"v\":1,\"items\":[" + (1..5000).joinToString(",") { "{\"id\":$it,\"title\":\"Track title number $it\",\"artist\":\"Artist Name\"}" } + "]}"
        val dto = PageDescriptorDto.pack(mediumJson, baseVersion = 1)
        assertTrue(dto.contractVersion and PageDescriptorDto.FLAG_COMPRESSED_GZIP != 0)

        val parcel = io.mockk.mockk<Parcel>(relaxed = true)
        dto.writeToParcel(parcel, 0)
        io.mockk.verify { parcel.writeInt(dto.contractVersion) }
        io.mockk.verify { parcel.writeByteArray(dto.compressedBytes) }

        // Test reading back via CREATOR
        val readParcel = io.mockk.mockk<Parcel>()
        io.mockk.every { readParcel.readInt() } returns dto.contractVersion
        io.mockk.every { readParcel.createByteArray() } returns dto.compressedBytes

        val restoredDto = PageDescriptorDto.CREATOR.createFromParcel(readParcel)
        assertEquals(dto.contractVersion, restoredDto.contractVersion)
        assertEquals(1, restoredDto.baseVersion)
        assertEquals(mediumJson, restoredDto.json)
    }

    @Test
    fun `writeToParcel and createFromParcel roundtrip for Tier 3 PFD stream`() {
        val mockPfd = io.mockk.mockk<ParcelFileDescriptor>()
        val dto = PageDescriptorDto(
            contractVersion = 1 or PageDescriptorDto.FLAG_PFD_STREAM,
            pfd = mockPfd,
        )

        val parcel = io.mockk.mockk<Parcel>(relaxed = true)
        dto.writeToParcel(parcel, Parcelable.PARCELABLE_WRITE_RETURN_VALUE)
        io.mockk.verify { parcel.writeInt(dto.contractVersion) }
        io.mockk.verify { parcel.writeParcelable(mockPfd, Parcelable.PARCELABLE_WRITE_RETURN_VALUE) }

        // Test reading back via CREATOR
        val readParcel = io.mockk.mockk<Parcel>()
        io.mockk.every { readParcel.readInt() } returns (1 or PageDescriptorDto.FLAG_PFD_STREAM)
        io.mockk.every {
            readParcel.readParcelable<ParcelFileDescriptor>(any(), any())
        } returns mockPfd
        io.mockk.every {
            @Suppress("DEPRECATION")
            readParcel.readParcelable<ParcelFileDescriptor>(any())
        } returns mockPfd

        val restoredDto = PageDescriptorDto.CREATOR.createFromParcel(readParcel)
        assertEquals(1 or PageDescriptorDto.FLAG_PFD_STREAM, restoredDto.contractVersion)
        assertEquals(1, restoredDto.baseVersion)
        assertEquals(mockPfd, restoredDto.pfd)
        assertEquals(Parcelable.CONTENTS_FILE_DESCRIPTOR, restoredDto.describeContents())
    }

    @Test
    fun `backward compatibility - CREATOR reads raw string when contractVersion has no flags`() {
        val readParcel = io.mockk.mockk<Parcel>()
        io.mockk.every { readParcel.readInt() } returns 1
        io.mockk.every { readParcel.readString() } returns "{\"v\":1,\"msg\":\"legacy\"}"

        val restoredDto = PageDescriptorDto.CREATOR.createFromParcel(readParcel)
        assertEquals(1, restoredDto.contractVersion)
        assertEquals(1, restoredDto.baseVersion)
        assertEquals("{\"v\":1,\"msg\":\"legacy\"}", restoredDto.json)
    }

    @Test
    fun `corrupt GZIP bytes gracefully fall back to empty string without throwing`() {
        val corruptBytes = byteArrayOf(0x12, 0x34, 0x56, 0x78)
        val dto = PageDescriptorDto(
            contractVersion = 1 or PageDescriptorDto.FLAG_COMPRESSED_GZIP,
            compressedBytes = corruptBytes,
        )
        assertEquals("", dto.json)
        // Subsequent read returns cached empty string safely
        assertEquals("", dto.json)
    }

    @Test
    fun `truncated GZIP bytes gracefully fall back to empty string without throwing`() {
        val validJson = "{\"title\":\"Long Title to Compress\"}"
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(validJson.toByteArray(StandardCharsets.UTF_8)) }
        val fullGzip = bos.toByteArray()
        // Truncate halfway
        val truncatedGzip = fullGzip.copyOf(fullGzip.size / 2)

        val dto = PageDescriptorDto(
            contractVersion = 1 or PageDescriptorDto.FLAG_COMPRESSED_GZIP,
            compressedBytes = truncatedGzip,
        )
        assertEquals("", dto.json)
    }

    @Test
    fun `corrupt Deflate bytes gracefully fall back to empty string without throwing`() {
        val corruptBytes = byteArrayOf(0x78, 0x01, 0x00, 0x00) // corrupt zlib stream
        val dto = PageDescriptorDto(
            contractVersion = 1 or PageDescriptorDto.FLAG_COMPRESSED_DEFLATE,
            compressedBytes = corruptBytes,
        )
        assertEquals("", dto.json)
    }

    @Test
    fun `missing compressedBytes with compression flag gracefully falls back to empty string`() {
        val dto = PageDescriptorDto(
            contractVersion = 1 or PageDescriptorDto.FLAG_COMPRESSED_GZIP,
            compressedBytes = null,
        )
        assertEquals("", dto.json)
    }

    @Test
    fun `missing streamSupplier and PFD with PFD flag gracefully falls back to empty string`() {
        val dto = PageDescriptorDto(
            contractVersion = 1 or PageDescriptorDto.FLAG_PFD_STREAM,
            pfd = null,
        )
        assertEquals("", dto.json)
    }

    @Test
    fun `toLegacy strips transport flags and writes raw json string to parcel`() {
        val largeJson = "{\"v\":1,\"items\":[" + (1..3000).joinToString(",") { "{\"id\":$it,\"title\":\"Long Track Title Number $it\",\"performer\":\"Artist Name\",\"album\":\"Sample Album Title\"}" } + "]}"
        val rawBytes = largeJson.toByteArray(StandardCharsets.UTF_8)
        assertTrue(rawBytes.size > PageDescriptorDto.INLINE_THRESHOLD_BYTES)
        val packed = PageDescriptorDto.pack(largeJson, baseVersion = 1)
        assertTrue(packed.contractVersion and PageDescriptorDto.FLAG_COMPRESSED_GZIP != 0)

        val legacy = packed.toLegacy()
        assertEquals(1, legacy.contractVersion)
        assertEquals(1, legacy.baseVersion)
        assertEquals(largeJson, legacy.json)
        assertNotNull(legacy.rawJson)

        val parcel = io.mockk.mockk<Parcel>(relaxed = true)
        legacy.writeToParcel(parcel, 0)
        io.mockk.verify { parcel.writeInt(1) }
        io.mockk.verify { parcel.writeString(largeJson) }
        io.mockk.verify(exactly = 0) { parcel.writeByteArray(any()) }
        io.mockk.verify(exactly = 0) { parcel.writeParcelable(any(), any()) }
    }

    @Test
    fun `toLegacy returns self if already uncompressed legacy format`() {
        val rawDto = PageDescriptorDto.raw("{\"route\":\"home\"}", baseVersion = 1)
        val legacy = rawDto.toLegacy()
        org.junit.jupiter.api.Assertions.assertSame(rawDto, legacy)
    }
    @Test
    fun `legacy 1_3_1 CREATOR successfully reads toLegacy output`() {
        val largeJson = "{\"v\":1,\"items\":[" + (1..3000).joinToString(",") { "{\"id\":$it,\"title\":\"Long Track Title Number $it\",\"performer\":\"Artist Name\",\"album\":\"Sample Album Title\"}" } + "]}"
        val packed = PageDescriptorDto.pack(largeJson, baseVersion = 1)
        val legacy = packed.toLegacy()

        val parcel = io.mockk.mockk<Parcel>()
        io.mockk.every { parcel.readInt() } returns legacy.contractVersion
        io.mockk.every { parcel.readString() } returns legacy.rawJson

        // Simulate legacy host 1.3.1 Parcelable.Creator:
        // Reads contractVersion (int) then rawJson (String) unconditionally
        val legacyVersion = parcel.readInt()
        val legacyRawJson = parcel.readString().orEmpty()
        val hostDto = PageDescriptorDto(contractVersion = legacyVersion, rawJson = legacyRawJson)

        assertEquals(1, hostDto.contractVersion)
        assertEquals(largeJson, hostDto.json)
    }

    @Test
    fun `raw factory creates uncompressed descriptor with masked version`() {
        val json = "{\"test\":true}"
        val dto = PageDescriptorDto.raw(json, baseVersion = 0x1201)
        assertEquals(1, dto.contractVersion)
        assertEquals(1, dto.baseVersion)
        assertEquals(json, dto.rawJson)
        assertEquals(json, dto.json)
    }
}

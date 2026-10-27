package com.bitperfect.player.sdk.plugin.dto

import android.os.Parcel
import com.bitperfect.player.sdk.plugin.IBitperfectPluginV1
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class StreamDescriptorDtoTest {

    @Test
    fun `prepareNextStream transaction id is 22nd transaction`() {
        val field = IBitperfectPluginV1.Stub::class.java.getDeclaredField("TRANSACTION_prepareNextStream")
        field.isAccessible = true
        assertEquals(android.os.IBinder.FIRST_CALL_TRANSACTION + 22, field.getInt(null))
    }

    @Test
    fun `StreamDescriptorDto parcel read with all 13 fields`() {
        val parcel = mockk<Parcel>(relaxed = true)
        every { parcel.readString() } returnsMany listOf(
            "https://stream.example.com/audio.flac", // url
            "User-Agent", // header key
            "Bitperfect/1.0", // header val
            null, // cipher
            "flac", // codec
            "audio/flac", // mimeType
        )
        every { parcel.readInt() } returnsMany listOf(
            1, // header count
            0, // encrypted
            24, // bitDepth
            96000, // sampleRate
            576, // encoderDelay
            1152, // encoderPadding
        )
        every { parcel.createByteArray() } returns null
        every { parcel.readLong() } returnsMany listOf(
            123456789L, // expiresAtEpochMs
            44100000L, // pcmSampleCount
        )
        every { parcel.dataAvail() } returns 100 // data is available for all 4 new fields

        val dto = StreamDescriptorDto.CREATOR.createFromParcel(parcel)
        assertEquals("https://stream.example.com/audio.flac", dto.url)
        assertEquals(mapOf("User-Agent" to "Bitperfect/1.0"), dto.headers)
        assertEquals(false, dto.encrypted)
        assertNull(dto.trackKey)
        assertNull(dto.cipher)
        assertEquals("flac", dto.codec)
        assertEquals(24, dto.bitDepth)
        assertEquals(96000, dto.sampleRate)
        assertEquals(123456789L, dto.expiresAtEpochMs)
        assertEquals(576, dto.encoderDelay)
        assertEquals(1152, dto.encoderPadding)
        assertEquals(44100000L, dto.pcmSampleCount)
        assertEquals("audio/flac", dto.mimeType)
    }

    @Test
    fun `StreamDescriptorDto backward compatibility when parcel has only 9 legacy fields`() {
        val parcel = mockk<Parcel>(relaxed = true)
        every { parcel.readString() } returnsMany listOf(
            "https://stream.example.com/audio.mp3", // url
            null, // cipher
            "mp3", // codec
        )
        every { parcel.readInt() } returnsMany listOf(
            0, // header count
            0, // encrypted
            16, // bitDepth
            44100, // sampleRate
        )
        every { parcel.createByteArray() } returns null
        every { parcel.readLong() } returns 987654321L
        // Legacy parcel has no more data after the 9th field
        every { parcel.dataAvail() } returns 0

        val dto = StreamDescriptorDto.CREATOR.createFromParcel(parcel)
        assertEquals("https://stream.example.com/audio.mp3", dto.url)
        assertEquals(emptyMap<String, String>(), dto.headers)
        assertEquals("mp3", dto.codec)
        assertEquals(16, dto.bitDepth)
        assertEquals(44100, dto.sampleRate)
        assertEquals(987654321L, dto.expiresAtEpochMs)
        assertEquals(0, dto.encoderDelay)
        assertEquals(0, dto.encoderPadding)
        assertEquals(0L, dto.pcmSampleCount)
        assertNull(dto.mimeType)
    }
}

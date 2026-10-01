package com.bitperfect.player.sdk.plugin.dto

import android.os.Parcel
import android.os.Parcelable

data class DspStreamFormatDto(
    val sampleRate: Int,
    val channels: Int,
    val channelMask: Int = 0,
    val sampleFormat: Int = SAMPLE_FORMAT_FLOAT32,
) : Parcelable {
    constructor(
        sampleRate: Int,
        channelCount: Int,
        bitDepth: Int = 32,
        encoding: Int = SAMPLE_FORMAT_FLOAT32,
        channelMask: Int = 0,
    ) : this(
        sampleRate = sampleRate,
        channels = channelCount,
        channelMask = channelMask,
        sampleFormat = encoding,
    )

    val channelCount: Int get() = channels
    val encoding: Int get() = sampleFormat
    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(sampleRate)
        dest.writeInt(channels)
        dest.writeInt(channelMask)
        dest.writeInt(sampleFormat)
    }

    companion object {
        const val SAMPLE_FORMAT_INT24_PACKED_IN_INT32 = 0
        const val SAMPLE_FORMAT_FLOAT32 = 1

        @JvmField
        val CREATOR = object : Parcelable.Creator<DspStreamFormatDto> {
            override fun createFromParcel(parcel: Parcel) = DspStreamFormatDto(
                sampleRate = parcel.readInt(),
                channels = parcel.readInt(),
                channelMask = parcel.readInt(),
                sampleFormat = parcel.readInt(),
            )

            override fun newArray(size: Int) = arrayOfNulls<DspStreamFormatDto>(size)
        }
    }
}

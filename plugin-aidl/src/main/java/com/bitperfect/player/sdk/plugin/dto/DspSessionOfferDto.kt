package com.bitperfect.player.sdk.plugin.dto

import android.os.BadParcelableException
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.Parcelable

data class DspSessionOfferDto(
    val sessionId: String,
    val nonce: Long,
    val format: DspStreamFormatDto,
    val chunkFrames: Int,
    val capacityFrames: Int,
    val shmemInFd: ParcelFileDescriptor?,
    val shmemOutFd: ParcelFileDescriptor?,
    val notifyInFd: ParcelFileDescriptor?,
    val notifyOutFd: ParcelFileDescriptor?,
) : Parcelable {
    override fun describeContents(): Int = if (
        shmemInFd != null || shmemOutFd != null || notifyInFd != null || notifyOutFd != null
    ) {
        Parcelable.CONTENTS_FILE_DESCRIPTOR
    } else {
        0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(sessionId)
        dest.writeLong(nonce)
        dest.writeParcelable(format, flags)
        dest.writeInt(chunkFrames)
        dest.writeInt(capacityFrames)
        dest.writeParcelable(shmemInFd, flags)
        dest.writeParcelable(shmemOutFd, flags)
        dest.writeParcelable(notifyInFd, flags)
        dest.writeParcelable(notifyOutFd, flags)
    }

    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<DspSessionOfferDto> {
            override fun createFromParcel(parcel: Parcel) = DspSessionOfferDto(
                sessionId = parcel.readString().orEmpty(),
                nonce = parcel.readLong(),
                format = parcel.readParcelable(DspStreamFormatDto::class.java.classLoader)
                    as? DspStreamFormatDto ?: throw BadParcelableException("Missing DSP stream format"),
                chunkFrames = parcel.readInt(),
                capacityFrames = parcel.readInt(),
                shmemInFd = parcel.readParcelable(ParcelFileDescriptor::class.java.classLoader),
                shmemOutFd = parcel.readParcelable(ParcelFileDescriptor::class.java.classLoader),
                notifyInFd = parcel.readParcelable(ParcelFileDescriptor::class.java.classLoader),
                notifyOutFd = parcel.readParcelable(ParcelFileDescriptor::class.java.classLoader),
            )

            override fun newArray(size: Int) = arrayOfNulls<DspSessionOfferDto>(size)
        }
    }
}

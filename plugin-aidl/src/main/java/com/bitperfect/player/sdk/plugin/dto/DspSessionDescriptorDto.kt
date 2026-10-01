package com.bitperfect.player.sdk.plugin.dto

import android.os.BadParcelableException
import android.os.IBinder
import android.os.Parcel
import android.os.Parcelable

data class DspSessionDescriptorDto(
    val stageInfo: DspStageInfoDto,
    val sessionBinder: IBinder?,
) : Parcelable {
    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeParcelable(stageInfo, flags)
        dest.writeStrongBinder(sessionBinder)
    }

    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<DspSessionDescriptorDto> {
            override fun createFromParcel(parcel: Parcel) = DspSessionDescriptorDto(
                stageInfo = parcel.readParcelable(DspStageInfoDto::class.java.classLoader)
                    as? DspStageInfoDto ?: throw BadParcelableException("Missing DSP stage info"),
                sessionBinder = parcel.readStrongBinder(),
            )

            override fun newArray(size: Int) = arrayOfNulls<DspSessionDescriptorDto>(size)
        }
    }
}

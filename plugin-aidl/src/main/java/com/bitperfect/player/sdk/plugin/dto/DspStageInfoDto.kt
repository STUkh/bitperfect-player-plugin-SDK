package com.bitperfect.player.sdk.plugin.dto

import android.os.Parcel
import android.os.Parcelable

data class DspStageInfoDto(
    val stageId: String,
    val displayName: String,
    val outputSampleRate: Int,
    val latencyFrames: Int,
    val isLengthChanging: Boolean,
) : Parcelable {
    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(stageId)
        dest.writeString(displayName)
        dest.writeInt(outputSampleRate)
        dest.writeInt(latencyFrames)
        dest.writeInt(if (isLengthChanging) 1 else 0)
    }

    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<DspStageInfoDto> {
            override fun createFromParcel(parcel: Parcel) = DspStageInfoDto(
                stageId = parcel.readString().orEmpty(),
                displayName = parcel.readString().orEmpty(),
                outputSampleRate = parcel.readInt(),
                latencyFrames = parcel.readInt(),
                isLengthChanging = parcel.readInt() != 0,
            )

            override fun newArray(size: Int) = arrayOfNulls<DspStageInfoDto>(size)
        }
    }
}

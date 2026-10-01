package com.bitperfect.player.sdk.plugin.dto

import android.os.Parcel
import android.os.Parcelable

data class RendererInfoDto(
    val rendererId: String,
    val displayName: String,
    val modelName: String,
    val supportsGapless: Boolean,
    val isAvailable: Boolean,
) : Parcelable {

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(rendererId)
        dest.writeString(displayName)
        dest.writeString(modelName)
        dest.writeInt(if (supportsGapless) 1 else 0)
        dest.writeInt(if (isAvailable) 1 else 0)
    }

    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<RendererInfoDto> {
            override fun createFromParcel(p: Parcel) = RendererInfoDto(
                rendererId = p.readString().orEmpty(),
                displayName = p.readString().orEmpty(),
                modelName = p.readString().orEmpty(),
                supportsGapless = p.readInt() == 1,
                isAvailable = p.readInt() == 1,
            )

            override fun newArray(size: Int) = arrayOfNulls<RendererInfoDto>(size)
        }
    }
}

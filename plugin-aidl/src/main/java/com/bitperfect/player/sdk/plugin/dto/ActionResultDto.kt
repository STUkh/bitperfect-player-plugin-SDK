package com.bitperfect.player.sdk.plugin.dto

import android.os.Parcel
import android.os.Parcelable

data class ActionResultDto(
    val kind: String,
    val json: String,
) : Parcelable {
    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(kind)
        dest.writeString(json)
    }

    companion object {
        const val KIND_NONE = "NONE"
        const val KIND_REFRESH = "REFRESH"
        const val KIND_NAVIGATE = "NAVIGATE"
        const val KIND_CHOOSE = "CHOOSE"
        const val KIND_TOAST = "TOAST"
        const val KIND_AUTH_REQUIRED = "AUTH_REQUIRED"

        @JvmField
        val CREATOR = object : Parcelable.Creator<ActionResultDto> {
            override fun createFromParcel(parcel: Parcel) = ActionResultDto(
                kind = parcel.readString().orEmpty(),
                json = parcel.readString().orEmpty(),
            )

            override fun newArray(size: Int) = arrayOfNulls<ActionResultDto>(size)
        }
    }
}

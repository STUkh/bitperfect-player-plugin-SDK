package com.bitperfect.player.sdk.plugin.dto

import android.os.Parcel
import android.os.Parcelable

data class PageDescriptorDto(
    val contractVersion: Int,
    val json: String,
) : Parcelable {
    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(contractVersion)
        dest.writeString(json)
    }

    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<PageDescriptorDto> {
            override fun createFromParcel(parcel: Parcel) = PageDescriptorDto(
                contractVersion = parcel.readInt(),
                json = parcel.readString().orEmpty(),
            )

            override fun newArray(size: Int) = arrayOfNulls<PageDescriptorDto>(size)
        }
    }
}

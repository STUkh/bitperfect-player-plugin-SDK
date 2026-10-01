package com.bitperfect.player.sdk.plugin.dto

import android.os.Parcel
import android.os.Parcelable

data class BrowseResultDto(
    val items: List<BrowseItemDto>,
    val nextPageToken: String?,
) : Parcelable {

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeTypedList(items)
        dest.writeString(nextPageToken)
    }

    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<BrowseResultDto> {
            override fun createFromParcel(p: Parcel) = BrowseResultDto(
                items = p.createTypedArrayList(BrowseItemDto.CREATOR).orEmpty(),
                nextPageToken = p.readString(),
            )

            override fun newArray(size: Int) = arrayOfNulls<BrowseResultDto>(size)
        }
    }
}

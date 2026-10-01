package com.bitperfect.player.sdk.plugin.dto

import android.os.Parcel
import android.os.Parcelable

data class SearchResultDto(
    val tracks: List<TrackRefDto>,
    val nextPageToken: String?,
) : Parcelable {

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeTypedList(tracks)
        dest.writeString(nextPageToken)
    }

    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<SearchResultDto> {
            override fun createFromParcel(p: Parcel) = SearchResultDto(
                tracks = p.createTypedArrayList(TrackRefDto.CREATOR).orEmpty(),
                nextPageToken = p.readString(),
            )

            override fun newArray(size: Int) = arrayOfNulls<SearchResultDto>(size)
        }
    }
}

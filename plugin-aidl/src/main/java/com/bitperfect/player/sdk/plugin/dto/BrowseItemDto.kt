package com.bitperfect.player.sdk.plugin.dto

import android.os.Parcel
import android.os.Parcelable

data class BrowseItemDto(
    val id: String,
    val title: String,
    val subtitle: String?,
    val isContainer: Boolean,
    val artUrl: String?,
    val track: TrackRefDto?,
) : Parcelable {

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(id)
        dest.writeString(title)
        dest.writeString(subtitle)
        dest.writeInt(if (isContainer) 1 else 0)
        dest.writeString(artUrl)
        dest.writeParcelable(track, flags)
    }

    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<BrowseItemDto> {
            override fun createFromParcel(p: Parcel) = BrowseItemDto(
                id = p.readString().orEmpty(),
                title = p.readString().orEmpty(),
                subtitle = p.readString(),
                isContainer = p.readInt() == 1,
                artUrl = p.readString(),
                track = readTrack(p),
            )

            override fun newArray(size: Int) = arrayOfNulls<BrowseItemDto>(size)
        }

        private fun readTrack(p: Parcel): TrackRefDto? =
            @Suppress("DEPRECATION")
            p.readParcelable(TrackRefDto::class.java.classLoader)
    }
}

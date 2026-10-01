package com.bitperfect.player.sdk.plugin.dto

import android.os.Parcel
import android.os.Parcelable

data class AlbumDto(
    val albumId: String,
    val title: String,
    val artist: String,
    val artUrl: String?,
    val tracks: List<TrackRefDto>,
) : Parcelable {

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(albumId)
        dest.writeString(title)
        dest.writeString(artist)
        dest.writeString(artUrl)
        dest.writeTypedList(tracks)
    }

    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<AlbumDto> {
            override fun createFromParcel(p: Parcel) = AlbumDto(
                albumId = p.readString().orEmpty(),
                title = p.readString().orEmpty(),
                artist = p.readString().orEmpty(),
                artUrl = p.readString(),
                tracks = p.createTypedArrayList(TrackRefDto.CREATOR).orEmpty(),
            )

            override fun newArray(size: Int) = arrayOfNulls<AlbumDto>(size)
        }
    }
}

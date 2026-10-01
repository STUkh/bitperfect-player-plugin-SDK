package com.bitperfect.player.sdk.plugin.dto

import android.os.Parcel
import android.os.Parcelable

data class PlaybackTrackDto(
    val trackId: String,
    val albumId: String?,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val artworkUrl: String?,
    val quality: String?,
) : Parcelable {
    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(trackId)
        dest.writeString(albumId)
        dest.writeString(title)
        dest.writeString(artist)
        dest.writeString(album)
        dest.writeLong(durationMs)
        dest.writeString(artworkUrl)
        dest.writeString(quality)
    }

    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<PlaybackTrackDto> {
            override fun createFromParcel(parcel: Parcel) = PlaybackTrackDto(
                trackId = parcel.readString().orEmpty(),
                albumId = parcel.readString(),
                title = parcel.readString().orEmpty(),
                artist = parcel.readString().orEmpty(),
                album = parcel.readString().orEmpty(),
                durationMs = parcel.readLong(),
                artworkUrl = parcel.readString(),
                quality = parcel.readString(),
            )

            override fun newArray(size: Int) = arrayOfNulls<PlaybackTrackDto>(size)
        }
    }
}

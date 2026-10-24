package com.bitperfect.player.sdk.plugin.dto

import android.os.Parcel
import android.os.Parcelable

data class TrackRefDto(
    val pluginId: String,
    val trackId: String,
    val albumId: String?,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val album: String? = null,
    val artworkUrl: String? = null,
) : Parcelable {

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(pluginId)
        dest.writeString(trackId)
        dest.writeString(albumId)
        dest.writeString(title)
        dest.writeString(artist)
        dest.writeLong(durationMs)
        dest.writeString(album)
        dest.writeString(artworkUrl)
    }

    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<TrackRefDto> {
            override fun createFromParcel(p: Parcel) = TrackRefDto(
                pluginId = p.readString().orEmpty(),
                trackId = p.readString().orEmpty(),
                albumId = p.readString(),
                title = p.readString().orEmpty(),
                artist = p.readString().orEmpty(),
                durationMs = p.readLong(),
                album = p.readString(),
                artworkUrl = p.readString(),
            )

            override fun newArray(size: Int) = arrayOfNulls<TrackRefDto>(size)
        }
    }
}

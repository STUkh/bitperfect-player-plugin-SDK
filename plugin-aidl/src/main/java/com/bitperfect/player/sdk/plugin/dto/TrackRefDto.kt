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
) : Parcelable {

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(pluginId)
        dest.writeString(trackId)
        dest.writeString(albumId)
        dest.writeString(title)
        dest.writeString(artist)
        dest.writeLong(durationMs)
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
            )

            override fun newArray(size: Int) = arrayOfNulls<TrackRefDto>(size)
        }
    }
}

package com.bitperfect.player.sdk.plugin.dto

import android.os.Parcel
import android.os.Parcelable

// A playlist in the host's own library, offered to a plugin so it can add tracks to one.
data class LibraryPlaylistDto(
    val playlistId: String,
    val title: String,
    val trackCount: Int,
) : Parcelable {
    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(playlistId)
        dest.writeString(title)
        dest.writeInt(trackCount)
    }

    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<LibraryPlaylistDto> {
            override fun createFromParcel(parcel: Parcel) = LibraryPlaylistDto(
                playlistId = parcel.readString().orEmpty(),
                title = parcel.readString().orEmpty(),
                trackCount = parcel.readInt(),
            )

            override fun newArray(size: Int) = arrayOfNulls<LibraryPlaylistDto>(size)
        }
    }
}

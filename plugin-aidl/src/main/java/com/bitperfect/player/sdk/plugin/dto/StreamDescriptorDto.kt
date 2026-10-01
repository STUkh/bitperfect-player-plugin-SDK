package com.bitperfect.player.sdk.plugin.dto

import android.os.Parcel
import android.os.Parcelable

data class StreamDescriptorDto(
    val url: String,
    val headers: Map<String, String>,
    val encrypted: Boolean,
    val trackKey: ByteArray?,
    val cipher: String?,
    val codec: String,
    val bitDepth: Int,
    val sampleRate: Int,
    val expiresAtEpochMs: Long,
) : Parcelable {

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(url)
        dest.writeInt(headers.size)
        for ((k, v) in headers) {
            dest.writeString(k)
            dest.writeString(v)
        }
        dest.writeInt(if (encrypted) 1 else 0)
        dest.writeByteArray(trackKey)
        dest.writeString(cipher)
        dest.writeString(codec)
        dest.writeInt(bitDepth)
        dest.writeInt(sampleRate)
        dest.writeLong(expiresAtEpochMs)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StreamDescriptorDto) return false
        return url == other.url && headers == other.headers && encrypted == other.encrypted &&
            trackKey.contentEquals(other.trackKey) && cipher == other.cipher && codec == other.codec &&
            bitDepth == other.bitDepth && sampleRate == other.sampleRate &&
            expiresAtEpochMs == other.expiresAtEpochMs
    }

    override fun hashCode(): Int {
        var result = url.hashCode()
        result = 31 * result + headers.hashCode()
        result = 31 * result + encrypted.hashCode()
        result = 31 * result + (trackKey?.contentHashCode() ?: 0)
        result = 31 * result + (cipher?.hashCode() ?: 0)
        result = 31 * result + codec.hashCode()
        result = 31 * result + bitDepth
        result = 31 * result + sampleRate
        result = 31 * result + expiresAtEpochMs.hashCode()
        return result
    }

    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<StreamDescriptorDto> {
            override fun createFromParcel(p: Parcel): StreamDescriptorDto {
                val url = p.readString().orEmpty()
                val n = p.readInt()
                val headers = LinkedHashMap<String, String>(n)
                repeat(n) { headers[p.readString().orEmpty()] = p.readString().orEmpty() }
                return StreamDescriptorDto(
                    url = url,
                    headers = headers,
                    encrypted = p.readInt() == 1,
                    trackKey = p.createByteArray(),
                    cipher = p.readString(),
                    codec = p.readString().orEmpty(),
                    bitDepth = p.readInt(),
                    sampleRate = p.readInt(),
                    expiresAtEpochMs = p.readLong(),
                )
            }

            override fun newArray(size: Int) = arrayOfNulls<StreamDescriptorDto>(size)
        }
    }
}

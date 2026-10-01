package com.bitperfect.player.sdk.plugin.dto

import android.os.Parcel
import android.os.Parcelable

data class HttpResponseDto(
    val statusCode: Int,
    val contentType: String,
    val body: ByteArray,
) : Parcelable {
    val bodyString: String get() = String(body, Charsets.UTF_8)

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(statusCode)
        dest.writeString(contentType)
        dest.writeInt(body.size)
        dest.writeByteArray(body)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as HttpResponseDto
        if (statusCode != other.statusCode) return false
        if (contentType != other.contentType) return false
        if (!body.contentEquals(other.body)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = statusCode
        result = 31 * result + contentType.hashCode()
        result = 31 * result + body.contentHashCode()
        return result
    }

    companion object {
        fun okJson(json: String) = HttpResponseDto(200, "application/json", json.toByteArray(Charsets.UTF_8))
        fun notFound() = HttpResponseDto(404, "text/plain", "Not Found".toByteArray(Charsets.UTF_8))
        fun error(code: Int, message: String) = HttpResponseDto(code, "text/plain", message.toByteArray(Charsets.UTF_8))

        @JvmField
        val CREATOR = object : Parcelable.Creator<HttpResponseDto> {
            override fun createFromParcel(parcel: Parcel): HttpResponseDto {
                val statusCode = parcel.readInt()
                val contentType = parcel.readString().orEmpty()
                val size = parcel.readInt()
                val body = ByteArray(size)
                parcel.readByteArray(body)
                return HttpResponseDto(statusCode, contentType, body)
            }

            override fun newArray(size: Int) = arrayOfNulls<HttpResponseDto>(size)
        }
    }
}

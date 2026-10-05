package com.bitperfect.player.sdk.plugin.dto

import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.Parcelable
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

/**
 * 3-Tier Transport Architecture for large declarative plugin pages across Android Binder IPC:
 * - Tier 1 (Inline raw string, <= 16 KB): contractVersion, dest.writeString(rawJson).
 * - Tier 2 (Compressed GZIP byte array, <= 512 KB compressed): contractVersion or FLAG_COMPRESSED_GZIP, dest.writeByteArray(compressedBytes).
 * - Tier 3 (Stream via ParcelFileDescriptor, for extreme payloads): contractVersion or FLAG_PFD_STREAM, dest.writeParcelable(pfd, flags).
 *
 * Full backward and forward compatibility:
 * - If contractVersion has no flags (flags == 0), it reads parcel.readString() as raw JSON.
 * - `val json: String` transparently returns the uncompressed JSON string via lazy decompression or stream read.
 * - `describeContents()` returns CONTENTS_FILE_DESCRIPTOR if pfd != null else 0.
 */
class PageDescriptorDto internal constructor(
    val contractVersion: Int,
    val rawJson: String? = null,
    internal val compressedBytes: ByteArray? = null,
    val pfd: ParcelFileDescriptor? = null,
    internal val streamSupplier: (() -> InputStream)? = null,
) : Parcelable {
    val baseVersion: Int
        get() = contractVersion and VERSION_MASK

    /**
     * Transparent resolution: host UI calls `descriptor.json` exactly as before.
     * Transparently resolves from inline string, decompresses GZIP/Deflate blob, or reads PFD stream.
     */
    val json: String by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        try {
            when {
                contractVersion and FLAG_PFD_STREAM != 0 -> {
                    val stream = streamSupplier?.invoke()
                        ?: pfd?.let { ParcelFileDescriptor.AutoCloseInputStream(it) }
                        ?: return@lazy ""
                    stream.use { s ->
                        if (contractVersion and FLAG_COMPRESSED_GZIP != 0) {
                            GZIPInputStream(s).use { it.readBytes().toString(StandardCharsets.UTF_8) }
                        } else {
                            s.readBytes().toString(StandardCharsets.UTF_8)
                        }
                    }
                }
                contractVersion and FLAG_COMPRESSED_GZIP != 0 -> {
                    val bytes = compressedBytes ?: return@lazy ""
                    GZIPInputStream(ByteArrayInputStream(bytes)).use {
                        it.readBytes().toString(StandardCharsets.UTF_8)
                    }
                }
                contractVersion and FLAG_COMPRESSED_DEFLATE != 0 -> {
                    val bytes = compressedBytes ?: return@lazy ""
                    val inflater = Inflater(false)
                    try {
                        InflaterInputStream(ByteArrayInputStream(bytes), inflater).use {
                            it.readBytes().toString(StandardCharsets.UTF_8)
                        }
                    } finally {
                        inflater.end()
                    }
                }
                else -> {
                    rawJson.orEmpty()
                }
            }
        } catch (_: Throwable) {
            ""
        }
    }

    override fun describeContents(): Int =
        if (pfd != null) Parcelable.CONTENTS_FILE_DESCRIPTOR else 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(contractVersion)
        when {
            contractVersion and FLAG_PFD_STREAM != 0 -> {
                dest.writeParcelable(pfd, flags)
            }
            contractVersion and (FLAG_COMPRESSED_GZIP or FLAG_COMPRESSED_DEFLATE) != 0 -> {
                dest.writeByteArray(compressedBytes)
            }
            else -> {
                dest.writeString(rawJson ?: json)
            }
        }
    }

    operator fun component1(): Int = contractVersion
    operator fun component2(): String = json

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PageDescriptorDto) return false
        return contractVersion == other.contractVersion && json == other.json
    }

    override fun hashCode(): Int {
        var result = contractVersion
        result = 31 * result + json.hashCode()
        return result
    }

    override fun toString(): String =
        "PageDescriptorDto(contractVersion=$contractVersion, jsonLength=${json.length})"

    companion object {
        const val FLAG_PFD_STREAM = 1 shl 13          // 0x2000 (ParcelFileDescriptor stream)
        const val FLAG_COMPRESSED_DEFLATE = 1 shl 14  // 0x4000 (ZLIB Deflate)
        const val FLAG_COMPRESSED_GZIP = 1 shl 15     // 0x8000 (GZIP stream)
        const val VERSION_MASK = 0x00FF               // Lower 8 bits for base API version
        const val INLINE_THRESHOLD_BYTES = 16 * 1024       // 16 KB
        const val COMPRESSION_THRESHOLD_BYTES = 512 * 1024 // 512 KB

        @JvmStatic
        fun pack(json: String, baseVersion: Int = 1): PageDescriptorDto {
            val rawBytes = json.toByteArray(StandardCharsets.UTF_8)
            return when {
                rawBytes.size <= INLINE_THRESHOLD_BYTES -> {
                    PageDescriptorDto(contractVersion = baseVersion, rawJson = json)
                }
                else -> {
                    val bos = ByteArrayOutputStream()
                    GZIPOutputStream(bos).use { it.write(rawBytes) }
                    val compressed = bos.toByteArray()
                    if (compressed.size <= COMPRESSION_THRESHOLD_BYTES) {
                        PageDescriptorDto(
                            contractVersion = baseVersion or FLAG_COMPRESSED_GZIP,
                            compressedBytes = compressed,
                        )
                    } else {
                        try {
                            val pipe = ParcelFileDescriptor.createPipe()
                            val readFd = pipe[0]
                            val writeFd = pipe[1]
                            Thread {
                                try {
                                    ParcelFileDescriptor.AutoCloseOutputStream(writeFd).use { out ->
                                        out.write(rawBytes)
                                    }
                                } catch (_: IOException) {
                                    // Broken pipe / consumer closed
                                }
                            }.apply {
                                isDaemon = true
                                name = "PageDescriptorDto-PipeWriter"
                            }.start()
                            PageDescriptorDto(
                                contractVersion = baseVersion or FLAG_PFD_STREAM,
                                pfd = readFd,
                            )
                        } catch (_: Throwable) {
                            // Fallback for non-Android unit test environment
                            PageDescriptorDto(
                                contractVersion = baseVersion or FLAG_PFD_STREAM,
                                streamSupplier = { ByteArrayInputStream(rawBytes) },
                            )
                        }
                    }
                }
            }
        }

        @JvmField
        val CREATOR = object : Parcelable.Creator<PageDescriptorDto> {
            override fun createFromParcel(parcel: Parcel): PageDescriptorDto {
                val version = parcel.readInt()
                return when {
                    version and FLAG_PFD_STREAM != 0 -> {
                        @Suppress("DEPRECATION")
                        val pfd = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            parcel.readParcelable(ParcelFileDescriptor::class.java.classLoader, ParcelFileDescriptor::class.java)
                        } else {
                            parcel.readParcelable(ParcelFileDescriptor::class.java.classLoader)
                        }
                        PageDescriptorDto(contractVersion = version, pfd = pfd)
                    }
                    version and (FLAG_COMPRESSED_GZIP or FLAG_COMPRESSED_DEFLATE) != 0 -> {
                        val bytes = parcel.createByteArray()
                        PageDescriptorDto(contractVersion = version, compressedBytes = bytes ?: ByteArray(0))
                    }
                    else -> {
                        val str = parcel.readString().orEmpty()
                        PageDescriptorDto(contractVersion = version, rawJson = str)
                    }
                }
            }

            override fun newArray(size: Int): Array<PageDescriptorDto?> = arrayOfNulls(size)
        }
    }
}

fun String.toPageDescriptorDto(baseVersion: Int = 1): PageDescriptorDto =
    PageDescriptorDto.pack(this, baseVersion)

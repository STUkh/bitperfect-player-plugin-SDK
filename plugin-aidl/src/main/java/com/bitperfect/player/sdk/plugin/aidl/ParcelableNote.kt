package com.bitperfect.player.sdk.plugin.aidl

// ---------------------------------------------------------------------------
// Parcelable data transfer conventions for :sdk:plugin-aidl
// ---------------------------------------------------------------------------
//
// All Parcelable types exchanged over IBitperfectPluginV1 must follow these rules:
//
// 1. Implement android.os.Parcelable via the @Parcelize annotation
//    (kotlin-parcelize plugin) to avoid hand-written CREATOR boilerplate.
//
// 2. Every Parcelable class must include a PARCELABLE_WRITE_RETURN_VALUE flag
//    check in writeToParcel() if it holds nested Parcelables, so that
//    FLAG_RETURN_VALUE is propagated correctly across process boundaries.
//
// 3. Fields added in later API versions must be guarded by a version check
//    when reading, because older senders will not have written them.
//    Pattern:
//       val newField = if (source.readInt() == 1) source.readString() else null
//
// 4. No Serializable or Java-serialization-based types across AIDL boundaries —
//    use Parcelable or primitive types only.
//
// 5. String fields must cope with null (readString() can return null on older
//    Android versions); use ?: "" or ?: defaults defensively.
//
// This file is informational only — no runtime code.  Delete or replace it
// when the first real Parcelable DTOs are introduced.

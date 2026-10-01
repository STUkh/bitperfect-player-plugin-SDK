package com.bitperfect.player.sdk.plugin.runtime

import android.content.Context
import android.content.pm.PackageManager

internal object PluginHostTrust {
    private const val HOST_PACKAGE = "com.bitperfect.player"
    private const val DEBUG_HOST_PACKAGE = "$HOST_PACKAGE.debug"
    private val officialHostSigners = setOf(
        "94e1e1bdb6afccb3a09312ad0c3df7bf49faaac4b8dd9d80f130a6c4906c9183",
        "df373938a7b06aaaf70a78237ac713ce85fa23b3f63a36387b9d9ce2b64456be",
    )

    fun isTrustedCaller(context: Context, callingUid: Int): Boolean =
        isTrustedPackage(context, HOST_PACKAGE, callingUid, allowSameSigner = false) ||
            isTrustedPackage(context, DEBUG_HOST_PACKAGE, callingUid, allowSameSigner = true)

    private fun isTrustedPackage(
        context: Context,
        packageName: String,
        callingUid: Int,
        allowSameSigner: Boolean,
    ): Boolean {
        val packageManager = context.packageManager
        val packageUid = runCatching { packageManager.getPackageUid(packageName, 0) }.getOrNull()
        if (packageUid != callingUid) return false
        if (SignatureTrust.signerFingerprints(context, packageName).any(officialHostSigners::contains)) return true
        return allowSameSigner && runCatching {
            packageManager.checkSignatures(context.packageName, packageName) == PackageManager.SIGNATURE_MATCH
        }.getOrDefault(false)
    }
}

package com.bitperfect.player.sdk.plugin.runtime

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.security.MessageDigest
import java.util.Locale

/** SHA-256 signing-certificate allowlist check, usable by host (API 26–30 gate) and plugins alike. */
object SignatureTrust {

    fun isTrusted(context: Context, packageName: String, allowlist: Set<String>): Boolean {
        val normalized = allowlist.mapTo(HashSet()) { normalizeFingerprint(it) }
        return signerFingerprints(context, packageName).any { it in normalized }
    }

    fun signerFingerprints(context: Context, packageName: String): List<String> {
        val signatures = signatures(context, packageName) ?: return emptyList()
        val digest = MessageDigest.getInstance("SHA-256")
        return signatures.map { sig ->
            digest.digest(sig.toByteArray()).joinToString("") { "%02x".format(it) }
        }
    }

    private fun signatures(context: Context, packageName: String): Array<Signature>? = runCatching {
        val pm = context.packageManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            val signing = info.signingInfo ?: return@runCatching null
            if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES).signatures
        }
    }.getOrNull()

    private fun normalizeFingerprint(raw: String): String =
        raw.replace(":", "").replace(" ", "").lowercase(Locale.ROOT)
}

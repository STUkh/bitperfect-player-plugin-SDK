package com.bitperfect.player.sdk.plugin.runtime

import android.content.Context
import android.os.Binder
import android.os.Build
import android.os.Process
import com.bitperfect.player.sdk.plugin.dto.PageDescriptorDto
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONObject

internal object HostTransportNegotiator {
    private val uidCache = ConcurrentHashMap<Int, Boolean>()

    private const val HOST_PACKAGE_RELEASE = "com.bitperfect.player"
    private const val HOST_PACKAGE_DEBUG = "com.bitperfect.player.debug"
    private const val MIN_TIERED_HOST_VERSION_CODE = 60L

    fun isTieredTransportSupported(
        context: Context?,
        paramsJson: String?,
        callingUid: Int = Binder.getCallingUid(),
    ): Boolean {
        if (isTieredParamPresent(paramsJson)) return true
        if (callingUid == Process.myUid()) return true

        uidCache[callingUid]?.let { return it }

        val supported = resolveHostViaPackageManager(context, callingUid)
        uidCache[callingUid] = supported
        return supported
    }

    private fun isTieredParamPresent(paramsJson: String?): Boolean {
        if (paramsJson == null || !paramsJson.contains(PageDescriptorDto.PARAM_HOST_TRANSPORT)) {
            return false
        }
        val fromJson = runCatching {
            JSONObject(paramsJson).optString(PageDescriptorDto.PARAM_HOST_TRANSPORT)
        }.getOrNull()
        if (fromJson == PageDescriptorDto.TRANSPORT_TIERED) return true

        return paramsJson.contains(
            "\"${PageDescriptorDto.PARAM_HOST_TRANSPORT}\"\\s*:\\s*\"${PageDescriptorDto.TRANSPORT_TIERED}\"".toRegex()
        )
    }

    private fun resolveHostViaPackageManager(context: Context?, callingUid: Int): Boolean {
        if (context == null) return false
        return runCatching {
            val pm = context.packageManager
            val packages = pm.getPackagesForUid(callingUid) ?: return false
            val hostPkg = packages.firstOrNull {
                it == HOST_PACKAGE_RELEASE || it == HOST_PACKAGE_DEBUG
            } ?: return false

            val packageInfo = pm.getPackageInfo(hostPkg, 0)
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
            versionCode >= MIN_TIERED_HOST_VERSION_CODE
        }.getOrDefault(false)
    }

    internal fun clearCacheForTests() {
        uidCache.clear()
    }
}

package com.bitperfect.player.sdk.plugin.runtime

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import com.bitperfect.player.sdk.plugin.dto.PageDescriptorDto
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class HostTransportNegotiatorTest {

    @BeforeEach
    @AfterEach
    fun reset() {
        HostTransportNegotiator.clearCacheForTests()
    }

    @Test
    fun `tiered transport supported when transport parameter is present in paramsJson`() {
        val params = "{\"${PageDescriptorDto.PARAM_HOST_TRANSPORT}\":\"${PageDescriptorDto.TRANSPORT_TIERED}\"}"
        val supported = HostTransportNegotiator.isTieredTransportSupported(
            context = null,
            paramsJson = params,
            callingUid = 99999,
        )
        assertTrue(supported)
    }

    @Test
    fun `tiered transport supported when caller is same process`() {
        val supported = HostTransportNegotiator.isTieredTransportSupported(
            context = null,
            paramsJson = null,
            callingUid = Process.myUid(),
        )
        assertTrue(supported)
    }

    @Test
    fun `tiered transport returns false when context is null and caller is different uid without param`() {
        val supported = HostTransportNegotiator.isTieredTransportSupported(
            context = null,
            paramsJson = "{}",
            callingUid = 99999,
        )
        assertFalse(supported)
    }

    @Test
    fun `tiered transport resolves true via package manager for modern host versionCode 60`() {
        val context = mockk<Context>()
        val pm = mockk<PackageManager>()
        every { context.packageManager } returns pm
        val callingUid = 10050
        every { pm.getPackagesForUid(callingUid) } returns arrayOf("com.bitperfect.player")
        val pkgInfo = PackageInfo().apply {
            versionCode = 60
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                longVersionCode = 60L
            }
        }
        every { pm.getPackageInfo("com.bitperfect.player", 0) } returns pkgInfo

        val supported = HostTransportNegotiator.isTieredTransportSupported(
            context = context,
            paramsJson = null,
            callingUid = callingUid,
        )
        assertTrue(supported)

        // Check uidCache: second call doesn't re-query pm
        HostTransportNegotiator.isTieredTransportSupported(
            context = context,
            paramsJson = null,
            callingUid = callingUid,
        )
        verify(exactly = 1) { pm.getPackageInfo("com.bitperfect.player", 0) }
    }

    @Test
    fun `tiered transport resolves false via package manager for legacy host versionCode 59`() {
        val context = mockk<Context>()
        val pm = mockk<PackageManager>()
        every { context.packageManager } returns pm
        val callingUid = 10051
        every { pm.getPackagesForUid(callingUid) } returns arrayOf("com.bitperfect.player")
        val pkgInfo = PackageInfo().apply {
            versionCode = 59
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                longVersionCode = 59L
            }
        }
        every { pm.getPackageInfo("com.bitperfect.player", 0) } returns pkgInfo

        val supported = HostTransportNegotiator.isTieredTransportSupported(
            context = context,
            paramsJson = null,
            callingUid = callingUid,
        )
        assertFalse(supported)
    }

    @Test
    fun `tiered transport resolves true for debug host package`() {
        val context = mockk<Context>()
        val pm = mockk<PackageManager>()
        every { context.packageManager } returns pm
        val callingUid = 10052
        every { pm.getPackagesForUid(callingUid) } returns arrayOf("com.bitperfect.player.debug")
        val pkgInfo = PackageInfo().apply {
            versionCode = 61
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                longVersionCode = 61L
            }
        }
        every { pm.getPackageInfo("com.bitperfect.player.debug", 0) } returns pkgInfo

        val supported = HostTransportNegotiator.isTieredTransportSupported(
            context = context,
            paramsJson = null,
            callingUid = callingUid,
        )
        assertTrue(supported)
    }
}

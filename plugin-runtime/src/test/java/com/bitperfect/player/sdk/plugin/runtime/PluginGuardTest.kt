package com.bitperfect.player.sdk.plugin.runtime

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import com.bitperfect.player.sdk.plugin.IBitperfectPluginV1
import com.bitperfect.player.sdk.plugin.IPluginHostCallbackV1
import com.bitperfect.player.sdk.plugin.IPluginHttpHandlerV1
import com.bitperfect.player.sdk.plugin.dto.LibraryPlaylistDto
import com.bitperfect.player.sdk.plugin.dto.PlaybackTrackDto
import com.bitperfect.player.sdk.plugin.dto.RendererInfoDto
import com.bitperfect.player.sdk.plugin.dto.StreamDescriptorDto
import com.bitperfect.player.sdk.plugin.dto.TrackRefDto
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PluginGuardTest {
    private val context = mockk<Context>()
    private val packageManager = mockk<PackageManager>()

    @AfterEach
    fun tearDown() = unmockkAll()

    @Test
    fun `unrelated UID is denied`() {
        trustContext()
        every { packageManager.getPackageUid("com.bitperfect.player", 0) } returns 100
        every { packageManager.getPackageUid("com.bitperfect.player.debug", 0) } returns 101

        assertFalse(PluginHostTrust.isTrustedCaller(context, 999))
    }

    @Test
    fun `spoofed production package with untrusted signer is denied`() {
        trustContext()
        every { packageManager.getPackageUid("com.bitperfect.player", 0) } returns 100
        every { packageManager.getPackageUid("com.bitperfect.player.debug", 0) } returns 101
        every { SignatureTrust.signerFingerprints(context, "com.bitperfect.player") } returns emptyList()
        every { packageManager.checkSignatures("com.example.plugin", "com.bitperfect.player") } returns
            PackageManager.SIGNATURE_NO_MATCH

        assertFalse(PluginHostTrust.isTrustedCaller(context, 100))
    }

    @Test
    fun `official host signer is accepted without plugin signer match`() {
        trustContext()
        every { packageManager.getPackageUid("com.bitperfect.player", 0) } returns 100
        every { SignatureTrust.signerFingerprints(context, "com.bitperfect.player") } returns listOf(
            "94e1e1bdb6afccb3a09312ad0c3df7bf49faaac4b8dd9d80f130a6c4906c9183",
        )

        assertTrue(PluginHostTrust.isTrustedCaller(context, 100))
    }

    @Test
    fun `same signed debug host is accepted`() {
        trustContext()
        every { packageManager.getPackageUid("com.bitperfect.player", 0) } returns 101
        every { packageManager.getPackageUid("com.bitperfect.player.debug", 0) } returns 100
        every { SignatureTrust.signerFingerprints(context, "com.bitperfect.player.debug") } returns emptyList()
        every { packageManager.checkSignatures("com.example.plugin", "com.bitperfect.player.debug") } returns
            PackageManager.SIGNATURE_MATCH

        assertTrue(PluginHostTrust.isTrustedCaller(context, 100))
    }

    @Test
    fun `unrelated debug signer is denied`() {
        trustContext()
        every { packageManager.getPackageUid("com.bitperfect.player", 0) } returns 101
        every { packageManager.getPackageUid("com.bitperfect.player.debug", 0) } returns 100
        every { SignatureTrust.signerFingerprints(context, "com.bitperfect.player.debug") } returns emptyList()
        every { packageManager.checkSignatures("com.example.plugin", "com.bitperfect.player.debug") } returns
            PackageManager.SIGNATURE_NO_MATCH

        assertFalse(PluginHostTrust.isTrustedCaller(context, 100))
    }

    @Test
    fun `missing host package is denied without throwing`() {
        trustContext()
        every { packageManager.getPackageUid(any(), 0) } throws PackageManager.NameNotFoundException()

        assertFalse(PluginHostTrust.isTrustedCaller(context, 100))
    }

    @Test
    fun `production signature check failure is denied without throwing`() {
        trustContext()
        every { packageManager.getPackageUid("com.bitperfect.player", 0) } returns 100
        every { packageManager.getPackageUid("com.bitperfect.player.debug", 0) } returns 101
        every { SignatureTrust.signerFingerprints(context, "com.bitperfect.player") } returns emptyList()
        every { packageManager.checkSignatures("com.example.plugin", "com.bitperfect.player") } throws
            SecurityException("package manager unavailable")

        assertFalse(PluginHostTrust.isTrustedCaller(context, 100))
    }

    @Test
    fun `source transaction denies untrusted caller before stream resolution`() {
        val plugin = SourceProbe()
        mockkObject(PluginHostTrust)
        mockkStatic(Binder::class)
        every { Binder.getCallingUid() } returns 999
        every { PluginHostTrust.isTrustedCaller(any(), 999) } returns false

        assertThrows(SecurityException::class.java) {
            (plugin.onBind(null) as IBitperfectPluginV1.Stub).onTransact(
                IBinder.FIRST_CALL_TRANSACTION + 3, mockk(relaxed = true), mockk(relaxed = true), 0,
            )
        }
        assertFalse(plugin.resolved)
    }

    @Test
    fun `renderer transaction denies untrusted caller before renderer enumeration`() {
        val plugin = RendererProbe()
        mockkObject(PluginHostTrust)
        mockkStatic(Binder::class)
        every { Binder.getCallingUid() } returns 999
        every { PluginHostTrust.isTrustedCaller(any(), 999) } returns false

        assertThrows(SecurityException::class.java) {
            (plugin.onBind(null) as IBitperfectPluginV1.Stub).onTransact(
                IBinder.FIRST_CALL_TRANSACTION + 8, mockk(relaxed = true), mockk(relaxed = true), 0,
            )
        }
        assertFalse(plugin.enumerated)
    }

    @Test
    fun `trusted source transaction dispatches resolved descriptor`() {
        val plugin = SourceProbe()
        val data = mockk<Parcel>(relaxed = true)
        val reply = mockk<Parcel>(relaxed = true)
        mockkObject(PluginHostTrust)
        mockkStatic(Binder::class)
        every { Binder.getCallingUid() } returns 100
        every { PluginHostTrust.isTrustedCaller(any(), 100) } returns true
        every { data.readString() } returnsMany listOf("plugin", "track", null, "Track", "Artist")
        every { data.readInt() } returnsMany listOf(1, 24, 192_000)
        every { data.readLong() } returns 1L

        assertTrue(
            (plugin.onBind(null) as IBitperfectPluginV1.Stub).onTransact(
                IBinder.FIRST_CALL_TRANSACTION + 3, data, reply, 0,
            ),
        )
        assertTrue(plugin.resolved)
        verify { reply.writeString(plugin.descriptor.url) }
    }

    @Test
    fun `bind intent extras cannot replace a registered callback`() {
        val plugin = SourceProbe()
        val trusted = HostCallbackProbe()
        val attacker = HostCallbackProbe()
        val extras = mockk<Bundle>()
        val intent = mockk<android.content.Intent>()
        every { intent.extras } returns extras
        every { extras.getBinder(any()) } returns attacker.asBinder()

        (plugin.onBind(null) as IBitperfectPluginV1.Stub).registerHostCallback(trusted)
        plugin.onBind(intent)

        assertTrue(plugin.callback() === trusted)
    }

    @Test
    fun `untrusted caller cannot change a registered callback`() {
        val plugin = SourceProbe()
        val trusted = HostCallbackProbe()
        val binder = plugin.onBind(null) as IBitperfectPluginV1.Stub
        binder.registerHostCallback(trusted)
        mockkObject(PluginHostTrust)
        mockkStatic(Binder::class)
        every { Binder.getCallingUid() } returns 999
        every { PluginHostTrust.isTrustedCaller(any(), 999) } returns false

        assertThrows(SecurityException::class.java) {
            binder.onTransact(IBinder.FIRST_CALL_TRANSACTION + 11, mockk(relaxed = true), mockk(relaxed = true), 0)
        }
        assertThrows(SecurityException::class.java) {
            binder.onTransact(IBinder.FIRST_CALL_TRANSACTION + 12, mockk(relaxed = true), mockk(relaxed = true), 0)
        }

        assertSame(trusted, plugin.callback())
    }

    @Test
    fun `new callback survives older owner cleanup`() {
        val plugin = SourceProbe()
        val first = HostCallbackProbe()
        val second = HostCallbackProbe()
        val binder = plugin.onBind(null) as IBitperfectPluginV1.Stub

        binder.registerHostCallback(first)
        binder.registerHostCallback(second)
        binder.unregisterHostCallback(first)
        plugin.postEvent("connected")

        assertTrue(first.events.isEmpty())
        assertTrue(plugin.getCredential("access") == "credential")
        assertTrue(second.events == listOf("connected"))
    }

    @Test
    fun `renderer callback reaches its registered owner`() {
        val plugin = RendererProbe()
        val callback = HostCallbackProbe()
        val binder = plugin.onBind(null) as IBitperfectPluginV1.Stub

        binder.registerHostCallback(callback)
        plugin.postEvent("renderer-connected")

        assertTrue(plugin.getCredential("access") == "credential")
        assertTrue(callback.events == listOf("renderer-connected"))
    }

    @Test
    fun `dead older callback cannot clear a newer registration`() {
        val callbacks = PluginHostCallbackRegistry()
        val first = mockk<IPluginHostCallbackV1>()
        val second = mockk<IPluginHostCallbackV1>()
        val firstBinder = mockk<IBinder>(relaxed = true)
        val secondBinder = mockk<IBinder>(relaxed = true)
        val death = io.mockk.slot<IBinder.DeathRecipient>()
        every { first.asBinder() } returns firstBinder
        every { second.asBinder() } returns secondBinder
        every { firstBinder.linkToDeath(capture(death), 0) } returns Unit

        callbacks.register(first)
        callbacks.register(second)
        death.captured.binderDied()

        assertSame(second, callbacks.callback)
    }

    private fun trustContext() {
        mockkObject(SignatureTrust)
        every { context.packageManager } returns packageManager
        every { context.packageName } returns "com.example.plugin"
    }

    private class SourceProbe : BitperfectSourcePlugin() {
        var resolved = false
        val descriptor = StreamDescriptorDto("https://example.test/audio", emptyMap(), false, null, null, "flac", 24, 192_000, 0)

        override fun onResolveStream(track: TrackRefDto, maxBitDepth: Int, maxSampleRate: Int): StreamDescriptorDto {
            resolved = true
            return descriptor
        }

        fun callback(): IPluginHostCallbackV1? = hostCallback

        fun hasCallback(): Boolean = hostCallback != null

        fun postEvent(event: String) {
            hostCallback?.postEvent(event)
        }

        fun getCredential(key: String): String? = hostCallback?.getCredential(key)
    }

    private class HostCallbackProbe : IPluginHostCallbackV1.Stub() {
        val events = mutableListOf<String>()

        override fun getCredential(key: String): String? = "credential"

        override fun putCredential(key: String, value: String) = Unit

        override fun clearCredentials() = Unit

        override fun getRecentPlayback(limit: Int): MutableList<PlaybackTrackDto> = mutableListOf()

        override fun requestPlayback(tracks: MutableList<PlaybackTrackDto>, startIndex: Int): Boolean = false

        override fun extendPlayback(tracks: MutableList<PlaybackTrackDto>, anchorTrackId: String, mode: Int): Boolean = false

        override fun getLibraryPlaylists(): MutableList<LibraryPlaylistDto> = mutableListOf()

        override fun addToLibraryPlaylist(
            playlistId: String,
            newTitle: String,
            tracks: MutableList<PlaybackTrackDto>,
        ): String? = null

        override fun postEvent(eventJson: String) {
            events += eventJson
        }

        override fun registerNetworkService(
            serviceType: String,
            serviceName: String,
            paths: MutableList<String>,
            txtRecords: MutableMap<String, String>,
            handler: IPluginHttpHandlerV1,
        ): Int {
            events += "registerNetworkService:$serviceType:$serviceName"
            return 0
        }

        override fun unregisterNetworkService(registrationId: Int) {
            events += "unregisterNetworkService:$registrationId"
        }

        override fun getRemainingQueueTracks(): Int = 0
    }

    private class RendererProbe : BitperfectRendererPlugin() {
        var enumerated = false

        override fun onListRenderers(): List<RendererInfoDto> {
            enumerated = true
            return emptyList()
        }

        override fun onPlayOnRenderer(rendererId: String, track: TrackRefDto, hostMediaUrl: String) = false

        override fun onEnqueueNextOnRenderer(rendererId: String, track: TrackRefDto, hostMediaUrl: String) = false

        fun postEvent(event: String) {
            hostCallback?.postEvent(event)
        }

        fun getCredential(key: String): String? = hostCallback?.getCredential(key)
    }
}

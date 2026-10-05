package com.bitperfect.player.sdk.plugin.runtime

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import com.bitperfect.player.sdk.plugin.IBitperfectPluginV1
import com.bitperfect.player.sdk.plugin.IPluginHostCallbackV1
import com.bitperfect.player.sdk.plugin.dto.ActionResultDto
import com.bitperfect.player.sdk.plugin.dto.AlbumDto
import com.bitperfect.player.sdk.plugin.dto.BrowseResultDto
import com.bitperfect.player.sdk.plugin.dto.DspSessionDescriptorDto
import com.bitperfect.player.sdk.plugin.dto.DspSessionOfferDto
import com.bitperfect.player.sdk.plugin.dto.PageDescriptorDto
import com.bitperfect.player.sdk.plugin.dto.RendererInfoDto
import com.bitperfect.player.sdk.plugin.dto.SearchResultDto
import com.bitperfect.player.sdk.plugin.dto.StreamDescriptorDto
import com.bitperfect.player.sdk.plugin.dto.TrackRefDto

abstract class BitperfectDspPlugin : Service() {
    private val callbacks = PluginHostCallbackRegistry { onHostCallbackChanged(it) }

    protected val hostCallback: IPluginHostCallbackV1?
        get() = callbacks.callback

    open fun onHostCallbackChanged(connected: Boolean) = Unit

    abstract fun onOpenDspSession(offer: DspSessionOfferDto): DspSessionDescriptorDto?
    open fun onRenderPage(routeId: String, paramsJson: String?, pageToken: String?): PageDescriptorDto? = null

    open fun onDispatchAction(actionId: String, paramsJson: String?): ActionResultDto? = null

    open fun capabilities(): Int = PluginSdk.CAPABILITY_DSP

    private val binder = object : IBitperfectPluginV1.Stub() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code != IBinder.INTERFACE_TRANSACTION &&
                !PluginHostTrust.isTrustedCaller(this@BitperfectDspPlugin, Binder.getCallingUid())
            ) {
                throw SecurityException("Only the Bitperfect host may call this plugin")
            }
            return super.onTransact(code, data, reply, flags)
        }

        override fun getApiVersion(): Int = PluginSdk.API_VERSION
        override fun getCapabilities(): Int = capabilities()

        override fun resolveStream(
            track: TrackRefDto,
            maxBitDepth: Int,
            maxSampleRate: Int,
        ): StreamDescriptorDto? = throw UnsupportedOperationException("Not a SOURCE plugin")

        override fun browse(folderId: String?, pageToken: String?): BrowseResultDto? =
            throw UnsupportedOperationException("Not a SOURCE plugin")

        override fun search(query: String, pageToken: String?): SearchResultDto? =
            throw UnsupportedOperationException("Not a SOURCE plugin")

        override fun getAlbum(albumId: String): AlbumDto? =
            throw UnsupportedOperationException("Not a SOURCE plugin")

        override fun parseUrl(url: String): TrackRefDto? =
            throw UnsupportedOperationException("Not a SOURCE plugin")

        override fun listRenderers(): List<RendererInfoDto> =
            throw UnsupportedOperationException("Not an OUTPUT_RENDERER plugin")

        override fun playOnRenderer(rendererId: String, track: TrackRefDto, hostMediaUrl: String): Boolean =
            throw UnsupportedOperationException("Not an OUTPUT_RENDERER plugin")

        override fun enqueueNextOnRenderer(
            rendererId: String,
            track: TrackRefDto,
            hostMediaUrl: String,
        ): Boolean = throw UnsupportedOperationException("Not an OUTPUT_RENDERER plugin")

        override fun registerHostCallback(callback: IPluginHostCallbackV1) = callbacks.register(callback)

        override fun unregisterHostCallback(callback: IPluginHostCallbackV1) = callbacks.clear(callback)

        override fun renderPage(
            routeId: String,
            paramsJson: String?,
            pageToken: String?,
        ): PageDescriptorDto? {
            val descriptor = onRenderPage(routeId, paramsJson, pageToken) ?: return null
            val raw = descriptor.rawJson
            return if (raw != null && descriptor.contractVersion == descriptor.baseVersion &&
                raw.length > PageDescriptorDto.INLINE_THRESHOLD_BYTES
            ) {
                PageDescriptorDto.pack(raw, descriptor.baseVersion)
            } else {
                descriptor
            }
        }

        override fun dispatchAction(actionId: String, paramsJson: String?): ActionResultDto? =
            onDispatchAction(actionId, paramsJson)

        override fun openDspSession(offer: DspSessionOfferDto?): DspSessionDescriptorDto? =
            offer?.let(::onOpenDspSession)
        override fun openReceiverSession(controller: com.bitperfect.player.sdk.plugin.IHostReceiverControllerV1?): com.bitperfect.player.sdk.plugin.IPluginReceiverSessionV1? = null
        override fun pauseRenderer(rendererId: String?): Boolean = false
        override fun resumeRenderer(rendererId: String?): Boolean = false
        override fun seekRenderer(rendererId: String?, positionMs: Long): Boolean = false
        override fun setRendererVolume(rendererId: String?, volumeFraction: Float): Boolean = false
        override fun stopRenderer(rendererId: String?) = Unit
    }

    final override fun onBind(intent: Intent?): IBinder = binder
}

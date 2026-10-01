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
import com.bitperfect.player.sdk.plugin.dto.PageDescriptorDto
import com.bitperfect.player.sdk.plugin.dto.RendererInfoDto
import com.bitperfect.player.sdk.plugin.dto.SearchResultDto
import com.bitperfect.player.sdk.plugin.dto.StreamDescriptorDto
import com.bitperfect.player.sdk.plugin.dto.DspSessionDescriptorDto
import com.bitperfect.player.sdk.plugin.dto.DspSessionOfferDto
import com.bitperfect.player.sdk.plugin.dto.TrackRefDto

/** Base Service for SOURCE plugins: subclasses implement stream resolution and browsing. */
abstract class BitperfectSourcePlugin : Service() {

    private val callbacks = PluginHostCallbackRegistry { onHostCallbackChanged(it) }

    protected val hostCallback: IPluginHostCallbackV1?
        get() = callbacks.callback

    /** The host callback was registered or lost. Default does nothing. */
    open fun onHostCallbackChanged(connected: Boolean) = Unit

    abstract fun onResolveStream(track: TrackRefDto, maxBitDepth: Int, maxSampleRate: Int): StreamDescriptorDto?

    open fun onBrowse(folderId: String?, pageToken: String?): BrowseResultDto? = null

    open fun onSearch(query: String, pageToken: String?): SearchResultDto? = null

    open fun onGetAlbum(albumId: String): AlbumDto? = null

    open fun onParseUrl(url: String): TrackRefDto? = null

    open fun onRenderPage(routeId: String, paramsJson: String?, pageToken: String?): PageDescriptorDto? = null

    open fun onDispatchAction(actionId: String, paramsJson: String?): ActionResultDto? = null
    open fun onListRenderers(): List<RendererInfoDto> = emptyList()
    open fun onPlayOnRenderer(rendererId: String, track: TrackRefDto, hostMediaUrl: String): Boolean = false
    open fun onEnqueueNextOnRenderer(rendererId: String, track: TrackRefDto, hostMediaUrl: String): Boolean = false

    open fun onOpenReceiverSession(controller: com.bitperfect.player.sdk.plugin.IHostReceiverControllerV1): com.bitperfect.player.sdk.plugin.IPluginReceiverSessionV1? = null
    open fun onPauseRenderer(rendererId: String): Boolean = false
    open fun onResumeRenderer(rendererId: String): Boolean = false
    open fun onSeekRenderer(rendererId: String, positionMs: Long): Boolean = false
    open fun onSetRendererVolume(rendererId: String, volumeFraction: Float): Boolean = false
    open fun onStopRenderer(rendererId: String) = Unit


    open fun apiVersion(): Int = PluginSdk.API_VERSION
    open fun capabilities(): Int = PluginSdk.CAPABILITY_SOURCE

    private val binder = object : IBitperfectPluginV1.Stub() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code != IBinder.INTERFACE_TRANSACTION &&
                !PluginHostTrust.isTrustedCaller(this@BitperfectSourcePlugin, Binder.getCallingUid())
            ) {
                throw SecurityException("Only the Bitperfect host may call this plugin")
            }
            return super.onTransact(code, data, reply, flags)
        }

        override fun getApiVersion(): Int = apiVersion()
        override fun getCapabilities(): Int = capabilities()
        override fun resolveStream(track: TrackRefDto, maxBitDepth: Int, maxSampleRate: Int): StreamDescriptorDto? =
            onResolveStream(track, maxBitDepth, maxSampleRate)
        override fun browse(folderId: String?, pageToken: String?): BrowseResultDto? = onBrowse(folderId, pageToken)
        override fun search(query: String, pageToken: String?): SearchResultDto? = onSearch(query, pageToken)
        override fun getAlbum(albumId: String): AlbumDto? = onGetAlbum(albumId)
        override fun parseUrl(url: String): TrackRefDto? = onParseUrl(url)
        override fun listRenderers(): List<RendererInfoDto> = onListRenderers()
        override fun playOnRenderer(rendererId: String, track: TrackRefDto, hostMediaUrl: String): Boolean =
            onPlayOnRenderer(rendererId, track, hostMediaUrl)
        override fun enqueueNextOnRenderer(rendererId: String, track: TrackRefDto, hostMediaUrl: String): Boolean =
            onEnqueueNextOnRenderer(rendererId, track, hostMediaUrl)
        override fun registerHostCallback(callback: IPluginHostCallbackV1) = callbacks.register(callback)

        override fun unregisterHostCallback(callback: IPluginHostCallbackV1) = callbacks.clear(callback)

        override fun renderPage(routeId: String, paramsJson: String?, pageToken: String?): PageDescriptorDto? =
            onRenderPage(routeId, paramsJson, pageToken)
        override fun dispatchAction(actionId: String, paramsJson: String?): ActionResultDto? =
            onDispatchAction(actionId, paramsJson)
        override fun openDspSession(offer: DspSessionOfferDto?): DspSessionDescriptorDto? = null
        override fun openReceiverSession(controller: com.bitperfect.player.sdk.plugin.IHostReceiverControllerV1?): com.bitperfect.player.sdk.plugin.IPluginReceiverSessionV1? =
            controller?.let { onOpenReceiverSession(it) }
        override fun pauseRenderer(rendererId: String?): Boolean =
            rendererId?.let { onPauseRenderer(it) } ?: false
        override fun resumeRenderer(rendererId: String?): Boolean =
            rendererId?.let { onResumeRenderer(it) } ?: false
        override fun seekRenderer(rendererId: String?, positionMs: Long): Boolean =
            rendererId?.let { onSeekRenderer(it, positionMs) } ?: false
        override fun setRendererVolume(rendererId: String?, volumeFraction: Float): Boolean =
            rendererId?.let { onSetRendererVolume(it, volumeFraction) } ?: false
        override fun stopRenderer(rendererId: String?) {
            rendererId?.let { onStopRenderer(it) }
        }
    }

    final override fun onBind(intent: Intent?): IBinder = binder
}

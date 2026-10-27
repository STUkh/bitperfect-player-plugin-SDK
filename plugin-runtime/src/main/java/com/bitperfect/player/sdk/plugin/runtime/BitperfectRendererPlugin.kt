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

/** Base Service for OUTPUT_RENDERER plugins: subclasses drive external renderers by URL, never PCM. */
abstract class BitperfectRendererPlugin : Service() {

    private val callbacks = PluginHostCallbackRegistry()

    protected val hostCallback: IPluginHostCallbackV1?
        get() = callbacks.callback

    abstract fun onListRenderers(): List<RendererInfoDto>

    abstract fun onPlayOnRenderer(rendererId: String, track: TrackRefDto, hostMediaUrl: String): Boolean

    abstract fun onEnqueueNextOnRenderer(rendererId: String, track: TrackRefDto, hostMediaUrl: String): Boolean

    open fun onResolveStream(track: TrackRefDto, maxBitDepth: Int, maxSampleRate: Int): StreamDescriptorDto? =
        throw UnsupportedOperationException("Not a SOURCE plugin")

    open fun onPrepareNextStream(track: TrackRefDto, maxBitDepth: Int, maxSampleRate: Int): StreamDescriptorDto? =
        throw UnsupportedOperationException("Not a SOURCE plugin")

    open fun onBrowse(folderId: String?, pageToken: String?): BrowseResultDto? =
        throw UnsupportedOperationException("Not a SOURCE plugin")

    open fun onSearch(query: String, pageToken: String?): SearchResultDto? =
        throw UnsupportedOperationException("Not a SOURCE plugin")

    open fun onGetAlbum(albumId: String): AlbumDto? =
        throw UnsupportedOperationException("Not a SOURCE plugin")

    open fun onParseUrl(url: String): TrackRefDto? =
        throw UnsupportedOperationException("Not a SOURCE plugin")

    open fun onPauseRenderer(rendererId: String): Boolean = false
    open fun onResumeRenderer(rendererId: String): Boolean = false
    open fun onSeekRenderer(rendererId: String, positionMs: Long): Boolean = false
    open fun onSetRendererVolume(rendererId: String, volumeFraction: Float): Boolean = false
    open fun onStopRenderer(rendererId: String) = Unit
    open fun capabilities(): Int = PluginSdk.CAPABILITY_OUTPUT_RENDERER

    private val binder = object : IBitperfectPluginV1.Stub() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code != IBinder.INTERFACE_TRANSACTION &&
                !PluginHostTrust.isTrustedCaller(this@BitperfectRendererPlugin, Binder.getCallingUid())
            ) {
                throw SecurityException("Only the Bitperfect host may call this plugin")
            }
            return super.onTransact(code, data, reply, flags)
        }

        override fun getApiVersion(): Int = PluginSdk.API_VERSION
        override fun getCapabilities(): Int = capabilities()
        override fun resolveStream(track: TrackRefDto, maxBitDepth: Int, maxSampleRate: Int): StreamDescriptorDto? =
            onResolveStream(track, maxBitDepth, maxSampleRate)
        override fun prepareNextStream(track: TrackRefDto, maxBitDepth: Int, maxSampleRate: Int): StreamDescriptorDto? =
            onPrepareNextStream(track, maxBitDepth, maxSampleRate)
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

        // Renderer plugins never declare PAGES; null is the contract's "no page".
        override fun renderPage(routeId: String, paramsJson: String?, pageToken: String?): PageDescriptorDto? = null
        override fun dispatchAction(actionId: String, paramsJson: String?): ActionResultDto? = null
        override fun openDspSession(offer: DspSessionOfferDto?): DspSessionDescriptorDto? = null
        override fun openReceiverSession(controller: com.bitperfect.player.sdk.plugin.IHostReceiverControllerV1?): com.bitperfect.player.sdk.plugin.IPluginReceiverSessionV1? = null
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

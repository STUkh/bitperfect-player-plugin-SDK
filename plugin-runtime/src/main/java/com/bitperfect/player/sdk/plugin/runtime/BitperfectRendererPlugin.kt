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
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

/** Base Service for OUTPUT_RENDERER plugins: subclasses drive external renderers by URL, never PCM. */
abstract class BitperfectRendererPlugin : Service() {

    private val callbacks = PluginHostCallbackRegistry { onHostCallbackChanged(it) }

    protected val hostCallback: IPluginHostCallbackV1?
        get() = callbacks.callback

    open fun onHostCallbackChanged(connected: Boolean) = Unit

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
    open fun apiVersion(): Int = 7
    open fun capabilities(): Int = PluginSdk.CAPABILITY_OUTPUT_RENDERER

    open fun rendererTimeoutMs(): Long = DEFAULT_RENDERER_TIMEOUT_MS
    open fun rendererControlTimeoutMs(): Long = DEFAULT_RENDERER_CONTROL_TIMEOUT_MS

    @Volatile
    private var workerExecutor: ExecutorService? = null

    @Volatile
    private var isDestroyed = false

    private val threadNumber = AtomicInteger(1)

    private fun getOrCreateExecutor(): ExecutorService {
        if (isDestroyed) {
            throw RejectedExecutionException("Plugin service is destroyed")
        }
        val current = workerExecutor
        if (current != null && !current.isShutdown) return current
        return synchronized(this) {
            if (isDestroyed) {
                throw RejectedExecutionException("Plugin service is destroyed")
            }
            val existing = workerExecutor
            if (existing != null && !existing.isShutdown) {
                existing
            } else {
                ThreadPoolExecutor(
                    0,
                    8,
                    60L,
                    TimeUnit.SECONDS,
                    SynchronousQueue<Runnable>(),
                    ThreadFactory { runnable ->
                        Thread(runnable, "BitperfectRendererPlugin-worker-${threadNumber.getAndIncrement()}").apply {
                            isDaemon = true
                        }
                    },
                    ThreadPoolExecutor.AbortPolicy(),
                ).also { workerExecutor = it }
            }
        }
    }

    private fun <T> executeSafeguarded(timeoutMs: Long, label: String, block: () -> T?): T? {
        if (isDestroyed) return null
        if (timeoutMs <= 0L) {
            return try {
                block()
            } catch (_: Throwable) {
                null
            }
        }
        val future = try {
            getOrCreateExecutor().submit(Callable { block() })
        } catch (_: RejectedExecutionException) {
            return null
        } catch (_: Throwable) {
            return null
        }
        return try {
            future.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            future.cancel(true)
            null
        } catch (e: ExecutionException) {
            null
        } catch (e: InterruptedException) {
            future.cancel(true)
            Thread.currentThread().interrupt()
            null
        } catch (_: Throwable) {
            null
        }
    }

    override fun onDestroy() {
        isDestroyed = true
        try {
            workerExecutor?.let { executor ->
                executor.shutdownNow()
                executor.awaitTermination(500, TimeUnit.MILLISECONDS)
            }
        } catch (_: Throwable) {
        } finally {
            workerExecutor = null
        }
        super.onDestroy()
    }

    private val binder = object : IBitperfectPluginV1.Stub() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code != IBinder.INTERFACE_TRANSACTION &&
                !PluginHostTrust.isTrustedCaller(this@BitperfectRendererPlugin, Binder.getCallingUid())
            ) {
                throw SecurityException("Only the Bitperfect host may call this plugin")
            }
            return super.onTransact(code, data, reply, flags)
        }

        override fun getApiVersion(): Int = apiVersion()
        override fun getCapabilities(): Int = capabilities()
        override fun resolveStream(track: TrackRefDto, maxBitDepth: Int, maxSampleRate: Int): StreamDescriptorDto? =
            onResolveStream(track, maxBitDepth, maxSampleRate)
        override fun prepareNextStream(track: TrackRefDto, maxBitDepth: Int, maxSampleRate: Int): StreamDescriptorDto? =
            onPrepareNextStream(track, maxBitDepth, maxSampleRate)
        override fun browse(folderId: String?, pageToken: String?): BrowseResultDto? = onBrowse(folderId, pageToken)
        override fun search(query: String, pageToken: String?): SearchResultDto? = onSearch(query, pageToken)
        override fun getAlbum(albumId: String): AlbumDto? = onGetAlbum(albumId)
        override fun parseUrl(url: String): TrackRefDto? = onParseUrl(url)
        override fun listRenderers(): List<RendererInfoDto> =
            executeSafeguarded(rendererTimeoutMs(), "listRenderers") { onListRenderers() } ?: emptyList()
        override fun playOnRenderer(rendererId: String, track: TrackRefDto, hostMediaUrl: String): Boolean =
            executeSafeguarded(rendererTimeoutMs(), "playOnRenderer") { onPlayOnRenderer(rendererId, track, hostMediaUrl) } ?: false
        override fun enqueueNextOnRenderer(rendererId: String, track: TrackRefDto, hostMediaUrl: String): Boolean =
            executeSafeguarded(rendererTimeoutMs(), "enqueueNextOnRenderer") { onEnqueueNextOnRenderer(rendererId, track, hostMediaUrl) } ?: false
        override fun registerHostCallback(callback: IPluginHostCallbackV1) = callbacks.register(callback)

        override fun unregisterHostCallback(callback: IPluginHostCallbackV1) = callbacks.clear(callback)

        // Renderer plugins never declare PAGES; null is the contract's "no page".
        override fun renderPage(routeId: String, paramsJson: String?, pageToken: String?): PageDescriptorDto? = null
        override fun dispatchAction(actionId: String, paramsJson: String?): ActionResultDto? = null
        override fun openDspSession(offer: DspSessionOfferDto?): DspSessionDescriptorDto? = null
        override fun openReceiverSession(controller: com.bitperfect.player.sdk.plugin.IHostReceiverControllerV1?): com.bitperfect.player.sdk.plugin.IPluginReceiverSessionV1? = null
        override fun pauseRenderer(rendererId: String?): Boolean =
            rendererId?.let { id -> executeSafeguarded(rendererControlTimeoutMs(), "pauseRenderer") { onPauseRenderer(id) } } ?: false
        override fun resumeRenderer(rendererId: String?): Boolean =
            rendererId?.let { id -> executeSafeguarded(rendererControlTimeoutMs(), "resumeRenderer") { onResumeRenderer(id) } } ?: false
        override fun seekRenderer(rendererId: String?, positionMs: Long): Boolean =
            rendererId?.let { id -> executeSafeguarded(rendererControlTimeoutMs(), "seekRenderer") { onSeekRenderer(id, positionMs) } } ?: false
        override fun setRendererVolume(rendererId: String?, volumeFraction: Float): Boolean =
            rendererId?.let { id -> executeSafeguarded(rendererControlTimeoutMs(), "setRendererVolume") { onSetRendererVolume(id, volumeFraction) } } ?: false
        override fun cancelRequest(requestId: String?) = Unit
        override fun stopRenderer(rendererId: String?) {
            rendererId?.let { id -> executeSafeguarded(rendererControlTimeoutMs(), "stopRenderer") { onStopRenderer(id) } }
        }
    }
    final override fun onBind(intent: Intent?): IBinder = binder

    companion object {
        const val DEFAULT_RENDERER_TIMEOUT_MS = 10_000L
        const val DEFAULT_RENDERER_CONTROL_TIMEOUT_MS = 5_000L
    }
}

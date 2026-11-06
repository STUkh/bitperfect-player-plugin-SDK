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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runInterruptible

/** Base Service for SOURCE plugins: subclasses implement stream resolution and browsing. */
abstract class BitperfectSourcePlugin : Service() {

    private val callbacks = PluginHostCallbackRegistry { onHostCallbackChanged(it) }

    protected val hostCallback: IPluginHostCallbackV1?
        get() = callbacks.callback

    /** The host callback was registered or lost. Default does nothing. */
    open fun onHostCallbackChanged(connected: Boolean) = Unit

    abstract fun onResolveStream(track: TrackRefDto, maxBitDepth: Int, maxSampleRate: Int): StreamDescriptorDto?

    open fun onPrepareNextStream(track: TrackRefDto, maxBitDepth: Int, maxSampleRate: Int): StreamDescriptorDto? =
        onResolveStream(track, maxBitDepth, maxSampleRate)
    open fun onBrowse(folderId: String?, pageToken: String?): BrowseResultDto? = null

    open fun onSearch(query: String, pageToken: String?): SearchResultDto? = null

    open fun onGetAlbum(albumId: String): AlbumDto? = null

    open fun onParseUrl(url: String): TrackRefDto? = null

    open fun onRenderPage(routeId: String, paramsJson: String?, pageToken: String?): PageDescriptorDto? = null

    /** Cancellable variant; the host cancels it via cancelRequest. Defaults to [onRenderPage]. */
    open suspend fun onRenderPageSuspend(routeId: String, paramsJson: String?, pageToken: String?): PageDescriptorDto? =
        runInterruptible { onRenderPage(routeId, paramsJson, pageToken) }

    /** Parsed variant: params without `_bp_*` keys. Defaults to [onRenderPageSuspend] with the raw JSON. */
    open suspend fun onRenderPageRequest(request: PageRequest): PageDescriptorDto? =
        onRenderPageSuspend(request.routeId, request.rawParamsJson, request.pageToken)

    /** Parsed variant of [onDispatchActionSuspend]. */
    open suspend fun onDispatchActionRequest(request: ActionRequest): ActionResultDto? =
        onDispatchActionSuspend(request.actionId, request.rawParamsJson)

    open fun onDispatchAction(actionId: String, paramsJson: String?): ActionResultDto? = null

    /** Cancellable variant of [onDispatchAction]. */
    open suspend fun onDispatchActionSuspend(actionId: String, paramsJson: String?): ActionResultDto? =
        runInterruptible { onDispatchAction(actionId, paramsJson) }
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

    /** Every deadline below reads from this policy; override it, not the individual getters. */
    open val callPolicy: PluginCallPolicy get() = PluginCallPolicy.Default

    open fun resolveTimeoutMs(): Long = callPolicy.resolveStreamMs
    open fun browseTimeoutMs(): Long = callPolicy.browseMs
    // Contract-fixed (PluginCallLimits); internal so only tests in this module can shorten them.
    internal open fun renderPageTimeoutMs(): Long = callPolicy.renderPageMs
    internal open fun dispatchActionTimeoutMs(): Long = callPolicy.dispatchActionMs
    open fun rendererTimeoutMs(): Long = callPolicy.rendererMs
    open fun rendererControlTimeoutMs(): Long = callPolicy.rendererControlMs

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val requests = PluginRequestRegistry(serviceScope)

    /** The only runBlocking on a Binder thread; [requestId] makes the call cancellable via cancelRequest. */
    protected fun <T> runCancellable(requestId: String?, timeoutMs: Long, block: suspend () -> T?): T? =
        if (isDestroyed) null else requests.runCancellable(requestId, timeoutMs, block)

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
                        Thread(runnable, "BitperfectSourcePlugin-worker-${threadNumber.getAndIncrement()}").apply {
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
        serviceScope.cancel()
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
                !PluginHostTrust.isTrustedCaller(this@BitperfectSourcePlugin, Binder.getCallingUid())
            ) {
                throw SecurityException("Only the Bitperfect host may call this plugin")
            }
            return super.onTransact(code, data, reply, flags)
        }

        override fun getApiVersion(): Int = apiVersion()
        override fun getCapabilities(): Int = capabilities()
        override fun resolveStream(track: TrackRefDto, maxBitDepth: Int, maxSampleRate: Int): StreamDescriptorDto? =
            executeSafeguarded(resolveTimeoutMs(), "resolveStream") {
                onResolveStream(track, maxBitDepth, maxSampleRate)
            }
        override fun prepareNextStream(track: TrackRefDto, maxBitDepth: Int, maxSampleRate: Int): StreamDescriptorDto? =
            executeSafeguarded(resolveTimeoutMs(), "prepareNextStream") {
                onPrepareNextStream(track, maxBitDepth, maxSampleRate)
            }
        override fun browse(folderId: String?, pageToken: String?): BrowseResultDto? =
            executeSafeguarded(browseTimeoutMs(), "browse") {
                onBrowse(folderId, pageToken)
            }
        override fun search(query: String, pageToken: String?): SearchResultDto? =
            executeSafeguarded(browseTimeoutMs(), "search") {
                onSearch(query, pageToken)
            }
        override fun getAlbum(albumId: String): AlbumDto? =
            executeSafeguarded(browseTimeoutMs(), "getAlbum") {
                onGetAlbum(albumId)
            }
        override fun parseUrl(url: String): TrackRefDto? =
            executeSafeguarded(browseTimeoutMs(), "parseUrl") { onParseUrl(url) }
        override fun listRenderers(): List<RendererInfoDto> =
            executeSafeguarded(rendererTimeoutMs(), "listRenderers") { onListRenderers() } ?: emptyList()
        override fun playOnRenderer(rendererId: String, track: TrackRefDto, hostMediaUrl: String): Boolean =
            executeSafeguarded(rendererTimeoutMs(), "playOnRenderer") { onPlayOnRenderer(rendererId, track, hostMediaUrl) } ?: false
        override fun enqueueNextOnRenderer(rendererId: String, track: TrackRefDto, hostMediaUrl: String): Boolean =
            executeSafeguarded(rendererTimeoutMs(), "enqueueNextOnRenderer") { onEnqueueNextOnRenderer(rendererId, track, hostMediaUrl) } ?: false
        override fun registerHostCallback(callback: IPluginHostCallbackV1) = callbacks.register(callback)

        override fun unregisterHostCallback(callback: IPluginHostCallbackV1) = callbacks.clear(callback)

        override fun renderPage(routeId: String, paramsJson: String?, pageToken: String?): PageDescriptorDto? {
            val callingUid = Binder.getCallingUid()
            val parsed = parseParams(paramsJson)
            val descriptor = runCancellable(parsed.requestId, renderPageTimeoutMs()) {
                onRenderPageRequest(PageRequest(routeId, parsed.params, pageToken, parsed.requestId, paramsJson))
            } ?: return null
            val supportsTiered = HostTransportNegotiator.isTieredTransportSupported(
                context = this@BitperfectSourcePlugin,
                paramsJson = paramsJson,
                callingUid = callingUid,
            )

            return if (supportsTiered) {
                val raw = descriptor.rawJson
                if (raw != null && descriptor.contractVersion == descriptor.baseVersion &&
                    raw.length > PageDescriptorDto.INLINE_THRESHOLD_BYTES
                ) {
                    PageDescriptorDto.pack(raw, descriptor.baseVersion)
                } else {
                    descriptor
                }
            } else {
                descriptor.toLegacy()
            }
        }
        override fun dispatchAction(actionId: String, paramsJson: String?): ActionResultDto? =
            parseParams(paramsJson).let { parsed ->
                runCancellable(parsed.requestId, dispatchActionTimeoutMs()) {
                    onDispatchActionRequest(ActionRequest(actionId, parsed.params, parsed.requestId, paramsJson))
                }
            }
        override fun cancelRequest(requestId: String?) {
            requestId?.let(requests::cancel)
        }
        override fun openDspSession(offer: DspSessionOfferDto?): DspSessionDescriptorDto? = null
        override fun openReceiverSession(controller: com.bitperfect.player.sdk.plugin.IHostReceiverControllerV1?): com.bitperfect.player.sdk.plugin.IPluginReceiverSessionV1? =
            controller?.let { onOpenReceiverSession(it) }
        override fun pauseRenderer(rendererId: String?): Boolean =
            rendererId?.let { id -> executeSafeguarded(rendererControlTimeoutMs(), "pauseRenderer") { onPauseRenderer(id) } } ?: false
        override fun resumeRenderer(rendererId: String?): Boolean =
            rendererId?.let { id -> executeSafeguarded(rendererControlTimeoutMs(), "resumeRenderer") { onResumeRenderer(id) } } ?: false
        override fun seekRenderer(rendererId: String?, positionMs: Long): Boolean =
            rendererId?.let { id -> executeSafeguarded(rendererControlTimeoutMs(), "seekRenderer") { onSeekRenderer(id, positionMs) } } ?: false
        override fun setRendererVolume(rendererId: String?, volumeFraction: Float): Boolean =
            rendererId?.let { id -> executeSafeguarded(rendererControlTimeoutMs(), "setRendererVolume") { onSetRendererVolume(id, volumeFraction) } } ?: false
        override fun stopRenderer(rendererId: String?) {
            rendererId?.let { id -> executeSafeguarded(rendererControlTimeoutMs(), "stopRenderer") { onStopRenderer(id) } }
        }
    }

    final override fun onBind(intent: Intent?): IBinder = binder
}

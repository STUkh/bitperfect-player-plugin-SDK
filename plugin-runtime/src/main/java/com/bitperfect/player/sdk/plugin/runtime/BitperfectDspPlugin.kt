package com.bitperfect.player.sdk.plugin.runtime

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import com.bitperfect.player.sdk.plugin.ApiLevel
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

abstract class BitperfectDspPlugin : Service() {
    private val callbacks = PluginHostCallbackRegistry { onHostCallbackChanged(it) }

    protected val hostCallback: IPluginHostCallbackV1?
        get() = callbacks.callback

    open fun onHostCallbackChanged(connected: Boolean) = Unit

    abstract fun onOpenDspSession(offer: DspSessionOfferDto): DspSessionDescriptorDto?

    // Stays below CANCEL_REQUEST: cancelRequest is a no-op here.
    open fun apiVersion(): Int = ApiLevel.RENDERER_DEFAULT
    open fun capabilities(): Int = PluginSdk.CAPABILITY_DSP

    open fun onRenderPage(routeId: String, paramsJson: String?, pageToken: String?): PageDescriptorDto? = null

    open fun onDispatchAction(actionId: String, paramsJson: String?): ActionResultDto? = null

    open fun dispatchActionTimeoutMs(): Long = DEFAULT_DISPATCH_TIMEOUT_MS
    open fun dspSessionTimeoutMs(): Long = DEFAULT_DSP_SESSION_TIMEOUT_MS

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
                        Thread(runnable, "BitperfectDspPlugin-worker-${threadNumber.getAndIncrement()}").apply {
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
                !PluginHostTrust.isTrustedCaller(this@BitperfectDspPlugin, Binder.getCallingUid())
            ) {
                throw SecurityException("Only the Bitperfect host may call this plugin")
            }
            return super.onTransact(code, data, reply, flags)
        }

        override fun getApiVersion(): Int = apiVersion()
        override fun getCapabilities(): Int = capabilities()

        override fun resolveStream(
            track: TrackRefDto,
            maxBitDepth: Int,
            maxSampleRate: Int,
        ): StreamDescriptorDto? = throw UnsupportedOperationException("Not a SOURCE plugin")

        override fun prepareNextStream(
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
            val callingUid = Binder.getCallingUid()
            val descriptor = onRenderPage(routeId, paramsJson, pageToken) ?: return null
            val supportsTiered = HostTransportNegotiator.isTieredTransportSupported(
                context = this@BitperfectDspPlugin,
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
            executeSafeguarded(dispatchActionTimeoutMs(), "dispatchAction") {
                onDispatchAction(actionId, paramsJson)
            }

        override fun openDspSession(offer: DspSessionOfferDto?): DspSessionDescriptorDto? =
            offer?.let { executeSafeguarded(dspSessionTimeoutMs(), "openDspSession") { onOpenDspSession(it) } }

        override fun openReceiverSession(controller: com.bitperfect.player.sdk.plugin.IHostReceiverControllerV1?): com.bitperfect.player.sdk.plugin.IPluginReceiverSessionV1? = null
        override fun pauseRenderer(rendererId: String?): Boolean = false
        override fun resumeRenderer(rendererId: String?): Boolean = false
        override fun seekRenderer(rendererId: String?, positionMs: Long): Boolean = false
        override fun setRendererVolume(rendererId: String?, volumeFraction: Float): Boolean = false
        override fun stopRenderer(rendererId: String?) = Unit
        override fun cancelRequest(requestId: String?) = Unit
    }

    final override fun onBind(intent: Intent?): IBinder = binder

    companion object {
        const val DEFAULT_DISPATCH_TIMEOUT_MS = 14_000L
        const val DEFAULT_DSP_SESSION_TIMEOUT_MS = 5_000L
    }
}

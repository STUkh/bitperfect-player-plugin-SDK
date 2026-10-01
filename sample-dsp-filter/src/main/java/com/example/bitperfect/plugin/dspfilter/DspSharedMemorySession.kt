package com.example.bitperfect.plugin.dspfilter

import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import com.bitperfect.player.sdk.plugin.IPluginDspSessionV1
import com.bitperfect.player.sdk.plugin.dto.DspSessionOfferDto
import com.bitperfect.player.sdk.plugin.dto.DspStageInfoDto
import java.io.Closeable
import java.io.FileDescriptor
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Handles zero-copy out-of-process real-time DSP audio processing via:
 * 1. Linux SharedMemory (Ashmem) ring buffers mapped into memory without copies.
 * 2. Linux eventfd file descriptors for microsecond-latency synchronization.
 * 3. Dedicated real-time audio thread executing within the 2.0 ms deadline.
 */
class DspSharedMemorySession(
    val offer: DspSessionOfferDto,
    private val stageInfo: DspStageInfoDto,
    private val filter: BiquadFilter,
    private val isBypassedProvider: () -> Boolean,
    private val onClosed: (() -> Unit)? = null,
) : IPluginDspSessionV1.Stub(), Closeable {

    companion object {
        const val HEADER_SIZE_BYTES = 64L
        const val OFFSET_MAGIC = 0
        const val OFFSET_VERSION = 4
        const val OFFSET_EPOCH = 8
        const val OFFSET_WRITE_POS = 16
        const val OFFSET_READ_POS = 24
        const val OFFSET_FLAGS = 32
        const val OFFSET_XRUNS = 36

        const val DSP_SHARED_MAGIC = 0x42504453 // "BPDS"
        const val DSP_SHARED_VERSION = 1
        const val FLAG_ACTIVE = 1
        const val FLAG_BYPASS = 2
        const val FLAG_ERROR = 4
    }

    private val isClosed = AtomicBoolean(false)
    private var workerThread: Thread? = null

    // Mapped memory buffers (both READ_WRITE to update read_pos / write_pos headers)
    private var inChannel: FileChannel? = null
    private var outChannel: FileChannel? = null
    private var inMappedBuffer: MappedByteBuffer? = null
    private var outMappedBuffer: MappedByteBuffer? = null

    // Duplicated file descriptors for safe lifetime ownership
    private var dupShmemIn: ParcelFileDescriptor? = null
    private var dupShmemOut: ParcelFileDescriptor? = null
    private var dupNotifyIn: ParcelFileDescriptor? = null
    private var dupNotifyOut: ParcelFileDescriptor? = null

    // Pre-allocated direct buffer for eventfd read/write to avoid allocations per tick
    private val eventFdBuffer = ByteBuffer.allocateDirect(8).order(ByteOrder.nativeOrder())

    // Real-time scratch arrays to avoid allocations in processing loop
    private val scratchIn = FloatArray(offer.chunkFrames * offer.format.channels)
    private val scratchOut = FloatArray(offer.chunkFrames * offer.format.channels)
    init {
        initializeTransport()
    }

    private fun initializeTransport() {
        try {
            // Duplicate descriptors so plugin owns their lifecycle safely
            dupShmemIn = offer.shmemInFd?.let { ParcelFileDescriptor.dup(it.fileDescriptor) }
            dupShmemOut = offer.shmemOutFd?.let { ParcelFileDescriptor.dup(it.fileDescriptor) }
            dupNotifyIn = offer.notifyInFd?.let { ParcelFileDescriptor.dup(it.fileDescriptor) }
            dupNotifyOut = offer.notifyOutFd?.let { ParcelFileDescriptor.dup(it.fileDescriptor) }

            val channels = offer.format.channels
            val bytesPerFrame = channels * 4 // Float32 is 4 bytes
            val totalRegionSizeBytes = HEADER_SIZE_BYTES + (offer.capacityFrames * bytesPerFrame).toLong()

            // Both mapped READ_WRITE: plugin updates read_pos in input header and write_pos in output header
            if (dupShmemIn != null && totalRegionSizeBytes > HEADER_SIZE_BYTES) {
                inChannel = ParcelFileDescriptor.AutoCloseInputStream(dupShmemIn).channel
                inMappedBuffer = inChannel!!.map(
                    FileChannel.MapMode.READ_WRITE,
                    0,
                    totalRegionSizeBytes
                ).also {
                    it.order(ByteOrder.nativeOrder())
                }
            }

            if (dupShmemOut != null && totalRegionSizeBytes > HEADER_SIZE_BYTES) {
                outChannel = ParcelFileDescriptor.AutoCloseOutputStream(dupShmemOut).channel
                outMappedBuffer = outChannel!!.map(
                    FileChannel.MapMode.READ_WRITE,
                    0,
                    totalRegionSizeBytes
                ).also {
                    it.order(ByteOrder.nativeOrder())
                }
            }
        } catch (_: Exception) {
            // In unit test environments without real ashmem fds, fallback gracefully
        }
    }

    fun startWorker() {
        if (dupNotifyIn == null || dupNotifyOut == null) return
        if (workerThread != null) return

        workerThread = Thread({
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
            runRealTimeLoop()
        }, "BitperfectDspWorker-${offer.sessionId}").apply {
            isDaemon = true
            start()
        }
    }

    private fun runRealTimeLoop() {
        val notifyInFd = dupNotifyIn?.fileDescriptor ?: return
        val notifyOutFd = dupNotifyOut?.fileDescriptor ?: return

        val pollFd = StructPollfd().apply {
            fd = notifyInFd
            events = OsConstants.POLLIN.toShort()
        }
        val pollFds = arrayOf(pollFd)

        val chunkFrames = offer.chunkFrames
        val capacityFrames = offer.capacityFrames
        val channels = offer.format.channels
        val samplesPerChunk = chunkFrames * channels
        val errorMask = (OsConstants.POLLERR or OsConstants.POLLHUP or OsConstants.POLLNVAL).toShort()

        while (!isClosed.get()) {
            try {
                // Wait for host eventfd notification with 2.0 ms timeout deadline
                val pollResult = Os.poll(pollFds, 2)
                if (pollResult > 0) {
                    val revents = pollFd.revents.toInt()
                    if ((revents and errorMask.toInt()) != 0) {
                        // Hangup or socket error - terminate gracefully
                        break
                    }
                    if ((revents and OsConstants.POLLIN) != 0) {
                        // Drain eventfd (read 8-byte uint64 counter)
                        drainEventFd(notifyInFd)

                        val inBuf = inMappedBuffer
                        val outBuf = outMappedBuffer
                        if (inBuf != null && outBuf != null) {
                            val inReadPos = inBuf.getLong(OFFSET_READ_POS)
                            val inWritePos = inBuf.getLong(OFFSET_WRITE_POS)
                            val availableFrames = (inWritePos - inReadPos).toInt()
                            val framesToProcess = chunkFrames.coerceAtMost(availableFrames.coerceAtLeast(0))

                            if (framesToProcess > 0) {
                                val readFrameOffset = (inReadPos % capacityFrames).toInt()
                                val firstFrames = framesToProcess.coerceAtMost(capacityFrames - readFrameOffset)
                                val firstSamples = firstFrames * channels
                                val firstByteOffset = (HEADER_SIZE_BYTES + readFrameOffset.toLong() * channels * 4).toInt()

                                inBuf.position(firstByteOffset)
                                inBuf.asFloatBuffer().get(scratchIn, 0, firstSamples)

                                val remainingSamples = framesToProcess * channels - firstSamples
                                if (remainingSamples > 0) {
                                    inBuf.position(HEADER_SIZE_BYTES.toInt())
                                    inBuf.asFloatBuffer().get(scratchIn, firstSamples, remainingSamples)
                                }

                                // Advance read_pos in input header
                                inBuf.putLong(OFFSET_READ_POS, inReadPos + framesToProcess)

                                // Apply biquad filtering
                                filter.process(
                                    input = scratchIn,
                                    output = scratchOut,
                                     frames = framesToProcess,
                                    bypassed = isBypassedProvider(),
                                )

                                val outWritePos = outBuf.getLong(OFFSET_WRITE_POS)
                                val writeFrameOffset = (outWritePos % capacityFrames).toInt()
                                val firstOutFrames = framesToProcess.coerceAtMost(capacityFrames - writeFrameOffset)
                                val firstOutSamples = firstOutFrames * channels
                                val firstOutByteOffset = (HEADER_SIZE_BYTES + writeFrameOffset.toLong() * channels * 4).toInt()

                                outBuf.position(firstOutByteOffset)
                                outBuf.asFloatBuffer().put(scratchOut, 0, firstOutSamples)

                                val remainingOutSamples = framesToProcess * channels - firstOutSamples
                                if (remainingOutSamples > 0) {
                                    outBuf.position(HEADER_SIZE_BYTES.toInt())
                                    outBuf.asFloatBuffer().put(scratchOut, firstOutSamples, remainingOutSamples)
                                }

                                // Advance write_pos in output header
                                outBuf.putLong(OFFSET_WRITE_POS, outWritePos + framesToProcess)
                            }
                        }

                        // Signal host via notifyOutFd (write 8-byte uint64 counter)
                        signalEventFd(notifyOutFd)
                    }
                }
            } catch (_: Exception) {
                if (isClosed.get()) break
            }
        }
    }

    /**
     * Synchronously processes one chunk of audio frames.
     * Useful for direct test verification or non-threaded invocations.
     */
    fun processFrames(
        input: FloatArray,
        output: FloatArray,
        frames: Int,
        bypassed: Boolean = isBypassedProvider(),
    ) {
        filter.process(input, output, frames, bypassed)
    }

    private fun drainEventFd(fd: FileDescriptor): Boolean {
        return try {
            eventFdBuffer.clear()
            Os.read(fd, eventFdBuffer) == 8
        } catch (_: Exception) {
            false
        }
    }

    private fun signalEventFd(fd: FileDescriptor): Boolean {
        return try {
            eventFdBuffer.clear()
            eventFdBuffer.putLong(1L).flip()
            Os.write(fd, eventFdBuffer) == 8
        } catch (_: Exception) {
            false
        }
    }

    override fun getStageInfo(): DspStageInfoDto = stageInfo

    override fun reset() {
        filter.reset()
    }

    override fun close() {
        if (isClosed.compareAndSet(false, true)) {
            workerThread?.interrupt()
            workerThread = null
            runCatching { inChannel?.close() }
            runCatching { outChannel?.close() }
            if (inChannel == null) {
                runCatching { dupShmemIn?.close() }
            }
            if (outChannel == null) {
                runCatching { dupShmemOut?.close() }
            }
            runCatching { dupNotifyIn?.close() }
            runCatching { dupNotifyOut?.close() }

            inMappedBuffer = null
            outMappedBuffer = null

            onClosed?.invoke()
        }
    }
}

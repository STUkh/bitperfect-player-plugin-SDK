package com.example.bitperfect.plugin.dspfilter

import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * High-precision stereo biquad filter based on the Audio EQ Cookbook formulas.
 * Implements Direct Form II Transposed topology for optimal numerical stability in 32-bit float audio.
 */
class BiquadFilter(
    private var sampleRate: Int = 44100,
    private var cutoffHz: Float = 1000.0f,
    private var qFactor: Float = 0.7071f,
    private var gainDb: Float = 0.0f,
) {
    // Normalized coefficients
    @Volatile private var b0 = 1.0f
    @Volatile private var b1 = 0.0f
    @Volatile private var b2 = 0.0f
    @Volatile private var a1 = 0.0f
    @Volatile private var a2 = 0.0f
    @Volatile private var linearGain = 1.0f

    // State registers for Transposed Direct Form II
    // Channel 0 (Left)
    private var s1Left = 0.0f
    private var s2Left = 0.0f

    // Channel 1 (Right)
    private var s1Right = 0.0f
    private var s2Right = 0.0f

    init {
        recalculate()
    }

    @Synchronized
    fun update(
        newSampleRate: Int = sampleRate,
        newCutoffHz: Float = cutoffHz,
        newQFactor: Float = qFactor,
        newGainDb: Float = gainDb,
    ) {
        sampleRate = newSampleRate.coerceAtLeast(8000)
        cutoffHz = newCutoffHz.coerceIn(10.0f, (sampleRate / 2.0f) * 0.95f)
        qFactor = newQFactor.coerceIn(0.1f, 10.0f)
        gainDb = newGainDb.coerceIn(-30.0f, 30.0f)
        recalculate()
    }

    private fun recalculate() {
        val nyquist = sampleRate / 2.0
        val clampedCutoff = cutoffHz.toDouble().coerceIn(10.0, nyquist * 0.95)
        val omega = 2.0 * Math.PI * clampedCutoff / sampleRate.toDouble()
        val alpha = sin(omega) / (2.0 * qFactor.toDouble())
        val cosOmega = cos(omega)

        // Low-pass filter coefficients
        val rawB0 = (1.0 - cosOmega) / 2.0
        val rawB1 = 1.0 - cosOmega
        val rawB2 = (1.0 - cosOmega) / 2.0
        val rawA0 = 1.0 + alpha
        val rawA1 = -2.0 * cosOmega
        val rawA2 = 1.0 - alpha

        // Normalize by a0
        b0 = (rawB0 / rawA0).toFloat()
        b1 = (rawB1 / rawA0).toFloat()
        b2 = (rawB2 / rawA0).toFloat()
        a1 = (rawA1 / rawA0).toFloat()
        a2 = (rawA2 / rawA0).toFloat()

        // Linear gain factor from dB
        linearGain = 10.0.pow(gainDb / 20.0).toFloat()
    }

    @Synchronized
    fun reset() {
        s1Left = 0.0f
        s2Left = 0.0f
        s1Right = 0.0f
        s2Right = 0.0f
    }

    /**
     * Processes interleaved stereo audio frames.
     * Each frame consists of 2 float samples (Left, Right).
     * Total samples = frames * 2.
     */
    fun process(
        input: FloatArray,
        output: FloatArray,
        frames: Int,
        bypassed: Boolean = false,
    ) {
        val totalSamples = frames * 2
        if (bypassed) {
            // Bit-perfect zero-alteration passthrough
            System.arraycopy(input, 0, output, 0, totalSamples)
            return
        }

        val curB0 = b0
        val curB1 = b1
        val curB2 = b2
        val curA1 = a1
        val curA2 = a2
        val curGain = linearGain

        var sampleIdx = 0
        for (f in 0 until frames) {
            // Process Left channel
            val inLeft = input[sampleIdx]
            val outLeft = curB0 * inLeft + s1Left
            s1Left = curB1 * inLeft - curA1 * outLeft + s2Left
            s2Left = curB2 * inLeft - curA2 * outLeft
            output[sampleIdx] = outLeft * curGain
            sampleIdx++

            // Process Right channel
            val inRight = input[sampleIdx]
            val outRight = curB0 * inRight + s1Right
            s1Right = curB1 * inRight - curA1 * outRight + s2Right
            s2Right = curB2 * inRight - curA2 * outRight
            output[sampleIdx] = outRight * curGain
            sampleIdx++
        }
    }
}

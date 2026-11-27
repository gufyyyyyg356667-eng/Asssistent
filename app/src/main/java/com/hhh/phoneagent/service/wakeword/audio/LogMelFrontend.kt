package com.hhh.phoneagent.service.wakeword.audio

import com.hhh.phoneagent.service.wakeword.WakeWordConfig
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/** Streams 16-bit PCM into log-mel frames. Keeps leftover samples between calls, so frames are continuous. */
class LogMelFrontend(private val config: WakeWordConfig) {

    private val window = FloatArray(config.frameLength) { i ->
        (0.5 - 0.5 * cos(2.0 * PI * i / (config.frameLength - 1))).toFloat()
    }
    private val powerSize = config.fftSize / 2 + 1
    private val filters = buildFilters()
    private val re = FloatArray(config.fftSize)
    private val im = FloatArray(config.fftSize)
    private val power = FloatArray(powerSize)
    private var carry = FloatArray(0)

    init {
        require(config.fftSize >= config.frameLength && (config.fftSize and (config.fftSize - 1)) == 0) {
            "fftSize must be a power of two and at least frameLength"
        }
    }

    fun push(pcm: ShortArray, count: Int): List<FloatArray> {
        val total = carry.size + count
        val samples = FloatArray(total)
        carry.copyInto(samples)
        for (i in 0 until count) samples[carry.size + i] = pcm[i] / 32768f

        val frames = ArrayList<FloatArray>()
        var start = 0
        while (start + config.frameLength <= total) {
            frames += melFrame(samples, start)
            start += config.hopLength
        }
        carry = samples.copyOfRange(start, total)
        return frames
    }

    private fun melFrame(samples: FloatArray, start: Int): FloatArray {
        re.fill(0f)
        im.fill(0f)
        for (i in 0 until config.frameLength) re[i] = samples[start + i] * window[i]
        fft()
        for (k in 0 until powerSize) power[k] = re[k] * re[k] + im[k] * im[k]
        return FloatArray(config.melBins) { m ->
            val filter = filters[m]
            var energy = 0f
            for (k in 0 until powerSize) energy += filter[k] * power[k]
            ln(max(energy, 1e-10f))
        }
    }

    /** In-place iterative radix-2 FFT on re/im. */
    private fun fft() {
        val n = config.fftSize
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while ((j and bit) != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val angle = -2.0 * PI / len
            val wr = cos(angle).toFloat()
            val wi = sin(angle).toFloat()
            val half = len / 2
            var i = 0
            while (i < n) {
                var curR = 1f
                var curI = 0f
                for (k in 0 until half) {
                    val a = i + k
                    val b = a + half
                    val vR = re[b] * curR - im[b] * curI
                    val vI = re[b] * curI + im[b] * curR
                    re[b] = re[a] - vR
                    im[b] = im[a] - vI
                    re[a] += vR
                    im[a] += vI
                    val nextR = curR * wr - curI * wi
                    curI = curR * wi + curI * wr
                    curR = nextR
                }
                i += len
            }
            len = len shl 1
        }
    }

    private fun buildFilters(): Array<FloatArray> {
        fun hzToMel(hz: Double) = 2595.0 * log10(1.0 + hz / 700.0)
        fun melToHz(mel: Double) = 700.0 * (10.0.pow(mel / 2595.0) - 1.0)

        val sr = config.sampleRate.toDouble()
        val melMin = hzToMel(0.0)
        val melMax = hzToMel(sr / 2.0)
        val points = DoubleArray(config.melBins + 2) { melToHz(melMin + (melMax - melMin) * it / (config.melBins + 1)) }
        val binHz = DoubleArray(powerSize) { it * sr / config.fftSize }

        return Array(config.melBins) { m ->
            val left = points[m]
            val center = points[m + 1]
            val right = points[m + 2]
            FloatArray(powerSize) { k ->
                val f = binHz[k]
                when {
                    f <= left || f >= right -> 0f
                    f <= center -> ((f - left) / (center - left)).toFloat()
                    else -> ((right - f) / (right - center)).toFloat()
                }
            }
        }
    }
}

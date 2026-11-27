package com.hhh.phoneagent.service.wakeword

import android.content.res.AssetManager
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/** Runs the TFLite wake-word model over a sliding window of log-mel frames. */
class WakeWordDetector private constructor(
    private val interpreter: Interpreter,
    private val config: WakeWordConfig,
) : Closeable {

    private val history = ArrayDeque<FloatArray>(config.windowFrames)
    private val input: ByteBuffer = ByteBuffer
        .allocateDirect(4 * config.windowFrames * config.melBins)
        .order(ByteOrder.nativeOrder())
    private val output = Array(1) { FloatArray(1) }
    private var framesSinceInference = 0

    /** Returns the score if a detection fires on any of the new frames, otherwise null. */
    fun feed(frames: List<FloatArray>): Float? {
        for (frame in frames) {
            if (history.size == config.windowFrames) history.removeFirst()
            history.addLast(frame)
            framesSinceInference++
            if (history.size == config.windowFrames && framesSinceInference >= config.inferStride) {
                framesSinceInference = 0
                val score = infer()
                if (score >= config.threshold) return score
            }
        }
        return null
    }

    private fun infer(): Float {
        input.rewind()
        for (frame in history) for (value in frame) input.putFloat(value)
        input.rewind()
        interpreter.run(input, output)
        return output[0][0]
    }

    override fun close() {
        interpreter.close()
    }

    companion object {
        /** Throws if the model asset is missing or invalid. The caller reports this to the user. */
        fun open(assets: AssetManager, config: WakeWordConfig): WakeWordDetector {
            val interpreter = Interpreter(mapModel(assets, config.modelAssetPath), Interpreter.Options().setNumThreads(1))
            return WakeWordDetector(interpreter, config)
        }

        private fun mapModel(assets: AssetManager, path: String): MappedByteBuffer =
            assets.openFd(path).use { descriptor ->
                FileInputStream(descriptor.fileDescriptor).channel.use { channel ->
                    channel.map(FileChannel.MapMode.READ_ONLY, descriptor.startOffset, descriptor.declaredLength)
                }
            }
    }
}

package com.hhh.phoneagent.service.wakeword

/**
 * Feature and model settings. The model must be trained with these exact features:
 * 16 kHz mono, 25 ms Hann frames, 10 ms hop, 40 log-mel bins, a 49-frame window.
 * Input tensor size is windowFrames * melBins floats.
 */
data class WakeWordConfig(
    val modelAssetPath: String = "wakeword/hey_nova.tflite",
    val sampleRate: Int = 16_000,
    val frameLength: Int = 400,
    val hopLength: Int = 160,
    val fftSize: Int = 512,
    val melBins: Int = 40,
    val windowFrames: Int = 49,
    val inferStride: Int = 5,
    val threshold: Float = 0.9f,
    val chunkSamples: Int = 1_600,
    val minBufferBytes: Int = 3_200,
) {
    companion object {
        val DEFAULT = WakeWordConfig()
    }
}

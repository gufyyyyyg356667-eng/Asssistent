package com.hhh.phoneagent.speech.live

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.hhh.phoneagent.data.live.LiveProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Plays the model's 24 kHz PCM16 speech. Chunks are queued and written by one IO coroutine,
 * so network jitter never blocks the socket reader. [interrupt] implements barge-in.
 */
class LivePlayer @Inject constructor() {

    @Volatile private var queue: Channel<ByteArray>? = null
    @Volatile private var track: AudioTrack? = null

    fun start(scope: CoroutineScope) {
        stop()
        val minBuffer = AudioTrack.getMinBufferSize(LiveProtocol.OUTPUT_SAMPLE_RATE, CHANNEL, ENCODING)
        require(minBuffer > 0) { "This device does not support 24 kHz playback" }
        val built = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(ENCODING)
                    .setSampleRate(LiveProtocol.OUTPUT_SAMPLE_RATE)
                    .setChannelMask(CHANNEL)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(maxOf(minBuffer, LiveProtocol.OUTPUT_SAMPLE_RATE))
            .build()
        val chunks = Channel<ByteArray>(Channel.UNLIMITED)
        built.play()
        track = built
        queue = chunks
        scope.launch(Dispatchers.IO) {
            try {
                for (chunk in chunks) {
                    if (built.write(chunk, 0, chunk.size) < 0) break
                }
            } finally {
                runCatching { built.release() }
            }
        }
    }

    fun enqueue(pcm: ByteArray) {
        queue?.trySend(pcm)
    }

    /** Drops queued speech and flushes what the hardware has not played yet. */
    fun interrupt() {
        val chunks = queue ?: return
        while (chunks.tryReceive().isSuccess) Unit
        track?.let { runCatching { it.pause(); it.flush(); it.play() } }
    }

    fun stop() {
        queue?.close()
        queue = null
        track?.let { runCatching { it.pause(); it.flush(); it.stop() } }
        track = null
    }

    private companion object {
        const val CHANNEL = AudioFormat.CHANNEL_OUT_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    }
}

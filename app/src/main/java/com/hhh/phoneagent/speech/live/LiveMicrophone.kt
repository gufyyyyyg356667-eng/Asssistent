package com.hhh.phoneagent.speech.live

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.hhh.phoneagent.data.live.LiveProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Streams 16 kHz mono PCM16 in 100 ms chunks until the calling coroutine is cancelled.
 * VOICE_COMMUNICATION enables the platform echo canceller where the device has one, so the
 * model's own voice coming out of the speaker is not sent back as user speech.
 */
class LiveMicrophone @Inject constructor() {

    @SuppressLint("MissingPermission")
    suspend fun stream(onChunk: (ByteArray) -> Unit): Unit = withContext(Dispatchers.IO) {
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        require(minBuffer > 0) { "This device does not support 16 kHz microphone capture" }
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            SAMPLE_RATE,
            CHANNEL,
            ENCODING,
            maxOf(minBuffer, CHUNK_BYTES * 2),
        )
        try {
            check(record.state == AudioRecord.STATE_INITIALIZED) { "Microphone could not be opened" }
            val buffer = ByteArray(CHUNK_BYTES)
            record.startRecording()
            while (isActive) {
                val read = record.read(buffer, 0, buffer.size)
                when {
                    read > 0 -> onChunk(buffer.copyOf(read))
                    read < 0 -> error("Microphone read failed ($read)")
                }
            }
        } finally {
            runCatching { record.stop() }
            record.release()
        }
    }

    private companion object {
        const val SAMPLE_RATE = LiveProtocol.INPUT_SAMPLE_RATE
        const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        const val CHUNK_BYTES = SAMPLE_RATE / 10 * 2
    }
}

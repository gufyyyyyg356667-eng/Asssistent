package com.hhh.phoneagent.service.wakeword

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.hhh.phoneagent.service.wakeword.audio.LogMelFrontend
import com.hhh.phoneagent.speech.MicArbiter
import com.hhh.phoneagent.speech.MicOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

sealed interface WakeOutcome {
    data class Detected(val score: Float) : WakeOutcome
    data object Interrupted : WakeOutcome
    data class Failed(val reason: String) : WakeOutcome
}

/**
 * Keeps the mic open only while the arbiter says WAKE_WORD owns it. Returns [WakeOutcome.Detected]
 * with the hardware already released, so a speech turn can start SpeechRecognizer immediately.
 */
@Singleton
class WakeWordEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val arbiter: MicArbiter,
) {
    private val config = WakeWordConfig.DEFAULT

    suspend fun listen(): WakeOutcome = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return@withContext WakeOutcome.Failed("Microphone permission is not granted")
        }
        val detector = try {
            WakeWordDetector.open(context.assets, config)
        } catch (e: Exception) {
            return@withContext WakeOutcome.Failed("Wake-word model could not be loaded (${config.modelAssetPath}): ${e.message}")
        }
        try {
            recordUntilWake(detector)
        } finally {
            detector.close()
        }
    }

    private fun CoroutineScope.recordUntilWake(detector: WakeWordDetector): WakeOutcome {
        val minBuffer = AudioRecord.getMinBufferSize(
            config.sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) return WakeOutcome.Failed("Device does not support 16 kHz mono capture")

        val record = try {
            AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(config.sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(maxOf(minBuffer, config.minBufferBytes))
                .build()
        } catch (e: SecurityException) {
            return WakeOutcome.Failed("Microphone permission was revoked")
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return WakeOutcome.Failed("AudioRecord failed to initialize")
        }

        val frontend = LogMelFrontend(config)
        val chunk = ShortArray(config.chunkSamples)
        arbiter.setWakeHardwareHeld(true)
        try {
            record.startRecording()
            while (isActive && arbiter.owner.value == MicOwner.WAKE_WORD) {
                val read = record.read(chunk, 0, chunk.size)
                if (read < 0) return WakeOutcome.Failed("Microphone read failed (code $read)")
                if (read == 0) continue
                val score = detector.feed(frontend.push(chunk, read))
                if (score != null) return WakeOutcome.Detected(score)
            }
            return WakeOutcome.Interrupted
        } catch (e: SecurityException) {
            return WakeOutcome.Failed("Microphone permission was revoked")
        } finally {
            runCatching { record.stop() }
            record.release()
            arbiter.setWakeHardwareHeld(false)
        }
    }
}

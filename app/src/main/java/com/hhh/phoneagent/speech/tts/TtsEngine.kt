package com.hhh.phoneagent.speech.tts

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.hhh.phoneagent.core.common.AppError
import com.hhh.phoneagent.core.common.AppResult
import com.hhh.phoneagent.core.common.failure
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Speaks text and suspends until the utterance finishes, so the caller knows when the mic may be released. */
@Singleton
class TtsEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val initLock = Mutex()
    private var engine: TextToSpeech? = null
    private val waiters = ConcurrentHashMap<String, (AppResult<Unit>) -> Unit>()

    private val progress = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit
        override fun onDone(utteranceId: String?) = finish(utteranceId, AppResult.Success(Unit))

        @Suppress("OVERRIDE_DEPRECATION")
        override fun onError(utteranceId: String?) =
            finish(utteranceId, failure(AppError.ErrorCode.UNKNOWN, "Speech output failed"))

        override fun onError(utteranceId: String?, errorCode: Int) =
            finish(utteranceId, failure(AppError.ErrorCode.UNKNOWN, "Speech output failed (code $errorCode)"))

        private fun finish(id: String?, result: AppResult<Unit>) {
            if (id != null) waiters.remove(id)?.invoke(result)
        }
    }

    suspend fun speak(text: String): AppResult<Unit> {
        val spoken = text.trim().take(MAX_CHARS)
        if (spoken.isEmpty()) return AppResult.Success(Unit)
        val tts = ensureReady()
            ?: return failure(AppError.ErrorCode.SERVICE_UNAVAILABLE, "Text-to-speech is not available")
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine<AppResult<Unit>> { cont ->
                val id = UUID.randomUUID().toString()
                waiters[id] = { result -> if (cont.isActive) cont.resume(result) }
                cont.invokeOnCancellation {
                    waiters.remove(id)
                    tts.stop()
                }
                tts.setLanguage(if (spoken.any { it in '\u0980'..'\u09FF' }) Locale("bn", "BD") else Locale.US)
                tts.setSpeechRate(0.95f)
                tts.setPitch(1.05f)
                if (tts.speak(spoken, TextToSpeech.QUEUE_FLUSH, null, id) == TextToSpeech.ERROR) {
                    waiters.remove(id)
                    cont.resume(failure(AppError.ErrorCode.UNKNOWN, "Could not queue speech"))
                }
            }
        }
    }

    private suspend fun ensureReady(): TextToSpeech? = initLock.withLock {
        engine?.let { return@withLock it }
        val created = withContext(Dispatchers.Main) { createEngine() } ?: return@withLock null
        engine = created
        created
    }

    private suspend fun createEngine(): TextToSpeech? {
        val ready = CompletableDeferred<Boolean>()
        val tts = TextToSpeech(context) { status -> ready.complete(status == TextToSpeech.SUCCESS) }
        val ok = withTimeoutOrNull(INIT_TIMEOUT_MS) { ready.await() } == true
        if (!ok) {
            tts.shutdown()
            return null
        }
        tts.setOnUtteranceProgressListener(progress)
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        return tts
    }

    private companion object {
        const val MAX_CHARS = 3_000
        const val INIT_TIMEOUT_MS = 5_000L
    }
}

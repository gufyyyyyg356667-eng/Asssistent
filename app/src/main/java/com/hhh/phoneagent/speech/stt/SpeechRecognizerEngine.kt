package com.hhh.phoneagent.speech.stt

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.hhh.phoneagent.core.common.AppError
import com.hhh.phoneagent.core.common.AppResult
import com.hhh.phoneagent.core.common.failure
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.isActive
import javax.inject.Inject
import javax.inject.Singleton

/** One utterance from the system recognizer. Must run on the main thread, which this class enforces. */
@Singleton
class SpeechRecognizerEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    suspend fun listenOnce(onPartial: (String) -> Unit = {}): AppResult<String> {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            return failure(AppError.ErrorCode.SERVICE_UNAVAILABLE, "Speech recognition is not available on this device")
        }
        return withContext(Dispatchers.Main) {
            withTimeoutOrNull(LISTEN_TIMEOUT_MS) { listenOnMainThread(onPartial) }
                ?: failure(AppError.ErrorCode.INVALID_INPUT, "No speech was detected in time")
        }
    }

    private suspend fun listenOnMainThread(onPartial: (String) -> Unit): AppResult<String> {
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        return try {
            suspendCancellableCoroutine<AppResult<String>> { cont ->
                fun finish(result: AppResult<String>) {
                    if (cont.isActive) cont.resume(result)
                }
                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) = Unit
                    override fun onBeginningOfSpeech() = Unit
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() = Unit
                    override fun onEvent(eventType: Int, params: Bundle?) = Unit

                    override fun onPartialResults(partialResults: Bundle?) {
                        partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()?.let(onPartial)
                    }

                    override fun onError(error: Int) = finish(errorFor(error))

                    override fun onResults(results: Bundle?) {
                        val best = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()?.trim()
                        finish(
                            if (best.isNullOrEmpty()) failure(AppError.ErrorCode.INVALID_INPUT, "Nothing was heard")
                            else AppResult.Success(best),
                        )
                    }
                })
                cont.invokeOnCancellation { recognizer.cancel() }
                recognizer.startListening(recognitionIntent())
            }
        } finally {
            recognizer.destroy()
        }
    }

    private fun recognitionIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)

    private fun errorFor(code: Int): AppResult.Failure = when (code) {
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
            failure(AppError.ErrorCode.INVALID_INPUT, "Nothing was heard")
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
            failure(AppError.ErrorCode.ACTION_REJECTED, "Microphone permission is missing")
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            failure(AppError.ErrorCode.NETWORK, "Speech recognition needs a network connection")
        else -> failure(AppError.ErrorCode.UNKNOWN, "Speech recognition failed (code $code)")
    }

    private companion object {
        const val LISTEN_TIMEOUT_MS = 12_000L
    }
}

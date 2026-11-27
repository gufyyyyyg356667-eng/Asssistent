package com.hhh.phoneagent.speech

import android.util.Log
import com.hhh.phoneagent.core.common.AppError
import com.hhh.phoneagent.core.common.AppResult
import com.hhh.phoneagent.core.common.failure
import com.hhh.phoneagent.domain.model.TurnSource
import com.hhh.phoneagent.domain.model.VoiceState
import com.hhh.phoneagent.domain.usecase.RunAgentTurnUseCase
import com.hhh.phoneagent.speech.stt.SpeechRecognizerEngine
import com.hhh.phoneagent.speech.tts.TtsEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One conversation: listen, think, speak, then keep listening for a short follow-up.
 * The mic stays with the turn for the whole conversation, so wake word stays paused.
 */
@Singleton
class SpeechCoordinator @Inject constructor(
    private val arbiter: MicArbiter,
    private val recognizer: SpeechRecognizerEngine,
    private val tts: TtsEngine,
    private val focus: AudioFocusCoordinator,
    private val agent: RunAgentTurnUseCase,
) {
    private val turnLock = Mutex()
    private val stateFlow = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val state: StateFlow<VoiceState> = stateFlow.asStateFlow()

    fun setWakeListening(active: Boolean) {
        stateFlow.update { current ->
            when {
                active && current == VoiceState.Idle -> VoiceState.WakeListening
                !active && current == VoiceState.WakeListening -> VoiceState.Idle
                else -> current
            }
        }
    }

    fun reportError(message: String) {
        stateFlow.value = VoiceState.Error(message)
    }

    suspend fun runTurn(source: TurnSource): AppResult<Unit> {
        if (!turnLock.tryLock()) {
            return failure(AppError.ErrorCode.ACTION_REJECTED, "A voice turn is already running")
        }
        return try {
            stateFlow.value = VoiceState.Idle
            Log.d(TAG, "Conversation started from $source")
            val result = if (arbiter.claim(MicOwner.SPEECH_TURN)) {
                runConversation()
            } else {
                fail("Microphone is busy, please try again")
            }
            if (result is AppResult.Success) stateFlow.value = VoiceState.Idle
            result
        } finally {
            focus.abandon()
            arbiter.release(MicOwner.SPEECH_TURN)
            turnLock.unlock()
        }
    }

    private suspend fun runConversation(): AppResult<Unit> {
        focus.requestTransient()
        var turns = 0
        while (turns < MAX_TURNS) {
            val timeout = if (turns == 0) FIRST_LISTEN_MS else FOLLOW_UP_LISTEN_MS
            stateFlow.value = VoiceState.Listening()
            val transcript = when (val heard = recognizer.listenOnce(timeout) { partial ->
                stateFlow.value = VoiceState.Listening(partial)
            }) {
                is AppResult.Failure -> {
                    // Silence after the first turn just means the user is done talking.
                    if (turns > 0 && heard.error.code == AppError.ErrorCode.INVALID_INPUT) {
                        return AppResult.Success(Unit)
                    }
                    return fail(heard.error.message)
                }
                is AppResult.Success -> heard.value
            }
            turns++

            stateFlow.value = VoiceState.Thinking
            val reply = when (val answer = agent(transcript)) {
                is AppResult.Failure -> {
                    tts.speak("দুঃখিত, এখন কাজটি করতে পারছি না। একটু পরে আবার বলবেন।")
                    return fail(answer.error.message)
                }
                is AppResult.Success -> answer.value
            }

            stateFlow.value = VoiceState.Speaking
            when (val spoken = tts.speak(reply)) {
                is AppResult.Failure -> return fail(spoken.error.message)
                is AppResult.Success -> Unit
            }
        }
        return AppResult.Success(Unit)
    }

    private fun fail(message: String): AppResult.Failure {
        stateFlow.value = VoiceState.Error(message)
        return failure(AppError.ErrorCode.ACTION_REJECTED, message)
    }

    private companion object {
        const val TAG = "SpeechCoordinator"
        const val FIRST_LISTEN_MS = 12_000L
        const val FOLLOW_UP_LISTEN_MS = 6_000L
        const val MAX_TURNS = 6
    }
}

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
 * Runs one voice turn: claim mic, listen, think, speak, release mic.
 * Only one turn runs at a time. Wake listening is paused for the entire turn.
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
            Log.d(TAG, "Turn started from $source")
            if (arbiter.claim(MicOwner.SPEECH_TURN)) {
                runClaimedTurn()
            } else {
                fail("Microphone is busy, please try again")
            }
        } finally {
            focus.abandon()
            arbiter.release(MicOwner.SPEECH_TURN)
            turnLock.unlock()
        }
    }

    private suspend fun runClaimedTurn(): AppResult<Unit> {
        focus.requestTransient()
        stateFlow.value = VoiceState.Listening()
        val transcript = when (val heard = recognizer.listenOnce { partial ->
            stateFlow.value = VoiceState.Listening(partial)
        }) {
            is AppResult.Failure -> return fail(heard.error.message)
            is AppResult.Success -> heard.value
        }

        stateFlow.value = VoiceState.Thinking
        val reply = when (val answer = agent(transcript)) {
            is AppResult.Failure -> {
                tts.speak("Sorry, I could not complete that.")
                return fail(answer.error.message)
            }
            is AppResult.Success -> answer.value
        }

        stateFlow.value = VoiceState.Speaking
        return when (val spoken = tts.speak(reply)) {
            is AppResult.Failure -> fail(spoken.error.message)
            is AppResult.Success -> {
                stateFlow.value = VoiceState.Idle
                AppResult.Success(Unit)
            }
        }
    }

    private fun fail(message: String): AppResult.Failure {
        stateFlow.value = VoiceState.Error(message)
        return failure(AppError.ErrorCode.ACTION_REJECTED, message)
    }

    private companion object {
        const val TAG = "SpeechCoordinator"
    }
}

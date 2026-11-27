package com.hhh.phoneagent.speech

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

enum class MicOwner { NONE, WAKE_WORD, SPEECH_TURN }

/**
 * Single source of truth for who may use the microphone. The wake-word loop must close its
 * AudioRecord before a speech turn may start SpeechRecognizer. Wake listening stays paused for
 * the whole turn (STT, LLM, TTS) and resumes only when the turn releases the mic.
 */
@Singleton
class MicArbiter @Inject constructor() {

    private val lock = Mutex()
    private val ownerState = MutableStateFlow(MicOwner.NONE)
    private val hardwareReleased = Channel<Unit>(Channel.CONFLATED)

    @Volatile
    private var wakeHoldsHardware = false

    val owner: StateFlow<MicOwner> = ownerState.asStateFlow()

    /** Called by the wake-word engine around AudioRecord start and stop. */
    fun setWakeHardwareHeld(held: Boolean) {
        if (held) {
            hardwareReleased.tryReceive()
            wakeHoldsHardware = true
        } else {
            wakeHoldsHardware = false
            hardwareReleased.trySend(Unit)
        }
    }

    /** Takes the mic. Waits (bounded) for the wake engine to close AudioRecord if needed. */
    suspend fun claim(owner: MicOwner): Boolean = lock.withLock {
        val previous = ownerState.value
        if (previous == owner) return@withLock true
        if (previous == MicOwner.WAKE_WORD && wakeHoldsHardware) {
            val released = withTimeoutOrNull(RELEASE_TIMEOUT_MS) { hardwareReleased.receive() } != null
            if (!released) {
                ownerState.value = MicOwner.NONE
                return@withLock false
            }
        }
        ownerState.value = owner
        true
    }

    /** Clears ownership only if [owner] still holds it. Never clears someone else's claim. */
    fun release(owner: MicOwner) {
        ownerState.update { current -> if (current == owner) MicOwner.NONE else current }
    }

    companion object {
        const val RELEASE_TIMEOUT_MS = 2_000L
    }
}

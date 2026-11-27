package com.hhh.phoneagent.domain.model

sealed interface VoiceState {
    data object Idle : VoiceState
    data object WakeListening : VoiceState
    data class Listening(val partialText: String = "") : VoiceState
    data object Thinking : VoiceState
    data object Speaking : VoiceState
    data class Error(val message: String) : VoiceState
}

enum class TurnSource { WAKE_WORD, BUTTON }

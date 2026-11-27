package com.hhh.phoneagent.speech.live

sealed interface LiveState {
    data object Off : LiveState
    data object Connecting : LiveState
    data object Connected : LiveState
    data class Error(val message: String) : LiveState
}

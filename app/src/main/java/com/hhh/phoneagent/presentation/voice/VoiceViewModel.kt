package com.hhh.phoneagent.presentation.voice

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hhh.phoneagent.data.local.prefs.VoiceSettingsStore
import com.hhh.phoneagent.domain.model.TurnSource
import com.hhh.phoneagent.domain.model.VoiceState
import com.hhh.phoneagent.service.wakeword.WakeWordController
import com.hhh.phoneagent.speech.SpeechCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VoiceViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: VoiceSettingsStore,
    private val coordinator: SpeechCoordinator,
) : ViewModel() {

    val wakeWordEnabled: StateFlow<Boolean> =
        settings.wakeWordEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val voiceState: StateFlow<VoiceState> = coordinator.state

    fun setWakeWordEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settings.setWakeWordEnabled(enabled)
            if (enabled) WakeWordController.start(context) else WakeWordController.stop(context)
        }
    }

    fun talkNow() {
        viewModelScope.launch { coordinator.runTurn(TurnSource.BUTTON) }
    }
}

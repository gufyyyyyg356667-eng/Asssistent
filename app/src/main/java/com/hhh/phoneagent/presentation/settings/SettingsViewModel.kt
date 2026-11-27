package com.hhh.phoneagent.presentation.settings

import androidx.lifecycle.ViewModel
import com.hhh.phoneagent.domain.repository.GeminiKeyStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val store: GeminiKeyStore,
) : ViewModel() {

    private val keysState = MutableStateFlow(store.keys())
    val keys: StateFlow<List<String>> = keysState.asStateFlow()

    /** Adds every key-like token from [raw] (split by new line, comma or space). Returns how many were new. */
    fun addKeys(raw: String): Int {
        val candidates = raw.split('\n', ',', ' ', '\t')
            .map { it.trim() }
            .filter { it.length >= MIN_KEY_LENGTH && !it.contains(' ') }
        val current = keysState.value
        val added = candidates.filter { it !in current }.distinct()
        if (added.isEmpty()) return 0
        val updated = current + added
        store.saveKeys(updated)
        keysState.value = updated
        return added.size
    }

    fun removeKey(key: String) {
        val updated = keysState.value.filter { it != key }
        store.saveKeys(updated)
        keysState.value = updated
    }

    companion object {
        const val MIN_KEY_LENGTH = 20

        fun mask(key: String): String = "…" + key.takeLast(4)
    }
}

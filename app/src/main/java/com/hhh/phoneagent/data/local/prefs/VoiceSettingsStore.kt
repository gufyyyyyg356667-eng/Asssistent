package com.hhh.phoneagent.data.local.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.voiceSettingsDataStore by preferencesDataStore(name = "voice_settings")

@Singleton
class VoiceSettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val wakeWordEnabled: Flow<Boolean> = context.voiceSettingsDataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs: Preferences -> prefs[WAKE_WORD_KEY] ?: false }

    suspend fun setWakeWordEnabled(enabled: Boolean) {
        context.voiceSettingsDataStore.edit { it[WAKE_WORD_KEY] = enabled }
    }

    private companion object {
        val WAKE_WORD_KEY = booleanPreferencesKey("wake_word_enabled")
    }
}

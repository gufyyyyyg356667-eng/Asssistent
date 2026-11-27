package com.hhh.phoneagent.data.local.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.hhh.phoneagent.domain.repository.GeminiKeyStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Gemini API keys, encrypted at rest with a Keystore-backed master key. */
@Singleton
class EncryptedGeminiKeyStore @Inject constructor(
    @ApplicationContext context: Context,
) : GeminiKeyStore {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override fun keys(): List<String> =
        prefs.getString(KEY_LIST, "").orEmpty()
            .split(SEPARATOR)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

    override fun saveKeys(keys: List<String>) {
        val cleaned = keys.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        prefs.edit().putString(KEY_LIST, cleaned.joinToString(SEPARATOR)).apply()
    }

    private companion object {
        const val FILE_NAME = "gemini_keys_secure"
        const val KEY_LIST = "gemini_api_keys"
        const val SEPARATOR = "\n"
    }
}

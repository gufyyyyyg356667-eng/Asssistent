package com.mark.android

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** API keys are encrypted with a key that lives in the phone's hardware-backed Keystore and never leaves it. */
object Secure {
    private const val ALIAS = "mark_secret_key"
    private val cache = HashMap<String, String>()

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        g.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        return g.generateKey()
    }

    @Synchronized fun put(name: String, value: String) {
        val v = value.trim()
        val e = Store.prefs.edit().remove(name)
        if (v.isEmpty()) e.remove("${name}_enc")
        else {
            val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, key())
            e.putString("${name}_enc", Base64.encodeToString(c.iv + c.doFinal(v.toByteArray()), Base64.NO_WRAP))
        }
        e.apply(); cache[name] = v
    }

    @Synchronized fun get(name: String): String {
        cache[name]?.let { return it }
        val enc = Store.prefs.getString("${name}_enc", null)
        if (enc != null) {
            return try {
                val raw = Base64.decode(enc, Base64.NO_WRAP)
                val c = Cipher.getInstance("AES/GCM/NoPadding")
                c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw.copyOfRange(0, 12)))
                String(c.doFinal(raw.copyOfRange(12, raw.size))).also { cache[name] = it }
            } catch (e: Exception) { "" }
        }
        val legacy = Store.prefs.getString(name, "")!!.trim()      // an older version stored it in plain text: encrypt it now
        if (legacy.isNotEmpty()) try { put(name, legacy) } catch (e: Exception) { }
        return legacy
    }
}

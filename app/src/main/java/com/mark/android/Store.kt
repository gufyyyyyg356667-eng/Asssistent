package com.mark.android

import android.app.Application
import android.content.Context
import android.content.SharedPreferences

class MarkApp : Application() {
    override fun onCreate() { super.onCreate(); Store.app = this }
}

object Store {
    lateinit var app: Context
    val prefs: SharedPreferences get() = app.getSharedPreferences("mark", Context.MODE_PRIVATE)
    val key: String get() = Secure.get("key")
    val textModel: String get() = prefs.getString("model", "gemini-2.5-flash")!!.trim()
    val liveModel: String get() = prefs.getString("live_model", "models/gemini-3.1-flash-live-preview")!!.trim()
    val providerUrl: String get() = prefs.getString("provider_url", "")!!.trim()
    val providerModel: String get() = prefs.getString("provider_model", "llama3.1")!!.trim()
    val providerKey: String get() = Secure.get("provider_key")
    val dailyCap: Int get() = prefs.getInt("daily_cap", 0)
    val persona: String get() = prefs.getString("persona", "")!!.trim()
    val hud: Boolean get() = prefs.getBoolean("hud", false)
    val maxSteps: Int get() = prefs.getInt("max_steps", 80)
    val voice: String get() = prefs.getString("voice", "Puck")!!.trim()
}

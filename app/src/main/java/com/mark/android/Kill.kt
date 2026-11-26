package com.mark.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Stops everything at once: phone task, research, voice, speech. Reachable by a notification button and by holding both volume keys. */
object Kill {
    fun all(reason: String) {
        Bus.log("⛔ Stopped everything ($reason)")
        try { AgentService.instance?.stop(); AgentService.instance?.hush() } catch (e: Exception) { }
        try { Research.cancel() } catch (e: Exception) { }
        try { VoiceService.inst?.kill() } catch (e: Exception) { }
        Bus.pending?.complete(false)
        Notifier.clearTaskStop()
    }
}

class KillReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) { Kill.all("notification button") }
}

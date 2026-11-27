package com.hhh.phoneagent.service.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.hhh.phoneagent.data.local.prefs.VoiceSettingsStore
import com.hhh.phoneagent.service.wakeword.WakeWordController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Android 12+ blocks microphone foreground services from starting after boot, so a failed
 * start is logged and the user reopens the app. It does not crash.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var settings: VoiceSettingsStore

    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope.launch {
            try {
                if (settings.wakeWordEnabled.first()) {
                    WakeWordController.start(context.applicationContext)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Wake word did not restart after boot; open the app to resume it", e)
            } finally {
                pending.finish()
                scope.cancel()
            }
        }
    }

    private companion object {
        const val TAG = "BootReceiver"
    }
}

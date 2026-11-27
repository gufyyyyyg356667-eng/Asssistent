package com.hhh.phoneagent.service.wakeword

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/** Starts and stops the wake-word service. Start only from a foreground UI action on Android 12+. */
object WakeWordController {

    fun start(context: Context) {
        ContextCompat.startForegroundService(context, Intent(context, WakeWordService::class.java))
    }

    fun stop(context: Context) {
        context.stopService(Intent(context, WakeWordService::class.java))
    }
}

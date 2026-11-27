package com.hhh.phoneagent.service.live

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.hhh.phoneagent.speech.live.LiveController
import com.hhh.phoneagent.speech.live.LiveState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Keeps Live voice alive in the background. Microphone capture from a background app requires
 * a foreground service of type microphone. The service stops itself once the session ends.
 */
@AndroidEntryPoint
class LiveService : Service() {

    @Inject lateinit var live: LiveController

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        createChannel()
        if (!startForegroundSafely()) {
            stopSelf()
            return START_NOT_STICKY
        }
        scope.launch {
            live.start()
            // Ends the service when the session ends or fails. A failed start already set Error.
            live.state.first { it is LiveState.Error || it == LiveState.Off }
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        live.stop()
        super.onDestroy()
    }

    private fun startForegroundSafely(): Boolean = try {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
        )
        true
    } catch (e: Exception) {
        Log.w(TAG, "Live service could not enter the foreground", e)
        false
    }

    private fun notification(): Notification {
        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, LiveService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Live voice is on")
            .setContentText("Talking with Gemini")
            .setOngoing(true)
            .setSilent(true)
            .addAction(0, "Stop", stopIntent)
            .build()
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Live voice", NotificationManager.IMPORTANCE_LOW),
        )
    }

    companion object {
        private const val TAG = "LiveService"
        private const val CHANNEL_ID = "live_voice"
        private const val NOTIFICATION_ID = 42
        const val ACTION_STOP = "com.hhh.phoneagent.live.STOP"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, LiveService::class.java))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, LiveService::class.java).setAction(ACTION_STOP))
        }
    }
}

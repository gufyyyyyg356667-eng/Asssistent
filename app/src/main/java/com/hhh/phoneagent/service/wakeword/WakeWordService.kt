package com.hhh.phoneagent.service.wakeword

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.hhh.phoneagent.domain.model.TurnSource
import com.hhh.phoneagent.speech.MicArbiter
import com.hhh.phoneagent.speech.MicOwner
import com.hhh.phoneagent.speech.SpeechCoordinator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Foreground microphone service that loops: wait for mic, listen for wake word, run a turn. */
@AndroidEntryPoint
class WakeWordService : Service() {

    @Inject lateinit var arbiter: MicArbiter
    @Inject lateinit var engine: WakeWordEngine
    @Inject lateinit var coordinator: SpeechCoordinator

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var supervisor: Job? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!startForegroundSafely()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (supervisor == null) supervisor = scope.launch { superviseWakeLoop() }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        supervisor = null
        arbiter.release(MicOwner.WAKE_WORD)
        coordinator.setWakeListening(false)
        super.onDestroy()
    }

    private suspend fun superviseWakeLoop() {
        while (currentCoroutineContext().isActive) {
            arbiter.owner.first { it == MicOwner.NONE }
            if (!arbiter.claim(MicOwner.WAKE_WORD)) continue

            coordinator.setWakeListening(true)
            val outcome = try {
                engine.listen()
            } catch (e: Exception) {
                WakeOutcome.Failed(e.message ?: "Wake-word error")
            }
            coordinator.setWakeListening(false)

            when (outcome) {
                is WakeOutcome.Detected -> {
                    Log.i(TAG, "Wake word detected (score ${outcome.score})")
                    coordinator.runTurn(TurnSource.WAKE_WORD)
                }
                is WakeOutcome.Interrupted -> Unit
                is WakeOutcome.Failed -> {
                    Log.e(TAG, "Wake word stopped: ${outcome.reason}")
                    coordinator.reportError(outcome.reason)
                    arbiter.release(MicOwner.WAKE_WORD)
                    stopSelf()
                    return
                }
            }
            arbiter.release(MicOwner.WAKE_WORD)
        }
    }

    private fun startForegroundSafely(): Boolean = try {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
        )
        true
    } catch (e: Exception) {
        Log.e(TAG, "Cannot start microphone foreground service", e)
        false
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Phone Agent is listening")
            .setContentText("Say the wake word to start")
            .setOngoing(true)
            .build()

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Wake word", NotificationManager.IMPORTANCE_LOW),
        )
    }

    private companion object {
        const val TAG = "WakeWordService"
        const val CHANNEL_ID = "wake_word"
        const val NOTIFICATION_ID = 1001
    }
}

package com.mark.android

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.util.Locale

/**
 * Foreground microphone service. WAKE_ON listens for the wake phrase with Android's speech recognizer,
 * then opens a Gemini Live session; when that session ends it goes back to listening. LIVE opens one directly.
 */
class VoiceService : Service() {
    companion object { var inst: VoiceService? = null }
    override fun onCreate() { super.onCreate(); inst = this }
    fun kill() { wake = false; live?.stop("kill switch"); killSr(); stopSelf() }
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var sr: SpeechRecognizer? = null
    private var live: LiveSession? = null
    private var wake = false

    private val phrases = listOf("hey jarvis", "hey mark", "jarvis", "okay mark", "হেই জার্ভিস", "জার্ভিস", "হ্যালো মার্ক", "হে মার্ক")

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, flags: Int, startId: Int): Int {
        // a sticky restart after Android killed us arrives with no intent: resume listening if wake mode was on
        val action = i?.action ?: if (Store.prefs.getBoolean("wake", false)) "WAKE_ON" else null
        try { startFg() } catch (e: Exception) {
            Bus.log("Android would not start the microphone service from the background: ${e.message}")
            Notifier.showOpen("Mark", "Tap to turn voice listening back on")
            stopSelf(); return START_NOT_STICKY
        }
        when (action) {
            "WAKE_ON" -> { wake = true; if (live == null) listenWake() }
            "WAKE_OFF" -> { wake = false; killSr(); if (live == null) stopSelf() }
            "LIVE" -> { killSr(); startLive() }
            "STOP" -> { wake = false; live?.stop("by you"); killSr(); stopSelf() }
        }
        return if (wake) START_STICKY else START_NOT_STICKY
    }

    private fun startFg() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("voice", "Mark voice", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 0, Intent(this, VoiceService::class.java).setAction("STOP"),
            PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, "voice").setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Mark is listening").setContentText("Say \"Hey Jarvis\"")
            .addAction(Notification.Action.Builder(null, "Stop", stop).build()).setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        else startForeground(1, n)
    }

    // ── wake word ──
    private fun listenWake() {
        if (!wake || live != null) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { Bus.log("No speech recognizer on this phone."); return }
        killSr()
        val r = SpeechRecognizer.createSpeechRecognizer(this)
        sr = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(t: Int, p: Bundle?) {}
            override fun onPartialResults(b: Bundle?) { check(b) }
            override fun onResults(b: Bundle?) { if (!check(b)) again(300) }
            override fun onError(e: Int) {
                if (e == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) { Bus.log("Microphone permission missing."); stopSelf(); return }
                again(if (e == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 1500 else 400)
            }
        })
        r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag()))
    }

    private fun again(ms: Long) { if (wake && live == null) handler.postDelayed({ listenWake() }, ms) }
    private fun again(ms: Int) = again(ms.toLong())

    private fun check(b: Bundle?): Boolean {
        val heard = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return false
        if (heard.none { h -> phrases.any { h.lowercase().contains(it) } }) return false
        killSr(); startLive(); return true
    }

    private fun killSr() { try { sr?.cancel(); sr?.destroy() } catch (e: Exception) { }; sr = null }

    // ── live ──
    private fun startLive() {
        if (live != null) return
        if (Store.key.isBlank()) { Bus.log("Set the API key first."); afterLive(); return }
        try { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80).startTone(ToneGenerator.TONE_PROP_ACK, 150) } catch (e: Exception) { }
        Hud.attach(this)
        live = LiveSession(scope) { _ -> handler.post { live = null; Hud.detach(this@VoiceService); afterLive() } }.also { it.start() }
    }

    private fun afterLive() { if (wake) handler.postDelayed({ listenWake() }, 600) else stopSelf() }

    override fun onDestroy() {
        inst = null; wake = false; killSr(); live?.stop("service stopped"); scope.cancel(); super.onDestroy()
    }
}

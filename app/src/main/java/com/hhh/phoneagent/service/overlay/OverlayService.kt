package com.hhh.phoneagent.service.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.hhh.phoneagent.domain.model.TurnSource
import com.hhh.phoneagent.domain.security.ApprovalDecision
import com.hhh.phoneagent.data.approval.ApprovalQueue
import com.hhh.phoneagent.speech.SpeechCoordinator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

/**
 * Shows the floating bubble and the approval card. The bubble starts a voice turn on tap and
 * can be dragged. The approval card appears only while ApprovalQueue has a pending request.
 */
@AndroidEntryPoint
class OverlayService : Service() {

    @Inject lateinit var coordinator: SpeechCoordinator
    @Inject lateinit var approvals: ApprovalQueue

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var jobs: List<Job> = emptyList()
    private lateinit var windowManager: WindowManager
    private lateinit var bubble: TextView
    private var approvalView: View? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!OverlayController.canDraw(this)) {
            Log.w(TAG, "Overlay permission missing")
            stopSelf()
            return START_NOT_STICKY
        }
        if (!startForegroundSafely()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (jobs.isEmpty()) {
            addBubble()
            jobs = listOf(
                scope.launch {
                    coordinator.state.collect { state -> bubble.applyState(state) }
                },
                scope.launch {
                    approvals.currentRequest.distinctUntilChanged().collect { request ->
                        if (request == null) {
                            removeApproval()
                        } else {
                            showApproval(request.id) { approved ->
                                approvals.resolve(
                                    request.id,
                                    if (approved) ApprovalDecision.APPROVED else ApprovalDecision.DENIED,
                                )
                            }
                        }
                    }
                },
            )
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        jobs.forEach { it.cancel() }
        removeApproval()
        if (::bubble.isInitialized && bubble.isAttachedToWindow) windowManager.removeView(bubble)
        scope.cancel()
        super.onDestroy()
    }

    private fun addBubble() {
        bubble = bubbleView(this)
        val params = overlayParams(
            width = dp(60),
            height = dp(60),
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16)
            y = dp(200)
        }
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        bubble.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (abs(dx) > TAP_SLOP || abs(dy) > TAP_SLOP) moved = true
                    params.x = startX + dx.toInt()
                    params.y = startY + dy.toInt()
                    windowManager.updateViewLayout(bubble, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) scope.launch { coordinator.runTurn(TurnSource.BUTTON) }
                    true
                }
                else -> false
            }
        }
        windowManager.addView(bubble, params)
    }

    private fun showApproval(requestId: String, onDecision: (Boolean) -> Unit) {
        removeApproval()
        val request = approvals.currentRequest.value ?: return
        if (request.id != requestId) return
        val card = approvalCard(this, request) { approved ->
            removeApproval()
            onDecision(approved)
        }
        val params = overlayParams(
            width = WindowManager.LayoutParams.MATCH_PARENT,
            height = WindowManager.LayoutParams.WRAP_CONTENT,
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        ).apply {
            gravity = Gravity.CENTER
            x = dp(16)
            y = 0
        }
        windowManager.addView(card, params)
        approvalView = card
    }

    private fun removeApproval() {
        approvalView?.let { view ->
            if (view.isAttachedToWindow) windowManager.removeView(view)
        }
        approvalView = null
    }

    private fun overlayParams(width: Int, height: Int, flags: Int) = WindowManager.LayoutParams(
        width,
        height,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        flags,
        PixelFormat.TRANSLUCENT,
    )

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun startForegroundSafely(): Boolean = try {
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
        true
    } catch (e: Exception) {
        Log.e(TAG, "Cannot start overlay foreground service", e)
        false
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Floating assistant is on")
            .setOngoing(true)
            .build()

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Floating assistant", NotificationManager.IMPORTANCE_LOW),
        )
    }

    private companion object {
        const val TAG = "OverlayService"
        const val CHANNEL_ID = "overlay"
        const val NOTIFICATION_ID = 1002
        const val TAP_SLOP = 12f
    }
}

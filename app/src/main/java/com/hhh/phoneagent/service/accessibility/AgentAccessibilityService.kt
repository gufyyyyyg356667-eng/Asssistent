package com.hhh.phoneagent.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.hhh.phoneagent.service.accessibility.eyes.ScreenEyes
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The single Accessibility Service in the app. Screen reading (Eyes), node actions and
 * gestures all go through the [ScreenController] it creates. Do not add a second service.
 */
@AndroidEntryPoint
class AgentAccessibilityService : AccessibilityService() {

    @Inject
    lateinit var bridge: AccessibilityBridge

    @Inject
    lateinit var eyes: ScreenEyes

    @Volatile
    private var controller: ScreenController? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        val created = ScreenController(this, eyes)
        controller = created
        bridge.attach(created)
        Log.i(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Intentionally empty in Phase 1. Event-driven caching arrives with the agent loop.
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        detach()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        detach()
        super.onDestroy()
    }

    private fun detach() {
        controller?.let { bridge.detach(it) }
        controller = null
    }

    private companion object {
        const val TAG = "AgentAccessibility"
    }
}

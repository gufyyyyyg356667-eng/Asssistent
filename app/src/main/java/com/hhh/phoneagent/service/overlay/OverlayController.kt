package com.hhh.phoneagent.service.overlay

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat

/** Starts and stops the overlay. Overlay permission is requested through Settings, never silently. */
object OverlayController {

    fun canDraw(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun permissionIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))

    fun start(context: Context): Boolean {
        if (!canDraw(context)) return false
        ContextCompat.startForegroundService(context, Intent(context, OverlayService::class.java))
        return true
    }

    fun stop(context: Context) {
        context.stopService(Intent(context, OverlayService::class.java))
    }
}

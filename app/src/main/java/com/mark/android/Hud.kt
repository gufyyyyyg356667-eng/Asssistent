package com.mark.android

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/** A small orb that floats over every app: blue = listening, amber = working, green = speaking, red = asking you. Never touchable. */
class OrbView(c: Context) : View(c) {
    private var state = "listening"
    private var t = 0f
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val anim = ValueAnimator.ofFloat(0f, 1f).apply { duration = 1400; repeatCount = ValueAnimator.INFINITE; addUpdateListener { t = it.animatedValue as Float; invalidate() } }
    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
    fun setState(s: String) { state = s; invalidate() }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); anim.start() }
    override fun onDetachedFromWindow() { anim.cancel(); super.onDetachedFromWindow() }
    override fun onDraw(c: Canvas) {
        val col = when (state) { "listening" -> 0xFF2196F3; "working" -> 0xFFFFC107; "speaking" -> 0xFF4CAF50; "confirm" -> 0xFFF44336; else -> 0xFF9E9E9E }.toInt()
        val cx = width / 2f; val cy = height / 2f; val base = width * 0.22f
        p.color = col; p.alpha = (90 * (1 - t)).toInt(); c.drawCircle(cx, cy, base + base * 0.9f * t, p)
        p.alpha = 255; c.drawCircle(cx, cy, base, p)
        p.color = Color.WHITE; p.alpha = 160; c.drawCircle(cx - base * 0.3f, cy - base * 0.3f, base * 0.25f, p)
    }
}

object Hud {
    private var view: OrbView? = null
    fun attach(c: Context) {
        if (!Store.hud || !Settings.canDrawOverlays(c) || view != null) return
        try {
            val v = OrbView(c)
            val lp = WindowManager.LayoutParams(200, 200, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.END; x = 12; y = 140 }
            c.getSystemService(WindowManager::class.java).addView(v, lp)
            view = v
            Bus.onState = { s -> v.post { v.setState(s) } }
        } catch (e: Exception) { Bus.log("Orb unavailable: ${e.message}") }
    }
    fun detach(c: Context) {
        Bus.onState = null
        try { view?.let { c.getSystemService(WindowManager::class.java).removeView(it) } } catch (e: Exception) { }
        view = null
    }
}

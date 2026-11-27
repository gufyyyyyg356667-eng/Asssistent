package com.hhh.phoneagent.service.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.hhh.phoneagent.domain.model.RiskLevel
import com.hhh.phoneagent.domain.model.VoiceState
import com.hhh.phoneagent.domain.security.ApprovalRequest

/** Programmatic views for the overlay. Kept free of Compose so they work inside a WindowManager window. */
internal fun Context.dp(value: Int): Int =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

internal fun bubbleView(context: Context): TextView = TextView(context).apply {
    text = "AI"
    setTextColor(Color.WHITE)
    textSize = 16f
    gravity = Gravity.CENTER
    background = ring(Color.parseColor("#3D5AFE"))
    layoutParams = LinearLayout.LayoutParams(context.dp(60), context.dp(60))
    elevation = context.dp(6).toFloat()
}

/** Bubble colour follows the voice state, so the user can see what the assistant is doing. */
internal fun colorFor(state: VoiceState): Int = when (state) {
    VoiceState.Idle -> Color.parseColor("#3D5AFE")
    VoiceState.WakeListening -> Color.parseColor("#2E7D32")
    is VoiceState.Listening -> Color.parseColor("#00C853")
    VoiceState.Thinking -> Color.parseColor("#FF8F00")
    VoiceState.Speaking -> Color.parseColor("#1565C0")
    is VoiceState.Error -> Color.parseColor("#C62828")
}

internal fun TextView.applyState(state: VoiceState) {
    background = ring(colorFor(state))
}

internal fun approvalCard(
    context: Context,
    request: ApprovalRequest,
    onDecision: (Boolean) -> Unit,
): View {
    val title = TextView(context).apply {
        text = "Approve this action?"
        setTextColor(Color.WHITE)
        textSize = 18f
    }
    val risk = TextView(context).apply {
        text = when (request.risk) {
            RiskLevel.HIGH_RISK -> "HIGH RISK"
            RiskLevel.MODERATE -> "Moderate"
            RiskLevel.SAFE -> "Safe"
        }
        setTextColor(if (request.risk == RiskLevel.HIGH_RISK) Color.parseColor("#FF5252") else Color.parseColor("#FFD54F"))
        textSize = 14f
    }
    val summary = TextView(context).apply {
        text = request.summary
        setTextColor(Color.parseColor("#E0E0E0"))
        textSize = 16f
        setPadding(0, context.dp(8), 0, context.dp(16))
    }
    val deny = Button(context).apply {
        text = "Deny"
        setOnClickListener { onDecision(false) }
    }
    val approve = Button(context).apply {
        text = "Approve"
        setOnClickListener { onDecision(true) }
    }
    val buttons = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.END
        addView(deny)
        addView(approve)
    }
    return LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(context.dp(20), context.dp(20), context.dp(20), context.dp(16))
        background = GradientDrawable().apply {
            setColor(Color.parseColor("#1E1E2E"))
            cornerRadius = context.dp(16).toFloat()
        }
        addView(title)
        addView(risk)
        addView(summary)
        addView(buttons)
    }
}

private fun ring(color: Int) = GradientDrawable().apply {
    shape = GradientDrawable.OVAL
    setColor(color)
    setStroke(4, Color.WHITE)
}

package com.mark.android

import android.app.Activity
import kotlinx.coroutines.CompletableDeferred

/** Shared state between the UI, the Live voice session, the confirm dialog and the service. */
object Bus {
    var listener: ((String) -> Unit)? = null
    var pending: CompletableDeferred<Boolean>? = null
    var dialog: Activity? = null
    @Volatile var running = false
    /** idle | listening | working | speaking | confirm : drives the floating orb. */
    var onState: ((String) -> Unit)? = null
    var state: String = "idle"
        set(v) { field = v; onState?.invoke(v) }
    @Volatile var photo: kotlinx.coroutines.CompletableDeferred<String?>? = null
    /** Set while a Live voice session is open: lets a phone task talk to the voice model. */
    @Volatile var liveNotify: ((String) -> Unit)? = null
    @Volatile var confirmAsked = 0L
    @Volatile var lastConfirmOk = 0L

    private val history = mutableListOf<String>()
    private val said = mutableListOf<Pair<Long, String>>()

    fun log(line: String) {
        synchronized(history) { history.add(line); if (history.size > 200) history.removeAt(0) }
        listener?.invoke(line)
    }
    fun all(): String = synchronized(history) { history.joinToString("\n") }

    /** What the USER said (from Live input transcription). Confirmations are judged on this, never on the model's say-so. */
    fun hear(t: String) { synchronized(said) { said.add(System.currentTimeMillis() to t); if (said.size > 80) said.removeAt(0) } }
    fun saidSince(ts: Long): String = synchronized(said) { said.filter { it.first >= ts }.joinToString(" ") { it.second } }

    val YES = Regex("(?i)\\b(yes|yeah|yep|sure|okay|ok|confirm|go ahead|do it|send it|proceed)\\b|হ্যাঁ|হ্যা|হাঁ|ঠিক আছে|ঠিকাছে|করো|পাঠাও|পাঠিয়ে দাও|কনফার্ম")
    val NO = Regex("(?i)\\b(no|nope|don't|do not|stop|cancel|wait|never)\\b|না|থামো|বাতিল|দরকার নেই")
}

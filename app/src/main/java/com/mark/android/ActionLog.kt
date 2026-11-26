package com.mark.android

import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What Mark did, line by line, kept 14 days in logs/. Typed text, messages and facts are never written, only their length. */
object ActionLog {
    private val hidden = setOf("text", "fact", "subject", "description", "template", "input", "message", "question", "persona")
    private fun file() = File(PhoneTools.base(), "logs/actions-${SimpleDateFormat("yyyyMMdd", Locale.ENGLISH).format(Date())}.log")
        .also { it.parentFile?.mkdirs() }

    fun clean(j: JSONObject): String = j.keys().asSequence().joinToString(" ") { k ->
        if (k in hidden) "$k=<${j.optString(k).length} chars>" else "$k=${j.opt(k)}"
    }.take(200)

    fun add(line: String) {
        try {
            file().appendText("${SimpleDateFormat("HH:mm:ss", Locale.ENGLISH).format(Date())} $line\n")
            if (System.currentTimeMillis() % 20 == 0L) File(PhoneTools.base(), "logs").listFiles()
                ?.filter { System.currentTimeMillis() - it.lastModified() > 14 * 86_400_000L }?.forEach { it.delete() }
        } catch (e: Exception) { }
    }

    fun tail(n: Int): String {
        val f = file()
        val lines = if (f.exists()) f.readLines() else emptyList()
        return lines.takeLast(n.coerceIn(1, 200)).joinToString("\n").ifBlank { "Nothing logged today yet." }
    }
}

/** Counts AI calls per day and enforces an optional daily limit, so a stuck loop cannot burn your quota. */
object Usage {
    private fun day() = SimpleDateFormat("yyyyMMdd", Locale.ENGLISH).format(Date())
    fun count(): Int = Store.prefs.getInt("use_${day()}", 0)

    fun beforeCall() {
        val cap = Store.dailyCap
        if (cap > 0 && count() >= cap) throw IllegalStateException("Daily AI call limit reached ($cap). Raise it in More settings.")
        Store.prefs.edit().putInt("use_${day()}", count() + 1).apply()
    }
    fun today(): String { val c = Store.dailyCap; return "AI calls today: ${count()}" + if (c > 0) " of $c allowed." else " (no daily limit set)." }
}

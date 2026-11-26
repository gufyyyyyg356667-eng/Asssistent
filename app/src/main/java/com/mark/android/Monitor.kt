package com.mark.android

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Only topics the user asked for. No finance, no uninvited tracking. */
object Monitor {
    suspend fun check(r: Routine) {
        val text = withContext(Dispatchers.IO) {
            Gemini.search("What is the single most important news from the past 24 hours about: ${r.text}? " +
                "Reply with one headline line, then one sentence. If there is nothing new, reply exactly NONE.").first
        }.trim()
        if (text.startsWith("NONE", true) || text.length < 12) return
        val head = text.lines().first().lowercase().filter { it.isLetterOrDigit() }.take(60)
        val key = "watch_${r.id}"
        if (Store.prefs.getString(key, "") == head) return          // same story as last time
        Store.prefs.edit().putString(key, head).apply()
        Notifier.show("News: ${r.text}", text)
        withContext(Dispatchers.Main) { AgentService.instance?.say("News about ${r.text}. $text") }
    }
}

/** Off by default. At most 3 a day, never within 2 hours of the last one, silence is the default answer. */
object Proactive {
    fun enabled() = Store.prefs.getBoolean("proactive", false)

    fun set(on: Boolean): String {
        Store.prefs.edit().putBoolean("proactive", on).apply()
        Routines.remove("proactive")
        if (on) Routines.add(Routine("proactive", "proactive", "", "interval", everyMin = 180))
        return if (on) "Proactive mode on: I may speak up at most 3 times a day, never in quiet hours." else "Proactive mode off."
    }

    suspend fun tick() {
        if (!enabled()) return
        val p = Store.prefs
        val day = SimpleDateFormat("yyyyMMdd", Locale.ENGLISH).format(Date())
        val count = if (p.getString("pro_day", "") == day) p.getInt("pro_count", 0) else 0
        if (count >= 3 || System.currentTimeMillis() - p.getLong("pro_last", 0) < 2 * 3600_000L) return
        val recent = p.getString("pro_recent", "")!!
        val ctx = """
Time: ${SimpleDateFormat("EEEE HH:mm", Locale.ENGLISH).format(Date())}
Phone: ${PhoneTools.status()}
Notifications waiting: ${NotifService.instance?.recent()?.lines()?.size ?: "unknown"}
Upcoming routines: ${Routines.all().filter { it.kind != "proactive" }.take(5).joinToString("; ") { Routines.describe(it) }.ifBlank { "none" }}
What you know about the user:
${Memory.block()}
Things you already said recently (never repeat or rephrase):
${recent.ifBlank { "(nothing)" }}
""".trimIndent()
        val raw = withContext(Dispatchers.IO) {
            Gemini.ask(Store.key, Store.textModel, "You are a considerate phone assistant deciding whether to say ONE short helpful thing right now.\n" +
                "Default to silence. Speak only if it clearly helps now. No finance, no uninvited tracking, no small talk.\n$ctx\n" +
                "Reply JSON only: {\"speak\":true|false,\"text\":\"<one or two sentences in the user's language>\"}")
        }
        val s = raw.indexOf('{'); val e = raw.lastIndexOf('}')
        val j = JSONObject(if (s >= 0 && e > s) raw.substring(s, e + 1) else "{}")
        val text = j.optString("text").trim()
        if (!j.optBoolean("speak") || text.isBlank()) return
        p.edit().putString("pro_day", day).putInt("pro_count", count + 1).putLong("pro_last", System.currentTimeMillis())
            .putString("pro_recent", (recent.lines() + text).takeLast(5).joinToString("\n")).apply()
        Notifier.show("Mark", text)
        withContext(Dispatchers.Main) { AgentService.instance?.say(text) }
    }
}

class BgWorker(c: Context, p: WorkerParameters) : CoroutineWorker(c, p) {
    override suspend fun doWork(): Result {
        val id = inputData.getString("id") ?: return Result.failure()
        val r = Routines.all().firstOrNull { it.id == id } ?: return Result.success()
        return try {
            when (r.kind) { "watch" -> Monitor.check(r); "proactive" -> Proactive.tick() }
            Result.success()
        } catch (e: Exception) { if (runAttemptCount < 2) Result.retry() else Result.failure() }
    }
}

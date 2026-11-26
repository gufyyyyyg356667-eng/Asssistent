package com.mark.android

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/** kind: "say" (notify + speak) or "do" (run a phone task). sched: once | daily | weekly | interval. */
data class Routine(
    val id: String, val kind: String, val text: String, val sched: String,
    val hour: Int = 0, val minute: Int = 0, val days: List<Int> = emptyList(),
    val everyMin: Int = 0, val atMillis: Long = 0, val enabled: Boolean = true
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("kind", kind).put("text", text).put("sched", sched)
        .put("hour", hour).put("minute", minute).put("days", JSONArray(days))
        .put("everyMin", everyMin).put("atMillis", atMillis).put("enabled", enabled)

    companion object {
        fun from(o: JSONObject) = Routine(
            o.getString("id"), o.getString("kind"), o.getString("text"), o.getString("sched"),
            o.optInt("hour"), o.optInt("minute"),
            o.optJSONArray("days")?.let { a -> (0 until a.length()).map { a.getInt(it) } } ?: emptyList(),
            o.optInt("everyMin"), o.optLong("atMillis"), o.optBoolean("enabled", true)
        )
    }
}

object Routines {
    private const val K = "routines"
    private val DAY = mapOf("mon" to Calendar.MONDAY, "tue" to Calendar.TUESDAY, "wed" to Calendar.WEDNESDAY,
        "thu" to Calendar.THURSDAY, "fri" to Calendar.FRIDAY, "sat" to Calendar.SATURDAY, "sun" to Calendar.SUNDAY)

    fun all(): List<Routine> = try {
        val a = JSONArray(Store.prefs.getString(K, "[]")); (0 until a.length()).map { Routine.from(a.getJSONObject(it)) }
    } catch (e: Exception) { emptyList() }

    private fun save(l: List<Routine>) {
        val a = JSONArray(); l.forEach { a.put(it.toJson()) }
        Store.prefs.edit().putString(K, a.toString()).apply()
    }

    fun add(r: Routine) { save(all() + r); schedule(r) }
    fun remove(id: String): Boolean {
        val l = all(); val hit = l.firstOrNull { it.id == id } ?: return false
        cancel(hit); save(l.filter { it.id != id }); return true
    }

    fun parseDays(s: String): List<Int> =
        s.lowercase().split(Regex("[,\\s]+")).mapNotNull { DAY[it.take(3)] }.distinct()

    fun describe(r: Routine): String {
        val hm = "%02d:%02d".format(r.hour, r.minute)
        val what = when (r.kind) { "do" -> "do \"${r.text}\""; "watch" -> "watch the news about \"${r.text}\""; "proactive" -> "proactive check"; else -> "say \"${r.text}\"" }
        val inv = DAY.entries.associate { it.value to it.key }
        return when (r.sched) {
            "once" -> "once at ${SimpleDateFormat("EEE d MMM HH:mm", Locale.getDefault()).format(Date(r.atMillis))}: $what"
            "daily" -> "daily at $hm: $what"
            "weekly" -> "weekly ${r.days.joinToString(",") { inv[it] ?: "?" }} at $hm: $what"
            else -> "every ${r.everyMin} min: $what"
        }
    }

    // ── time maths ──
    fun nextClock(h: Int, m: Int, days: List<Int>): Long {
        val now = System.currentTimeMillis()
        for (d in 0..7) {
            val c = Calendar.getInstance()
            c.add(Calendar.DAY_OF_YEAR, d)
            c.set(Calendar.HOUR_OF_DAY, h); c.set(Calendar.MINUTE, m); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
            if (c.timeInMillis > now && (days.isEmpty() || c.get(Calendar.DAY_OF_WEEK) in days)) return c.timeInMillis
        }
        return now + 24 * 3600_000L
    }

    fun nextTime(r: Routine): Long? {
        val now = System.currentTimeMillis()
        return when (r.sched) {
            "once" -> if (r.atMillis > now) r.atMillis else null
            "daily" -> nextClock(r.hour, r.minute, emptyList())
            "weekly" -> nextClock(r.hour, r.minute, r.days)
            else -> now + r.everyMin.coerceAtLeast(1) * 60_000L
        }
    }

    // ── quiet hours: nothing unprompted speaks inside the window; an exact "once" still fires ──
    fun setQuiet(startMin: Int, endMin: Int) { Store.prefs.edit().putInt("quiet_start", startMin).putInt("quiet_end", endMin).apply() }
    fun quietText(): String {
        val s = Store.prefs.getInt("quiet_start", -1); val e = Store.prefs.getInt("quiet_end", -1)
        return if (s < 0 || e < 0) "off" else "%02d:%02d-%02d:%02d".format(s / 60, s % 60, e / 60, e % 60)
    }
    fun inQuiet(): Boolean {
        val s = Store.prefs.getInt("quiet_start", -1); val e = Store.prefs.getInt("quiet_end", -1)
        if (s < 0 || e < 0 || s == e) return false
        val c = Calendar.getInstance(); val n = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        return if (s < e) n in s until e else (n >= s || n < e)
    }

    // ── alarms ──
    private fun pi(id: String): PendingIntent = PendingIntent.getBroadcast(
        Store.app, id.hashCode(), Intent(Store.app, RoutineReceiver::class.java).putExtra("id", id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun cancel(r: Routine) { Store.app.getSystemService(AlarmManager::class.java).cancel(pi(r.id)) }

    fun schedule(r: Routine) {
        if (!r.enabled) return
        val t = nextTime(r) ?: return
        val am = Store.app.getSystemService(AlarmManager::class.java)
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms())
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi(r.id))
        else
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi(r.id))
    }
    fun scheduleAll() { all().forEach { schedule(it) } }

    fun fire(r: Routine) {
        if (r.kind == "say") {
            Notifier.show("Mark", r.text)
            AgentService.instance?.say(r.text)
        } else {
            val svc = AgentService.instance
            if (svc == null) Notifier.show("Mark", "Could not run \"${r.text}\": the accessibility service is off.")
            else svc.start(r.text, true)
        }
    }
}

object Notifier {
    /** Ongoing notification with a STOP button while a phone task runs. */
    fun showTaskStop(text: String) {
        val nm = Store.app.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("task", "Mark is working", NotificationManager.IMPORTANCE_LOW))
        val pi = PendingIntent.getBroadcast(Store.app, 9, Intent(Store.app, KillReceiver::class.java), PendingIntent.FLAG_IMMUTABLE)
        nm.notify(78, Notification.Builder(Store.app, "task").setSmallIcon(android.R.drawable.ic_media_pause)
            .setContentTitle("Mark is working").setContentText(text).setOngoing(true)
            .addAction(Notification.Action.Builder(null, "STOP", pi).build()).build())
    }
    fun clearTaskStop() { Store.app.getSystemService(NotificationManager::class.java).cancel(78) }

    /** A notification whose tap opens Mark (a user tap is the one way Android lets the microphone service start from the background). */
    fun showOpen(title: String, text: String) {
        val nm = Store.app.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("mark", "Mark", NotificationManager.IMPORTANCE_HIGH))
        val pi = PendingIntent.getActivity(Store.app, 7,
            Intent(Store.app, MainActivity::class.java).putExtra("start_wake", true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        nm.notify(77, Notification.Builder(Store.app, "mark").setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(title).setContentText(text).setContentIntent(pi).setAutoCancel(true).build())
    }

    fun show(title: String, text: String) {
        val nm = Store.app.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("mark", "Mark", NotificationManager.IMPORTANCE_HIGH))
        val n = Notification.Builder(Store.app, "mark").setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title).setContentText(text).setStyle(Notification.BigTextStyle().bigText(text))
            .setAutoCancel(true).build()
        nm.notify(System.currentTimeMillis().toInt(), n)
    }
}

class RoutineReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val id = i.getStringExtra("id") ?: return
        val r = Routines.all().firstOrNull { it.id == id } ?: return
        if (!r.enabled) return
        val muted = Routines.inQuiet() && r.sched != "once"
        if (!muted) {
            if (r.kind == "watch" || r.kind == "proactive")
                WorkManager.getInstance(c).enqueue(OneTimeWorkRequestBuilder<BgWorker>().setInputData(workDataOf("id" to r.id)).build())
            else Routines.fire(r)
        }
        if (r.sched == "once") Routines.remove(r.id) else Routines.schedule(r)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action != Intent.ACTION_BOOT_COMPLETED) return
        Routines.scheduleAll()
        if (Store.prefs.getBoolean("wake", false)) {
            try { c.startForegroundService(Intent(c, VoiceService::class.java).setAction("WAKE_ON")) }
            catch (e: Exception) { Notifier.showOpen("Mark", "Tap to turn voice listening back on") }
        }
    }
}

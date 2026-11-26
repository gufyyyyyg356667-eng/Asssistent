package com.mark.android

import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.CalendarContract
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.File
import java.util.Calendar
import java.util.TimeZone
import java.util.UUID

/** Research, watching, nudges, camera, code, undo, plugins, audio output, video, updates. */
object ExtraTools {
    private val ctx get() = Store.app

    suspend fun call(name: String, a: JSONObject): String? = when (name) {
        "deep_research" -> Research.start(a.optString("topic"), a.optString("depth", "normal"))
        "research_status" -> Research.status()
        "research_cancel" -> Research.cancel()
        "watch_topic" -> watch(a)
        "list_watches" -> Routines.all().filter { it.kind == "watch" }
            .joinToString("\n") { "${it.id}: ${it.text} (daily at %02d:%02d)".format(it.hour, it.minute) }.ifBlank { "No watched topics." }
        "unwatch_topic" -> {
            val hit = Routines.all().filter { it.kind == "watch" && it.text.contains(a.optString("topic"), true) }
            hit.forEach { r -> Routines.remove(r.id); Undo.push("unwatch ${r.text}") { Routines.add(r); "Watching again." } }
            if (hit.isEmpty()) "No such watched topic." else "Stopped watching ${hit.size} topic(s)."
        }
        "proactive" -> Proactive.set(a.optString("mode").lowercase() == "on")
        "camera_vision" -> camera(a)
        "code_helper" -> codeHelper(a)
        "undo" -> if (a.optString("action") == "history") Undo.history() else Undo.last()
        "add_plugin" -> Plugins.add(Plugin(a.optString("name").lowercase(), a.optString("description"), a.optString("kind").lowercase(), a.optString("template")))
        "list_plugins" -> Plugins.catalog()
        "remove_plugin" -> if (Plugins.remove(a.optString("name"))) "Removed." else "No such plugin."
        "run_plugin" -> Plugins.run(a.optString("name"), a.optString("input"))
        "audio_output" -> audioOutput(a)
        "play_video" -> playVideo(a.optString("url"))
        "app_updates" -> appUpdates()
        "action_log" -> ActionLog.tail(a.optInt("count", 30))
        "usage_today" -> Usage.today()
        "clear_recipes" -> { Recipes.clear(); "Forgot the remembered task steps." }
        "calendar_list" -> calendarList(a)
        "calendar_add" -> calendarAdd(a)
        else -> null
    }

    private fun has(p: String) = ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    private fun calendarList(a: JSONObject): String {
        if (!has(android.Manifest.permission.READ_CALENDAR)) return "Allow the calendar permission for Mark first."
        val days = a.optInt("days", 7).coerceIn(1, 60)
        val now = System.currentTimeMillis()
        val b = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(b, now); ContentUris.appendId(b, now + days * 86_400_000L)
        val out = ArrayList<String>()
        val fmt = java.text.SimpleDateFormat("EEE d MMM HH:mm", java.util.Locale.getDefault())
        ctx.contentResolver.query(b.build(), arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.EVENT_LOCATION, CalendarContract.Instances.ALL_DAY), null, null,
            "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
            while (c.moveToNext() && out.size < 40) {
                val loc = c.getString(2).orEmpty()
                out.add("${fmt.format(java.util.Date(c.getLong(1)))}${if (c.getInt(3) == 1) " (all day)" else ""}: ${c.getString(0).orEmpty()}${if (loc.isNotBlank()) " @ $loc" else ""}")
            }
        }
        return out.joinToString("\n").ifBlank { "No events in the next $days days." }
    }

    private fun calendarAdd(a: JSONObject): String {
        if (!has(android.Manifest.permission.WRITE_CALENDAR) || !has(android.Manifest.permission.READ_CALENDAR)) return "Allow the calendar permission for Mark first."
        val m = Regex("^(\\d{4})-(\\d{2})-(\\d{2})[ T](\\d{1,2}):(\\d{2})$").find(a.optString("start").trim()) ?: return "start must look like 2026-12-24 18:30"
        val c = Calendar.getInstance().apply { clear(); set(m.groupValues[1].toInt(), m.groupValues[2].toInt() - 1, m.groupValues[3].toInt(), m.groupValues[4].toInt(), m.groupValues[5].toInt()) }
        val title = a.optString("title").trim(); if (title.isEmpty()) return "title is required"
        val cal = ctx.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, arrayOf(CalendarContract.Calendars._ID),
            "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= 500 AND ${CalendarContract.Calendars.VISIBLE} = 1", null, null)
            ?.use { if (it.moveToFirst()) it.getLong(0) else null } ?: return "No writable calendar found on this phone."
        val v = ContentValues().apply {
            put(CalendarContract.Events.DTSTART, c.timeInMillis)
            put(CalendarContract.Events.DTEND, c.timeInMillis + a.optInt("minutes", 60).coerceIn(5, 1440) * 60_000L)
            put(CalendarContract.Events.TITLE, title); put(CalendarContract.Events.CALENDAR_ID, cal)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            a.optString("location").takeIf { it.isNotBlank() }?.let { put(CalendarContract.Events.EVENT_LOCATION, it) }
        }
        val uri = ctx.contentResolver.insert(CalendarContract.Events.CONTENT_URI, v) ?: return "The calendar refused the event."
        val id = ContentUris.parseId(uri)
        ctx.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, ContentValues().apply {
            put(CalendarContract.Reminders.EVENT_ID, id); put(CalendarContract.Reminders.MINUTES, 10); put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT) })
        val there = ctx.contentResolver.query(uri, arrayOf(CalendarContract.Events.TITLE), null, null, null)?.use { it.moveToFirst() } ?: false
        Undo.push("calendar event \"${title.take(30)}\"") { ctx.contentResolver.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id), null, null); "Event removed." }
        return if (there) "Added \"$title\" (checked: it is in the calendar)." else "Added \"$title\" but could not read it back to verify."
    }

    private fun watch(a: JSONObject): String {
        val topic = a.optString("topic").trim(); if (topic.isEmpty()) return "topic is required"
        val (h, m) = Regex("^(\\d{1,2}):(\\d{2})$").find(a.optString("time", "10:00"))
            ?.let { it.groupValues[1].toInt() to it.groupValues[2].toInt() } ?: (10 to 0)
        val r = Routine(UUID.randomUUID().toString().take(6), "watch", topic, "daily", h.coerceIn(0, 23), m.coerceIn(0, 59))
        Routines.add(r); Undo.push("watch $topic") { Routines.remove(r.id); "Stopped watching." }
        return "Watching \"$topic\": a check every day at %02d:%02d, alerting only when something new appears.".format(r.hour, r.minute)
    }

    private suspend fun camera(a: JSONObject): String {
        if (ctx.checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return "Allow the camera permission for Mark first."
        val d = CompletableDeferred<String?>(); Bus.photo = d
        try { (AgentService.instance ?: ctx).startActivity(Intent(ctx, CameraActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("front", a.optString("camera") == "front")) }
        catch (e: Exception) { Bus.photo = null; return "Could not open the camera: ${e.message}" }
        val b64 = withTimeoutOrNull(20_000) { d.await() }; Bus.photo = null
        if (b64 == null) return "Could not take a photo (the camera is busy or blocked)."
        return withContext(Dispatchers.IO) { Gemini.visionPhoto(a.optString("question").ifBlank { "Describe what you see." }, b64) }
    }

    private fun strip(s: String) = s.trim().removePrefix("```").let { if (it.contains('\n') && it.lines().first().length < 15) it.substringAfter('\n') else it }.removeSuffix("```").trim()

    private suspend fun codeHelper(a: JSONObject): String = withContext(Dispatchers.IO) {
        val dir = File(PhoneTools.base(), "code").apply { mkdirs() }
        val action = a.optString("action"); val path = a.optString("path"); val desc = a.optString("description")
        if (action == "list") return@withContext dir.walkTopDown().filter { it.isFile }.joinToString("\n") { it.relativeTo(dir).path }.ifBlank { "No code files yet." }
        val f = File(dir, path).canonicalFile
        if (path.isBlank() || !f.path.startsWith(dir.canonicalPath)) return@withContext "Give a file name inside the code folder."
        when (action) {
            "write" -> {
                val code = strip(Gemini.plain("Write the complete file $path. Requirement: $desc\nReply with ONLY the code: no explanation, no markdown fences."))
                PhoneTools.backup(f, "write"); f.parentFile?.mkdirs(); f.writeText(code)
                "Wrote ${code.lines().size} lines to code/$path. It was not run: a phone has no general code runner."
            }
            "explain" -> if (f.isFile) Gemini.plain("Explain this code simply and point out any bugs.\n\n${f.readText().take(50_000)}") else "No such file."
            "fix" -> {
                if (!f.isFile) return@withContext "No such file."
                val code = strip(Gemini.plain("Fix this file. Problem: $desc\nReply with ONLY the complete corrected file, no fences.\n\n${f.readText().take(50_000)}"))
                PhoneTools.backup(f, "fix"); f.writeText(code)
                "Rewrote code/$path (${code.lines().size} lines). Not run. Undo brings back the old version."
            }
            else -> "Unknown action."
        }
    }

    @SuppressLint("NewApi")
    private fun audioOutput(a: JSONObject): String {
        val am = ctx.getSystemService(AudioManager::class.java)
        val device = a.optString("device")
        if (Build.VERSION.SDK_INT < 31) return when (device) {
            "speaker" -> { am.mode = AudioManager.MODE_IN_COMMUNICATION; am.isSpeakerphoneOn = true; "Speaker on." }
            "earpiece", "headset", "bluetooth" -> { am.isSpeakerphoneOn = false; "Speaker off; this Android version picks headset, Bluetooth or earpiece by itself." }
            else -> "Unknown device."
        }
        val names = mapOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER to "speaker", AudioDeviceInfo.TYPE_BUILTIN_EARPIECE to "earpiece",
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO to "bluetooth", AudioDeviceInfo.TYPE_BLE_HEADSET to "bluetooth",
            AudioDeviceInfo.TYPE_WIRED_HEADSET to "headset", AudioDeviceInfo.TYPE_WIRED_HEADPHONES to "headset", AudioDeviceInfo.TYPE_USB_HEADSET to "headset")
        val devs = am.availableCommunicationDevices
        fun label(d: AudioDeviceInfo) = "${names[d.type] ?: "type ${d.type}"} (${d.productName})"
        if (a.optString("action") == "list") return devs.joinToString("\n") { label(it) } + "\nCurrent: ${am.communicationDevice?.let { label(it) }}"
        val d = devs.firstOrNull { names[it.type] == device } ?: return "No $device output is available right now."
        return if (am.setCommunicationDevice(d)) "Sound now goes to ${label(d)}." else "Android refused to switch."
    }

    private fun playVideo(url: String): String {
        if (url.isBlank()) return "url is required"
        val u = if (url.startsWith("http")) url else "https://$url"
        val i = Intent(Intent.ACTION_VIEW)
        if (u.contains("youtu")) i.data = Uri.parse(u) else i.setDataAndType(Uri.parse(u), "video/*")
        return PhoneTools.launch(i, "the video")
    }

    private fun appUpdates(): String {
        val i = ctx.packageManager.getLaunchIntentForPackage("com.android.vending") ?: return "The Play Store is not installed."
        return PhoneTools.launch(i, "the Play Store") + " To update everything, use phone_task: profile picture, Manage apps and device, Update all."
    }
}

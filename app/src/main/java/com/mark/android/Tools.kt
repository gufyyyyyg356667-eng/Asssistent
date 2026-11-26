package com.mark.android

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object Tools {
    private const val S = "STRING"
    private const val I = "INTEGER"
    class Spec(val name: String, val desc: String, val props: List<Triple<String, String, String>> = emptyList(),
               val req: List<String> = emptyList(), val loop: Boolean = true)
    private fun p(n: String, t: String, d: String) = Triple(n, t, d)

    val specs: List<Spec> = listOf(
        Spec("phone_task", "Hand the phone to an autonomous operator for ANY job, small or big: it sees the screen and taps, swipes, types and scrolls across apps until finished (up to dozens of steps). It runs in the background: this call returns at once, you keep talking with the user, and you are told when it finishes or needs a yes/no. Use a direct tool instead when one fits.",
            listOf(p("goal", S, "What to achieve, in plain words, with every detail the user gave")), listOf("goal"), loop = false),
        // memory & routines
        Spec("remember", "Store a durable fact the user told you.", listOf(p("fact", S, "The fact")), listOf("fact")),
        Spec("forget", "Delete stored facts containing this text.", listOf(p("match", S, "Text to match")), listOf("match")),
        Spec("recall", "Search stored facts.", listOf(p("query", S, "What to look for")), listOf("query")),
        Spec("add_routine", "Create a reminder (kind=say) or an automatic phone task (kind=do).", listOf(
            p("kind", S, "say or do"), p("text", S, "What to say, or the task to do"),
            p("when", S, "once, daily, weekly or interval"), p("time", S, "HH:MM, 24-hour"),
            p("in_minutes", I, "For once: minutes from now"), p("days", S, "For weekly: mon,tue,..."),
            p("every_minutes", I, "For interval")), listOf("kind", "text", "when")),
        Spec("list_routines", "List all routines and the quiet-hours window."),
        Spec("remove_routine", "Delete a routine.", listOf(p("id", S, "Routine id")), listOf("id")),
        Spec("set_quiet_hours", "Window in which nothing unprompted speaks. start=off disables it.",
            listOf(p("start", S, "HH:MM or off"), p("end", S, "HH:MM or off")), listOf("start", "end")),
        // direct phone tools (the Android counterparts of Mark LVII's desktop tools)
        Spec("web_search", "Search the web for ANY current fact, news, price or topic. Returns a grounded answer.",
            listOf(p("query", S, "What to look up")), listOf("query")),
        Spec("weather_report", "Current weather and 3-day forecast for a city.", listOf(p("city", S, "City name")), listOf("city")),
        Spec("send_message", "Prepare a message in SMS, WhatsApp, Telegram or email. It opens the composer filled in; it does NOT send. Ask the user to confirm, then use phone_task to press Send.",
            listOf(p("app", S, "sms, whatsapp, telegram or email"), p("to", S, "Contact name, phone number or email"),
                p("text", S, "Message body"), p("subject", S, "Email subject (optional)")), listOf("app", "to", "text")),
        Spec("phone_call", "Open the dialer with a contact or number filled in (the user presses call).", listOf(p("to", S, "Contact name or number")), listOf("to")),
        Spec("phone_settings", "Phone controls. action: volume_up, volume_down, set_volume, mute, unmute, brightness, media_play_pause, media_next, media_previous, flashlight_on, flashlight_off, lock_screen, screenshot, notifications, quick_settings, power_menu, open_wifi, open_bluetooth, open_airplane, open_location, open_display, open_sound, open_battery_saver. Wi-Fi, Bluetooth and airplane mode cannot be flipped directly by Android: the open_* actions show the settings page, then use phone_task to flip the switch after the user confirms.",
            listOf(p("action", S, "One of the actions above"), p("value", I, "0-100 for set_volume and brightness")), listOf("action")),
        Spec("screen_vision", "Look at what is on the phone screen right now and answer a question about it.", listOf(p("question", S, "What to find out")), listOf("question")),
        Spec("system_status", "Battery, storage, memory, network and time."),
        Spec("youtube", "YouTube. action: search (opens results), play (opens results for a title), summarize (needs url of a public video).",
            listOf(p("action", S, "search, play or summarize"), p("query", S, "Search text or title"), p("url", S, "Video URL for summarize")), listOf("action")),
        Spec("open_url", "Open a web page in the browser.", listOf(p("url", S, "Address")), listOf("url")),
        Spec("flight_finder", "Open Google Flights for a route and date; then read the options with screen_vision.",
            listOf(p("origin", S, "From"), p("destination", S, "To"), p("date", S, "Date, e.g. 2026-12-24")), listOf("origin", "destination", "date")),
        Spec("files", "Files in the Mark folder on this phone. action: list, read, write, append, delete, find.",
            listOf(p("action", S, "list, read, write, append, delete or find"), p("path", S, "Relative path or search text"),
                p("text", S, "Content for write/append")), listOf("action")),
        Spec("set_alarm", "Set a clock alarm.", listOf(p("hour", I, "0-23"), p("minute", I, "0-59"), p("label", S, "Label")), listOf("hour", "minute")),
        Spec("set_timer", "Start a countdown timer.", listOf(p("seconds", I, "Duration in seconds"), p("label", S, "Label")), listOf("seconds")),
        Spec("phone_act", "Do ONE exact action on the phone right now and get the new screen back, e.g. 'scroll down', 'go back', 'tap the 3rd item'. action: tap, long_press, double_tap, swipe, scroll, drag, pinch, type, clear_text, press_enter, select_all, copy, paste, cut, back, home, recents, notifications, quick_settings, split_screen, lock_screen, open_app, find_tap. Element ids come from read_screen; x,y use a 0-1000 grid.",
            listOf(p("action", S, "One of the actions above"), p("id", I, "Element id from read_screen"), p("x", I, "0-1000"), p("y", I, "0-1000"),
                p("x2", I, "0-1000 (drag/swipe end)"), p("y2", I, "0-1000 (drag/swipe end)"), p("text", S, "Text to type"),
                p("direction", S, "up, down, left, right, in, out"), p("app", S, "App name for open_app"),
                p("target", S, "What to find and tap, for find_tap")), listOf("action"), loop = false),
        Spec("read_screen", "List what is on the phone screen right now, with element ids and positions.", loop = false),
        Spec("stop_task", "Stop the running phone task immediately.", loop = false),
        Spec("resume_task", "Continue the last phone task that was interrupted.", loop = false),
        Spec("ask_confirm", "Ask the user a yes/no question about an irreversible phone action. The phone speaks the question, listens, and answers from what the user actually says. Returns confirmed or denied.",
            listOf(p("message", S, "The question, short")), listOf("message"), loop = false),
        Spec("read_notifications", "Read the user's recent notifications (needs notification access)."),
        Spec("dismiss_notifications", "Clear all notifications."),
        Spec("call_control", "Answer or end the current phone call.", listOf(p("action", S, "answer or end")), listOf("action")),
        // research, watching, nudges
        Spec("deep_research", "Investigate a topic thoroughly in the background: plans questions, searches the web for each, cross-checks doubtful claims, writes a report with sources into the Mark folder. Survives restarts. Returns at once; you are told when it is ready.",
            listOf(p("topic", S, "What to research"), p("depth", S, "quick, normal or deep")), listOf("topic")),
        Spec("research_status", "Status of research jobs and where the reports are saved."),
        Spec("research_cancel", "Cancel running research."),
        Spec("watch_topic", "Check the news about a topic every day and alert only when something new appears.",
            listOf(p("topic", S, "Topic"), p("time", S, "HH:MM, default 10:00")), listOf("topic")),
        Spec("list_watches", "List watched topics."),
        Spec("unwatch_topic", "Stop watching a topic.", listOf(p("topic", S, "Topic or part of it")), listOf("topic")),
        Spec("proactive", "Turn proactive mode on or off: rare, useful nudges (max 3 a day, never in quiet hours).", listOf(p("mode", S, "on or off")), listOf("mode")),
        // senses, code, undo, plugins, audio, media
        Spec("camera_vision", "Take a photo with the phone camera right now and answer a question about it (what is this, read this label, what is in front of me).",
            listOf(p("question", S, "What to find out"), p("camera", S, "back or front")), listOf("question")),
        Spec("code_helper", "Write, explain or fix code files in the Mark folder (code/...). It cannot run code on a phone. action: write, explain, fix, list.",
            listOf(p("action", S, "write, explain, fix or list"), p("path", S, "File name, e.g. todo.py"), p("description", S, "What the code should do, or what is wrong")), listOf("action")),
        Spec("undo", "Undo the user's last reversible change (volume, brightness, files, memory, routines, quiet hours, plugins). action: last or history.",
            listOf(p("action", S, "last or history"))),
        Spec("add_plugin", "Create a new tool of the user's own from a recipe. kind: task (phone task), url (open an address) or search. Put {input} where the value goes. Only when the user asks for it.",
            listOf(p("name", S, "lowercase_name"), p("description", S, "What it does"), p("kind", S, "task, url or search"), p("template", S, "Recipe with {input}")),
            listOf("name", "description", "kind", "template")),
        Spec("list_plugins", "List the user's plugins."),
        Spec("remove_plugin", "Delete a plugin.", listOf(p("name", S, "Plugin name")), listOf("name")),
        Spec("run_plugin", "Run one of the user's plugins.", listOf(p("name", S, "Plugin name"), p("input", S, "Value for {input}")), listOf("name")),
        Spec("audio_output", "Choose where sound comes out. action: list or set; device: speaker, earpiece, bluetooth, headset.",
            listOf(p("action", S, "list or set"), p("device", S, "speaker, earpiece, bluetooth or headset")), listOf("action")),
        Spec("play_video", "Play a video from a link (YouTube or a direct video address).", listOf(p("url", S, "Video link")), listOf("url")),
        Spec("app_updates", "Open the Play Store so apps can be updated (then use phone_task to press Update all)."),
        // trust & calendar
        Spec("action_log", "Show what Mark did on the phone recently (typed text is never logged).", listOf(p("count", I, "How many lines, default 30"))),
        Spec("usage_today", "How many AI calls were made today and the daily limit."),
        Spec("clear_recipes", "Forget the remembered ways of doing tasks."),
        Spec("calendar_list", "List calendar events for the next days.", listOf(p("days", I, "How many days, default 7"))),
        Spec("calendar_add", "Add an event to the calendar (a reminder 10 minutes before). start is local time 'YYYY-MM-DD HH:MM'.",
            listOf(p("title", S, "Title"), p("start", S, "YYYY-MM-DD HH:MM"), p("minutes", I, "Length, default 60"), p("location", S, "Place")), listOf("title", "start"))
    )

    val NON_PHONE: Set<String> = specs.filter { it.loop }.map { it.name }.toSet()

    /** Declarations for the Live session. */
    fun declarations(): JSONArray {
        val out = JSONArray()
        for (s in specs) {
            val o = JSONObject().put("name", s.name).put("description", s.desc)
            if (s.props.isNotEmpty()) {
                val pr = JSONObject()
                s.props.forEach { (k, t, d) -> pr.put(k, JSONObject().put("type", t).put("description", d)) }
                o.put("parameters", JSONObject().put("type", "OBJECT").put("properties", pr).put("required", JSONArray(s.req)))
            }
            out.put(o)
        }
        return out
    }

    /** The same registry as action lines for the typed agent's prompt. */
    fun promptList(): String = specs.filter { it.loop }.joinToString("\n") { s ->
        val args = s.props.joinToString("") { (k, t, _) -> ",\"$k\":${if (t == I) "N" else "\"...\""}" }
        "{\"action\":\"${s.name}\"$args}  // ${s.desc.take(90)}"
    }

    suspend fun call(name: String, a: JSONObject, speak: Boolean): String {
        if (name != "phone_act") ActionLog.add("tool $name ${ActionLog.clean(a)}")
        return when (name) {
            "phone_task" -> {
                val svc = AgentService.instance ?: return "The accessibility service is not enabled, so I cannot operate the phone."
                if (Bus.liveNotify != null) {            // voice session: run in the background, stay conversational
                    if (Bus.running) return "A phone task is already running. Use stop_task to cancel it first."
                    withContext(Dispatchers.Main) { svc.start(a.optString("goal"), false) }
                    "Started. Tell the user you are on it. You will be told when it finishes; the user can keep talking and can ask you to stop."
                } else withContext(Dispatchers.Main) { svc.runTask(a.optString("goal"), speak) }
            }
            "phone_act" -> {
                val svc = AgentService.instance ?: return "The accessibility service is not enabled."
                withContext(Dispatchers.Main) { svc.actAndReport(a) }
            }
            "read_screen" -> {
                val svc = AgentService.instance ?: return "The accessibility service is not enabled."
                withContext(Dispatchers.Main) { svc.snapshot() }
            }
            "stop_task" -> if (Bus.running) { withContext(Dispatchers.Main) { AgentService.instance?.stop() }; "Stopped." } else "No task is running."
            "resume_task" -> {
                val svc = AgentService.instance ?: return "The accessibility service is not enabled."
                val t = TaskState.resumeText() ?: return "There is no interrupted task to resume."
                if (Bus.running) return "A task is already running."
                withContext(Dispatchers.Main) { svc.start(t, Bus.liveNotify == null) }
                "Resuming the last task."
            }
            "ask_confirm" -> {
                val svc = AgentService.instance ?: return "The accessibility service is not enabled."
                if (withContext(Dispatchers.Main) { svc.askConfirm(a.optString("message")) }) "confirmed" else "denied"
            }
            "remember" -> { val f = a.optString("fact"); val r = Memory.add(f); if (r == "saved") Undo.push("remembered \"${f.take(30)}\"") { Memory.removeExact(f.trim()); "Forgotten again." }; r }
            "forget" -> { val before = Memory.all(); val n = Memory.forget(a.optString("match")); val gone = before - Memory.all().toSet()
                if (gone.isNotEmpty()) Undo.push("forgot ${gone.size} fact(s)") { gone.forEach { Memory.add(it) }; "Facts restored." }; "forgot $n item(s)" }
            "recall" -> Memory.recall(a.optString("query")).ifEmpty { listOf("nothing found") }.joinToString("\n")
            "add_routine" -> addRoutine(a)
            "list_routines" -> Routines.all().ifEmpty { return "no routines. quiet hours: ${Routines.quietText()}" }
                .joinToString("\n") { "${it.id}: ${Routines.describe(it)}" } + "\nquiet hours: ${Routines.quietText()}"
            "remove_routine" -> { val r0 = Routines.all().firstOrNull { it.id == a.optString("id") }
                if (Routines.remove(a.optString("id"))) { r0?.let { rr -> Undo.push("removed routine") { Routines.add(rr); "Routine restored." } }; "removed" } else "no routine with that id" }
            "set_quiet_hours" -> setQuiet(a)
            else -> try { PhoneTools.call(name, a) ?: ExtraTools.call(name, a) ?: "unknown tool $name" } catch (e: Exception) { "$name failed: ${e.message}" }
        }
    }

    private fun parseTime(s: String): Pair<Int, Int>? {
        val m = Regex("^\\s*(\\d{1,2}):(\\d{2})\\s*$").find(s) ?: return null
        val h = m.groupValues[1].toInt(); val mi = m.groupValues[2].toInt()
        return if (h in 0..23 && mi in 0..59) Pair(h, mi) else null
    }

    private fun quietUndo() {
        val ps = Store.prefs.getInt("quiet_start", -1); val pe = Store.prefs.getInt("quiet_end", -1)
        Undo.push("quiet hours") { Routines.setQuiet(ps, pe); "Quiet hours back to ${Routines.quietText()}." }
    }

    private fun setQuiet(a: JSONObject): String {
        if (a.optString("start").lowercase() == "off" || a.optString("end").lowercase() == "off") {
            quietUndo(); Routines.setQuiet(-1, -1); return "quiet hours off"
        }
        val s = parseTime(a.optString("start")) ?: return "start must be HH:MM"
        val e = parseTime(a.optString("end")) ?: return "end must be HH:MM"
        quietUndo(); Routines.setQuiet(s.first * 60 + s.second, e.first * 60 + e.second)
        return "quiet hours ${Routines.quietText()}"
    }

    private fun addRoutine(a: JSONObject): String {
        val kind = if (a.optString("kind") == "do") "do" else "say"
        val text = a.optString("text").trim()
        if (text.isEmpty()) return "text is required"
        val sched = a.optString("when", "once").lowercase()
        val (h, m) = parseTime(a.optString("time")) ?: Pair(-1, -1)
        val id = UUID.randomUUID().toString().take(6)
        val r = when (sched) {
            "once" -> {
                val at = when {
                    a.has("in_minutes") -> System.currentTimeMillis() + a.optInt("in_minutes") * 60_000L
                    h >= 0 -> Routines.nextClock(h, m, emptyList())
                    else -> return "need in_minutes or time"
                }
                Routine(id, kind, text, "once", atMillis = at)
            }
            "daily" -> { if (h < 0) return "time (HH:MM) is required"; Routine(id, kind, text, "daily", h, m) }
            "weekly" -> {
                if (h < 0) return "time (HH:MM) is required"
                val d = Routines.parseDays(a.optString("days")); if (d.isEmpty()) return "days is required (e.g. mon,fri)"
                Routine(id, kind, text, "weekly", h, m, d)
            }
            "interval" -> {
                val e = a.optInt("every_minutes"); if (e < 1) return "every_minutes is required"
                Routine(id, kind, text, "interval", everyMin = e)
            }
            else -> return "unknown schedule '$sched'"
        }
        Routines.add(r)
        Undo.push("routine ${r.id}") { Routines.remove(r.id); "Routine removed." }
        return "added ${r.id}: ${Routines.describe(r)}"
    }
}

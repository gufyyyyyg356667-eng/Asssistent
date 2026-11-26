package com.mark.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Deep research as a background job: plan -> search each question -> cross-check doubtful claims -> write a report from the findings only.
 * Every finding is saved as it arrives, so a restart or a quota wall resumes where it stopped.
 */
object Research {
    private val jobs = ConcurrentHashMap<String, Job>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private fun dir() = File(Store.app.filesDir, "research").apply { mkdirs() }
    private fun load(id: String): JSONObject? = try { JSONObject(File(dir(), "$id.json").readText()) } catch (e: Exception) { null }
    private fun save(o: JSONObject) { o.put("updated", System.currentTimeMillis()); File(dir(), "${o.getString("id")}.json").writeText(o.toString()) }
    private fun all(): List<JSONObject> =
        dir().listFiles()?.mapNotNull { load(it.nameWithoutExtension) }?.sortedByDescending { it.optLong("updated") } ?: emptyList()
    private fun setStatus(id: String, s: String) { load(id)?.let { it.put("status", s); save(it) } }

    fun anyActive() = jobs.values.any { it.isActive }

    fun start(topic: String, depth: String): String {
        if (topic.isBlank()) return "topic is required"
        val id = UUID.randomUUID().toString().take(6)
        val d = if (depth in setOf("quick", "normal", "deep")) depth else "normal"
        save(JSONObject().put("id", id).put("topic", topic).put("depth", d).put("status", "running").put("stage", "plan"))
        launch(id, true)
        return "Research $id started ($d). It runs in the background and survives restarts. You will be told when the report is ready."
    }

    fun resumeAll() { all().filter { it.optString("status") in setOf("running", "waiting") }.forEach { launch(it.getString("id"), true) } }

    fun launch(id: String, startService: Boolean) {
        if (jobs[id]?.isActive == true) return
        if (startService) try { ContextCompat.startForegroundService(Store.app, Intent(Store.app, ResearchService::class.java)) } catch (e: Exception) { }
        jobs[id] = scope.launch {
            try { run(id) }
            catch (e: CancellationException) { }
            catch (e: Exception) { fail(id, e.message ?: "unknown error") }
            finally { ResearchService.maybeStop() }
        }
    }

    fun status(): String {
        val l = all().take(5)
        if (l.isEmpty()) return "No research jobs."
        return l.joinToString("\n") {
            val n = it.optJSONArray("questions")?.length() ?: 0; val d = it.optJSONArray("findings")?.length() ?: 0
            "${it.getString("id")} \"${it.getString("topic").take(50)}\": ${it.optString("status")}, stage ${it.optString("stage")}, $d/$n searches" +
                (if (it.has("report")) ", report: ${it.getString("report")}" else "") + (if (it.has("error")) ", error: ${it.getString("error")}" else "")
        }
    }

    fun cancel(): String {
        var n = 0
        jobs.forEach { (id, j) -> if (j.isActive) { j.cancel(); setStatus(id, "cancelled"); n++ } }
        return if (n == 0) "No research is running." else "Cancelled $n research job(s)."
    }

    private fun fail(id: String, msg: String) {
        load(id)?.let { it.put("status", "failed").put("error", msg.take(200)); save(it) }
        Notifier.show("Research failed", msg.take(120))
    }

    private suspend fun <T> quota(id: String, block: () -> T): T {
        var last: Exception? = null
        for (i in 0..5) {
            try { return block() } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                last = e
                if (e.message?.startsWith("HTTP 429") == true) { setStatus(id, "waiting"); delay(30_000L * (i + 1)); setStatus(id, "running") }
                else if (i < 2) delay(3000L) else throw e
            }
        }
        throw last!!
    }

    private fun json(raw: String): JSONObject {
        val s = raw.indexOf('{'); val e = raw.lastIndexOf('}')
        return JSONObject(if (s >= 0 && e > s) raw.substring(s, e + 1) else "{}")
    }

    private suspend fun run(id: String) {
        val o = load(id) ?: return
        val topic = o.getString("topic")
        val n = when (o.getString("depth")) { "quick" -> 3; "deep" -> 8; else -> 5 }

        if (!o.has("questions")) {
            val j = json(quota(id) { Gemini.plain("Plan web research on: $topic\nReturn ONLY JSON {\"questions\":[$n specific, distinct search questions that together cover the topic]}.") })
            val qs = j.optJSONArray("questions")?.takeIf { it.length() > 0 } ?: JSONArray().put(topic)
            o.put("questions", qs).put("findings", JSONArray()).put("stage", "search"); save(o)
        }
        val qs = o.getJSONArray("questions"); val findings = o.getJSONArray("findings")
        for (i in findings.length() until qs.length()) {
            val q = qs.getString(i)
            Bus.log("🔎 Research ${i + 1}/${qs.length()}: ${q.take(60)}")
            val (text, src) = quota(id) { Gemini.search(q) }
            findings.put(JSONObject().put("q", q).put("text", text).put("sources", JSONArray(src))); save(o)
        }

        if (!o.optBoolean("verified")) {
            o.put("stage", "verify"); save(o)
            val digest = (0 until findings.length()).joinToString("\n\n") { findings.getJSONObject(it).let { f -> "Q: ${f.getString("q")}\nA: ${f.getString("text")}" } }
            val j = json(quota(id) { Gemini.plain("Findings:\n$digest\n\nList up to 2 claims that conflict between findings or look doubtful, as ONLY JSON {\"check\":[\"search question\"]} (empty list if none).") })
            val checks = j.optJSONArray("check")
            if (checks != null) for (i in 0 until minOf(2, checks.length())) {
                val q = checks.getString(i)
                val (text, src) = quota(id) { Gemini.search(q) }
                findings.put(JSONObject().put("q", "VERIFY: $q").put("text", text).put("sources", JSONArray(src)))
            }
            o.put("verified", true); save(o)
        }

        o.put("stage", "write"); save(o)
        val all = (0 until findings.length()).joinToString("\n\n") { i ->
            findings.getJSONObject(i).let { f -> "## ${f.getString("q")}\n${f.getString("text")}\nSources: ${f.getJSONArray("sources").let { a -> (0 until a.length()).joinToString(", ") { a.getString(it) } }}" }
        }
        val report = quota(id) { Gemini.plain("Write a clear research report in Markdown on: $topic\nUse ONLY the findings below; add no facts from memory. " +
            "Structure: Summary, Key findings, Disagreements or uncertainty (mark anything uncertain), Sources (names as given). " +
            "Write in the language of the topic.\n\nFINDINGS:\n$all") }
        val slug = topic.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), "-").trim('-').take(40).ifBlank { "report" }
        val f = File(PhoneTools.base(), "reports/$slug-$id.md"); f.parentFile?.mkdirs(); f.writeText(report)
        val summary = quota(id) { Gemini.plain("Summarize this report in at most 3 short spoken sentences, same language:\n$report") }
        val rel = f.relativeTo(PhoneTools.base()).path
        o.put("status", "done").put("stage", "done").put("report", rel).put("summary", summary); save(o)

        Notifier.show("Research ready: ${topic.take(40)}", summary)
        val msg = "Research on \"$topic\" is ready. $summary The full report is saved as $rel."
        val live = Bus.liveNotify
        if (live != null) live("$msg Tell the user briefly.") else withContext(Dispatchers.Main) { AgentService.instance?.say(msg) }
    }
}

class ResearchService : Service() {
    companion object {
        var inst: ResearchService? = null
        fun maybeStop() { if (!Research.anyActive()) inst?.stopSelf() }
    }
    override fun onBind(i: Intent?): IBinder? = null
    override fun onStartCommand(i: Intent?, f: Int, s: Int): Int {
        inst = this
        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel("research", "Mark research", NotificationManager.IMPORTANCE_LOW))
            val n = Notification.Builder(this, "research").setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("Mark is researching").setContentText("Running in the background").setOngoing(true).build()
            if (Build.VERSION.SDK_INT >= 29) startForeground(2, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else startForeground(2, n)
        } catch (e: Exception) { }
        Research.resumeAll()
        return START_STICKY
    }
    override fun onDestroy() { inst = null; super.onDestroy() }
}

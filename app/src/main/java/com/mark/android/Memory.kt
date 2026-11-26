package com.mark.android

import org.json.JSONArray
import org.json.JSONObject

/** Facts the assistant keeps. Nothing is silently dropped; the prompt carries the latest 40, the rest is searchable. */
object Memory {
    private fun load(): JSONArray = try { JSONArray(Store.prefs.getString("memory", "[]")) } catch (e: Exception) { JSONArray() }
    private fun save(a: JSONArray) { Store.prefs.edit().putString("memory", a.toString()).apply() }

    fun all(): List<String> { val a = load(); return (0 until a.length()).map { a.getJSONObject(it).getString("t") } }

    fun add(fact: String): String {
        val t = fact.trim()
        if (t.isEmpty()) return "nothing to save"
        if (all().any { it.equals(t, ignoreCase = true) }) return "already known"
        val a = load(); a.put(JSONObject().put("t", t).put("ts", System.currentTimeMillis())); save(a)
        return "saved"
    }

    fun forget(match: String): Int {
        val m = match.trim().lowercase()
        if (m.isEmpty()) return 0
        val a = load(); val keep = JSONArray(); var removed = 0
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            if (o.getString("t").lowercase().contains(m)) removed++ else keep.put(o)
        }
        save(keep); return removed
    }

    fun removeExact(text: String) {
        val a = load(); val keep = JSONArray()
        for (i in 0 until a.length()) if (a.getJSONObject(i).getString("t") != text) keep.put(a.getJSONObject(i))
        save(keep)
    }

    fun recall(query: String): List<String> {
        val q = query.lowercase().trim()
        val words = q.split(Regex("[\\s,.;:!?]+")).filter { it.length > 2 }
        return all().map { f ->
            val l = f.lowercase()
            f to (words.count { l.contains(it) } + if (q.isNotEmpty() && l.contains(q)) 3 else 0)
        }.filter { it.second > 0 }.sortedByDescending { it.second }.take(8).map { it.first }
    }

    fun block(): String {
        val l = all().takeLast(40)
        return if (l.isEmpty()) "(nothing stored)" else l.joinToString("\n") { "- $it" }
    }

    fun clear() { save(JSONArray()) }
}

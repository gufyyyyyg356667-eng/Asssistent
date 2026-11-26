package com.mark.android

import org.json.JSONArray
import org.json.JSONObject

/** A finished task leaves a short note of the steps that worked; a similar task later starts with that hint. Stored on the phone only. */
object Recipes {
    private fun load(): JSONArray = try { JSONArray(Store.prefs.getString("recipes", "[]")) } catch (e: Exception) { JSONArray() }
    private fun words(s: String) = s.lowercase().split(Regex("[\\s,.;:!?]+")).filter { it.length > 3 }.toSet()

    fun save(goal: String, steps: List<String>) {
        val a = load(); val keep = JSONArray()
        for (i in maxOf(0, a.length() - 39) until a.length()) keep.put(a.getJSONObject(i))
        keep.put(JSONObject().put("goal", goal.take(120)).put("steps", JSONArray(steps.take(15))).put("ts", System.currentTimeMillis()))
        Store.prefs.edit().putString("recipes", keep.toString()).apply()
    }

    fun hint(goal: String): String {
        val w = words(goal); if (w.isEmpty()) return ""
        val a = load()
        return (0 until a.length()).map { a.getJSONObject(it) }
            .map { it to words(it.getString("goal")).count { x -> x in w } }.filter { it.second >= 2 }
            .sortedByDescending { it.second }.take(2)
            .joinToString("\n") { (o, _) ->
                val s = o.getJSONArray("steps"); "- ${o.getString("goal")}: " + (0 until s.length()).joinToString(" -> ") { s.getString(it) }
            }
    }
    fun clear() { Store.prefs.edit().remove("recipes").apply() }
}

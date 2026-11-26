package com.mark.android

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Mark LVII loads Python plugins. Android cannot load code safely, so a plugin here is a recipe the user dictates:
 *   task   -> a phone task with {input} filled in      ("open Spotify and play {input}")
 *   url    -> open an address with {input} filled in   ("https://maps.google.com/?q={input}")
 *   search -> a web search with {input} filled in
 */
data class Plugin(val name: String, val desc: String, val kind: String, val template: String)

object Plugins {
    private fun load(): List<Plugin> = try {
        val a = JSONArray(Store.prefs.getString("plugins", "[]"))
        (0 until a.length()).map { val o = a.getJSONObject(it); Plugin(o.getString("name"), o.getString("desc"), o.getString("kind"), o.getString("template")) }
    } catch (e: Exception) { emptyList() }
    private fun save(l: List<Plugin>) {
        val a = JSONArray(); l.forEach { a.put(JSONObject().put("name", it.name).put("desc", it.desc).put("kind", it.kind).put("template", it.template)) }
        Store.prefs.edit().putString("plugins", a.toString()).apply()
    }

    fun all() = load()
    fun catalog(): String = load().let { l -> if (l.isEmpty()) "(none)" else l.joinToString("\n") { "- ${it.name}: ${it.desc} (input = what to fill in)" } }

    fun add(p: Plugin): String {
        if (!Regex("^[a-z][a-z0-9_]{0,40}$").matches(p.name)) return "Name must be lowercase letters, digits and underscores."
        if (p.kind !in setOf("task", "url", "search")) return "kind must be task, url or search."
        if (p.template.isBlank()) return "template is required."
        val before = load()
        save(before.filter { it.name != p.name } + p)
        Undo.push("plugin ${p.name}") { save(before); "Plugin list restored." }
        return "Plugin ${p.name} saved."
    }
    fun remove(name: String): Boolean {
        val before = load(); if (before.none { it.name == name }) return false
        save(before.filter { it.name != name }); Undo.push("removed plugin $name") { save(before); "Plugin restored." }; return true
    }

    suspend fun run(name: String, input: String): String {
        val p = load().firstOrNull { it.name == name } ?: return "No plugin called $name."
        return when (p.kind) {
            "url" -> Tools.call("open_url", JSONObject().put("url", p.template.replace("{input}", URLEncoder.encode(input, "UTF-8"))), false)
            "search" -> Tools.call("web_search", JSONObject().put("query", p.template.replace("{input}", input)), false)
            else -> Tools.call("phone_task", JSONObject().put("goal", p.template.replace("{input}", input)), false)
        }
    }
}

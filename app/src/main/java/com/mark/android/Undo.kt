package com.mark.android

/** "Undo that": the last 20 reversible changes (volume, brightness, files, memory, routines, quiet hours, plugins). */
object Undo {
    private class Entry(val label: String, val fn: () -> String)
    private val stack = ArrayDeque<Entry>()

    fun push(label: String, fn: () -> String) {
        synchronized(stack) { stack.addLast(Entry(label, fn)); if (stack.size > 20) stack.removeFirst() }
    }
    fun last(): String {
        val e = synchronized(stack) { if (stack.isEmpty()) null else stack.removeLast() } ?: return "There is nothing to undo."
        return try { "Undone: ${e.label}. ${e.fn()}" } catch (ex: Exception) { "Could not undo ${e.label}: ${ex.message}" }
    }
    fun history(): String = synchronized(stack) {
        if (stack.isEmpty()) "Nothing to undo." else stack.reversed().joinToString("\n") { "- ${it.label}" }
    }
}

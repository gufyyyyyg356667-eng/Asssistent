package com.hhh.phoneagent.service.accessibility.eyes

/** Pure functions over snapshots, so they run on the JVM and are unit-testable. */
object ScreenSearch {

    fun find(root: ScreenNode?, query: String, limit: Int = 20): List<ScreenNode> {
        val needle = query.trim()
        if (root == null || needle.isEmpty() || limit <= 0) return emptyList()
        val out = mutableListOf<ScreenNode>()
        fun walk(node: ScreenNode) {
            if (out.size >= limit) return
            if (matches(node, needle)) out += node
            node.children.forEach(::walk)
        }
        walk(root)
        return out
    }

    private fun matches(node: ScreenNode, needle: String): Boolean =
        listOfNotNull(node.text, node.contentDescription, node.viewId)
            .any { it.contains(needle, ignoreCase = true) }
}

/** Compact text view of a snapshot, sized for an LLM prompt. */
object ScreenRenderer {

    fun toPromptText(snapshot: ScreenSnapshot, maxChars: Int = 6_000): String {
        val root = snapshot.root ?: return "(no content)"
        val sb = StringBuilder()
        val complete = render(root, 0, sb, maxChars)
        if (!complete || snapshot.truncated) sb.append("...(truncated)\n")
        return sb.toString()
    }

    /** Returns false once [maxChars] is reached, so the caller can stop early. */
    private fun render(node: ScreenNode, depth: Int, sb: StringBuilder, maxChars: Int): Boolean {
        if (sb.length >= maxChars) return false
        val label = node.text ?: node.contentDescription ?: node.viewId
        val flags = buildList {
            if (node.clickable) add("clickable")
            if (node.editable) add("editable")
            if (node.scrollable) add("scrollable")
            if (!node.enabled) add("disabled")
        }
        val interesting = !label.isNullOrBlank() || flags.isNotEmpty()
        if (interesting) {
            sb.append("  ".repeat(depth)).append('[').append(node.ref).append("] ")
            sb.append(node.className?.substringAfterLast('.') ?: "View")
            if (!label.isNullOrBlank()) sb.append(" \"").append(label.take(120)).append('"')
            if (flags.isNotEmpty()) sb.append(" (").append(flags.joinToString(", ")).append(')')
            sb.append('\n')
        }
        val childDepth = if (interesting) depth + 1 else depth
        for (child in node.children) {
            if (!render(child, childDepth, sb, maxChars)) return false
        }
        return true
    }
}

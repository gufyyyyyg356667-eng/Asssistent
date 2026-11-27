package com.hhh.phoneagent.service.accessibility.eyes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenSearchTest {

    private fun node(
        ref: String,
        text: String? = null,
        desc: String? = null,
        children: List<ScreenNode> = emptyList(),
    ) = ScreenNode(
        ref = ref,
        className = "android.widget.TextView",
        text = text,
        contentDescription = desc,
        viewId = null,
        bounds = NodeBounds(0, 0, 10, 10),
        clickable = false,
        longClickable = false,
        editable = false,
        scrollable = false,
        enabled = true,
        children = children,
    )

    @Test
    fun findsByTextAndDescriptionIgnoringCase() {
        val root = node("0", children = listOf(node("0.0", text = "Send Message"), node("0.1", desc = "Back button")))
        assertEquals(listOf("0.0"), ScreenSearch.find(root, "send").map { it.ref })
        assertEquals(listOf("0.1"), ScreenSearch.find(root, "BACK").map { it.ref })
    }

    @Test
    fun respectsLimitAndIgnoresBlankQuery() {
        val root = node("0", children = (0..5).map { node("0.$it", text = "item $it") })
        assertEquals(3, ScreenSearch.find(root, "item", limit = 3).size)
        assertTrue(ScreenSearch.find(root, "   ").isEmpty())
    }

    @Test
    fun rendererIncludesRefsAndInteractiveFlags() {
        val button = node("0.0", text = "OK").copy(clickable = true)
        val snapshot = ScreenSnapshot(
            packageName = "com.example",
            capturedAtMillis = 0L,
            root = node("0", children = listOf(button)),
            nodeCount = 2,
            truncated = false,
        )
        val text = ScreenRenderer.toPromptText(snapshot)
        assertTrue(text.contains("[0.0]"))
        assertTrue(text.contains("clickable"))
    }
}

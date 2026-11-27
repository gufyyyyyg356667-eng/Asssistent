package com.hhh.phoneagent.tools.ui

import com.hhh.phoneagent.domain.model.RiskLevel
import com.hhh.phoneagent.domain.security.RiskPolicy
import com.hhh.phoneagent.domain.tool.ToolResult
import com.hhh.phoneagent.domain.tool.booleanParam
import com.hhh.phoneagent.domain.tool.boolean
import com.hhh.phoneagent.domain.tool.int
import com.hhh.phoneagent.domain.tool.integerParam
import com.hhh.phoneagent.domain.tool.long
import com.hhh.phoneagent.domain.tool.objectSchema
import com.hhh.phoneagent.domain.tool.stringParam
import com.hhh.phoneagent.domain.tool.string
import com.hhh.phoneagent.domain.tool.toToolResult
import com.hhh.phoneagent.service.accessibility.AccessibilityBridge
import com.hhh.phoneagent.service.accessibility.GlobalAction
import com.hhh.phoneagent.tools.ScreenTool
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject

class ClickTool @Inject constructor(bridge: AccessibilityBridge) : ScreenTool(bridge) {
    override val name = "click"
    override val description = "Click an element by its ref from read_screen. Set long_press to true for a long press."
    override val parameters: JsonObject = objectSchema(
        properties = mapOf(
            "ref" to stringParam("Element ref, for example 0.2.1"),
            "long_press" to booleanParam("Long press instead of a normal click"),
        ),
        required = listOf("ref"),
    )

    override suspend fun assessRisk(args: JsonObject): RiskLevel =
        RiskPolicy.effectiveRisk(RiskLevel.MODERATE, args.string("ref")?.let { labelFor(it) })

    override suspend fun describeAction(args: JsonObject): String {
        val ref = args.string("ref") ?: return "Click an element"
        val verb = if (args.boolean("long_press") == true) "Long-press" else "Click"
        return "$verb \"${labelFor(ref) ?: ref}\""
    }

    override suspend fun execute(args: JsonObject): ToolResult {
        val ref = args.string("ref") ?: return ToolResult.error("Missing 'ref'")
        val longPress = args.boolean("long_press") == true
        return withController { controller ->
            val result = if (longPress) controller.longClick(ref) else controller.click(ref)
            result.toToolResult { if (longPress) "Long-pressed $ref" else "Clicked $ref" }
        }
    }
}

class TypeTextTool @Inject constructor(bridge: AccessibilityBridge) : ScreenTool(bridge) {
    override val name = "type_text"
    override val description = "Replace the text in an editable element identified by its ref."
    override val parameters: JsonObject = objectSchema(
        properties = mapOf(
            "ref" to stringParam("Ref of an editable element"),
            "text" to stringParam("Text to enter"),
        ),
        required = listOf("ref", "text"),
    )

    override suspend fun assessRisk(args: JsonObject): RiskLevel =
        RiskPolicy.effectiveRisk(RiskLevel.MODERATE, args.string("ref")?.let { labelFor(it) })

    override suspend fun describeAction(args: JsonObject): String {
        val ref = args.string("ref").orEmpty()
        val text = args.string("text").orEmpty().take(60)
        return "Type \"$text\" into \"${labelFor(ref) ?: ref}\""
    }

    override suspend fun execute(args: JsonObject): ToolResult {
        val ref = args.string("ref") ?: return ToolResult.error("Missing 'ref'")
        val text = args.string("text") ?: return ToolResult.error("Missing 'text'")
        return withController { controller ->
            controller.setText(ref, text).toToolResult { "Typed text into $ref" }
        }
    }
}

class ScrollTool @Inject constructor(bridge: AccessibilityBridge) : ScreenTool(bridge) {
    override val name = "scroll"
    override val description = "Scroll a scrollable element by its ref. Direction is forward (down) or backward (up)."
    override val parameters: JsonObject = objectSchema(
        properties = mapOf(
            "ref" to stringParam("Ref of a scrollable element"),
            "direction" to stringParam("Scroll direction", listOf("forward", "backward")),
        ),
        required = listOf("ref"),
    )

    override suspend fun assessRisk(args: JsonObject): RiskLevel = RiskLevel.SAFE

    override suspend fun describeAction(args: JsonObject): String =
        "Scroll ${args.string("direction") ?: "forward"}"

    override suspend fun execute(args: JsonObject): ToolResult {
        val ref = args.string("ref") ?: return ToolResult.error("Missing 'ref'")
        val forward = args.string("direction") != "backward"
        return withController { controller ->
            controller.scroll(ref, forward).toToolResult { "Scrolled $ref ${if (forward) "forward" else "backward"}" }
        }
    }
}

class TapTool @Inject constructor(bridge: AccessibilityBridge) : ScreenTool(bridge) {
    override val name = "tap_at"
    override val description = "Tap screen coordinates in pixels. Use only when no element ref is available."
    override val parameters: JsonObject = objectSchema(
        properties = mapOf(
            "x" to integerParam("X coordinate in pixels"),
            "y" to integerParam("Y coordinate in pixels"),
        ),
        required = listOf("x", "y"),
    )

    override suspend fun assessRisk(args: JsonObject): RiskLevel = RiskLevel.MODERATE

    override suspend fun describeAction(args: JsonObject): String =
        "Tap at (${args.int("x") ?: "?"}, ${args.int("y") ?: "?"})"

    override suspend fun execute(args: JsonObject): ToolResult {
        val x = args.int("x") ?: return ToolResult.error("Missing or invalid 'x'")
        val y = args.int("y") ?: return ToolResult.error("Missing or invalid 'y'")
        return withController { controller ->
            controller.tap(x, y).toToolResult { "Tapped ($x, $y)" }
        }
    }
}

class SwipeTool @Inject constructor(bridge: AccessibilityBridge) : ScreenTool(bridge) {
    override val name = "swipe"
    override val description = "Swipe from one coordinate to another in pixels."
    override val parameters: JsonObject = objectSchema(
        properties = mapOf(
            "x1" to integerParam("Start X"),
            "y1" to integerParam("Start Y"),
            "x2" to integerParam("End X"),
            "y2" to integerParam("End Y"),
            "duration_ms" to integerParam("Duration in milliseconds, default 300"),
        ),
        required = listOf("x1", "y1", "x2", "y2"),
    )

    override suspend fun assessRisk(args: JsonObject): RiskLevel = RiskLevel.SAFE

    override suspend fun describeAction(args: JsonObject): String = "Swipe on screen"

    override suspend fun execute(args: JsonObject): ToolResult {
        val x1 = args.int("x1") ?: return ToolResult.error("Missing or invalid 'x1'")
        val y1 = args.int("y1") ?: return ToolResult.error("Missing or invalid 'y1'")
        val x2 = args.int("x2") ?: return ToolResult.error("Missing or invalid 'x2'")
        val y2 = args.int("y2") ?: return ToolResult.error("Missing or invalid 'y2'")
        val duration = args.long("duration_ms") ?: DEFAULT_SWIPE_MS
        return withController { controller ->
            controller.swipe(x1, y1, x2, y2, duration).toToolResult { "Swiped ($x1,$y1) to ($x2,$y2)" }
        }
    }

    private companion object {
        const val DEFAULT_SWIPE_MS = 300L
    }
}

class GlobalActionTool @Inject constructor(bridge: AccessibilityBridge) : ScreenTool(bridge) {
    override val name = "global_action"
    override val description = "Press a system button: back, home, recents, notifications or quick_settings."
    override val parameters: JsonObject = objectSchema(
        properties = mapOf(
            "action" to stringParam(
                "System action to perform",
                listOf("back", "home", "recents", "notifications", "quick_settings"),
            ),
        ),
        required = listOf("action"),
    )

    override suspend fun assessRisk(args: JsonObject): RiskLevel = when (args.string("action")) {
        "notifications", "quick_settings" -> RiskLevel.MODERATE
        else -> RiskLevel.SAFE
    }

    override suspend fun describeAction(args: JsonObject): String = "Press ${args.string("action") ?: "a system button"}"

    override suspend fun execute(args: JsonObject): ToolResult {
        val raw = args.string("action") ?: return ToolResult.error("Missing 'action'")
        val action = GlobalAction.entries.firstOrNull { it.name.equals(raw.replace("_", ""), ignoreCase = true) ||
            it.name.equals(raw, ignoreCase = true) }
            ?: return ToolResult.error("Unknown action '$raw'")
        return withController { controller ->
            controller.global(action).toToolResult { "Pressed $raw" }
        }
    }
}

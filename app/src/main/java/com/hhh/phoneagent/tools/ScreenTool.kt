package com.hhh.phoneagent.tools

import com.hhh.phoneagent.core.common.AppResult
import com.hhh.phoneagent.domain.tool.AgentTool
import com.hhh.phoneagent.domain.tool.ToolResult
import com.hhh.phoneagent.service.accessibility.AccessibilityBridge
import com.hhh.phoneagent.service.accessibility.ScreenController
import com.hhh.phoneagent.service.accessibility.eyes.findByRef

/** Base for tools that act on the screen through the single Accessibility Service. */
abstract class ScreenTool(private val bridge: AccessibilityBridge) : AgentTool {

    protected suspend fun withController(block: suspend (ScreenController) -> ToolResult): ToolResult {
        val controller = bridge.awaitController()
            ?: return ToolResult.error(
                "Accessibility service is not connected. Ask the user to enable Phone Agent in Accessibility settings.",
            )
        return block(controller)
    }

    /** Visible label of the element with [ref] on the current screen, or null if unknown. */
    protected fun labelFor(ref: String): String? {
        val controller = bridge.currentController() ?: return null
        val root = when (val result = controller.snapshot()) {
            is AppResult.Success -> result.value.root
            is AppResult.Failure -> return null
        }
        val node = root.findByRef(ref) ?: return null
        return node.text?.takeIf { it.isNotBlank() }
            ?: node.contentDescription?.takeIf { it.isNotBlank() }
    }
}

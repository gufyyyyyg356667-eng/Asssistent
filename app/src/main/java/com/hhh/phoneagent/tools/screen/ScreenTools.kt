package com.hhh.phoneagent.tools.screen

import com.hhh.phoneagent.domain.model.RiskLevel
import com.hhh.phoneagent.domain.tool.ToolResult
import com.hhh.phoneagent.domain.tool.objectSchema
import com.hhh.phoneagent.domain.tool.stringParam
import com.hhh.phoneagent.domain.tool.string
import com.hhh.phoneagent.domain.tool.toToolResult
import com.hhh.phoneagent.service.accessibility.AccessibilityBridge
import com.hhh.phoneagent.service.accessibility.eyes.ScreenRenderer
import com.hhh.phoneagent.tools.ScreenTool
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject

class ReadScreenTool @Inject constructor(bridge: AccessibilityBridge) : ScreenTool(bridge) {
    override val name = "read_screen"
    override val description =
        "Read the active screen as text. Interactive elements carry refs such as [0.2.1]. " +
            "Pass refs to click, type_text and scroll. Read again after the screen changes."
    override val parameters: JsonObject = objectSchema(emptyMap())

    override suspend fun assessRisk(args: JsonObject): RiskLevel = RiskLevel.SAFE

    override suspend fun describeAction(args: JsonObject): String = "Read the screen"

    override suspend fun execute(args: JsonObject): ToolResult = withController { controller ->
        controller.snapshot().toToolResult { ScreenRenderer.toPromptText(it) }
    }
}

class FindElementsTool @Inject constructor(bridge: AccessibilityBridge) : ScreenTool(bridge) {
    override val name = "find_elements"
    override val description =
        "Find elements on the current screen whose text, description or view id contains the query " +
            "(case-insensitive). Returns their refs."
    override val parameters: JsonObject = objectSchema(
        properties = mapOf("query" to stringParam("Text to search for")),
        required = listOf("query"),
    )

    override suspend fun assessRisk(args: JsonObject): RiskLevel = RiskLevel.SAFE

    override suspend fun describeAction(args: JsonObject): String =
        "Search the screen for \"${args.string("query").orEmpty()}\""

    override suspend fun execute(args: JsonObject): ToolResult {
        val query = args.string("query")?.takeIf { it.isNotBlank() }
            ?: return ToolResult.error("Missing 'query'")
        return withController { controller ->
            controller.findNodes(query).toToolResult { nodes ->
                if (nodes.isEmpty()) {
                    "No matching elements"
                } else {
                    nodes.joinToString("\n") { node ->
                        val label = node.text ?: node.contentDescription ?: node.viewId.orEmpty()
                        "[${node.ref}] ${node.className?.substringAfterLast('.') ?: "View"} \"$label\""
                    }
                }
            }
        }
    }
}

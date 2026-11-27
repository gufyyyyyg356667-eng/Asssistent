package com.hhh.phoneagent.domain.tool

import kotlinx.serialization.json.JsonObject

data class ToolSchema(
    val name: String,
    val description: String,
    val parameters: JsonObject,
)

interface ToolRegistry {
    fun find(name: String): AgentTool?
    fun all(): List<AgentTool>
    fun schemas(): List<ToolSchema>
}

package com.hhh.phoneagent.domain.tool

import com.hhh.phoneagent.domain.model.RiskLevel
import kotlinx.serialization.json.JsonObject

/**
 * Every capability the agent can use implements this. Risk is assessed per call, not per
 * tool, because the same tool can be harmless in one place and dangerous in another.
 */
interface AgentTool {
    val name: String
    val description: String

    /** JSON Schema (type = object) for the arguments. */
    val parameters: JsonObject

    suspend fun assessRisk(args: JsonObject): RiskLevel

    /** Human-readable summary shown in the approval prompt. */
    suspend fun describeAction(args: JsonObject): String = "Run $name"

    suspend fun execute(args: JsonObject): ToolResult
}

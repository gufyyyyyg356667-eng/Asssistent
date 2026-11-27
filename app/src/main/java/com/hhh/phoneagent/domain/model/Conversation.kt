package com.hhh.phoneagent.domain.model

import com.hhh.phoneagent.domain.tool.ToolResult
import kotlinx.serialization.json.JsonObject

data class ToolCall(
    val id: String,
    val name: String,
    val args: JsonObject,
)

sealed interface ChatMessage {
    data class User(val text: String) : ChatMessage
    data class ModelText(val text: String) : ChatMessage
    data class ModelToolCall(val call: ToolCall) : ChatMessage
    data class ToolResponse(val call: ToolCall, val result: ToolResult) : ChatMessage
}

sealed interface ModelTurn {
    data class Text(val text: String) : ModelTurn
    data class ToolCalls(val calls: List<ToolCall>, val preamble: String?) : ModelTurn
}

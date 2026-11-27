package com.hhh.phoneagent.data.remote.gemini

import com.hhh.phoneagent.core.common.AppError.ErrorCode
import com.hhh.phoneagent.core.common.AppResult
import com.hhh.phoneagent.core.common.failure
import com.hhh.phoneagent.domain.model.ChatMessage
import com.hhh.phoneagent.domain.model.ModelTurn
import com.hhh.phoneagent.domain.model.ToolCall
import com.hhh.phoneagent.domain.tool.ToolSchema
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

internal fun toGeminiRequest(
    history: List<ChatMessage>,
    systemPrompt: String,
    tools: List<ToolSchema>,
): GenerateContentRequest = GenerateContentRequest(
    contents = history.map(::toContent),
    tools = tools.takeIf { it.isNotEmpty() }?.let { schemas ->
        listOf(
            GeminiTool(
                functionDeclarations = schemas.map { schema ->
                    FunctionDeclaration(
                        name = schema.name,
                        description = schema.description,
                        parameters = schema.parameters.toGeminiSchema(),
                    )
                },
            ),
        )
    },
    systemInstruction = systemPrompt.takeIf { it.isNotBlank() }?.let {
        Content(parts = listOf(Part(text = it)))
    },
)

private fun toContent(message: ChatMessage): Content = when (message) {
    is ChatMessage.User -> Content(role = "user", parts = listOf(Part(text = message.text)))
    is ChatMessage.ModelText -> Content(role = "model", parts = listOf(Part(text = message.text)))
    is ChatMessage.ModelToolCall -> Content(
        role = "model",
        parts = listOf(Part(functionCall = FunctionCall(message.call.name, message.call.args))),
    )
    is ChatMessage.ToolResponse -> Content(
        role = "user",
        parts = listOf(
            Part(
                functionResponse = FunctionResponse(
                    name = message.call.name,
                    response = buildJsonObject {
                        put("status", message.result.status.name)
                        put("content", message.result.content)
                    },
                ),
            ),
        ),
    )
}

/** Gemini expects upper-case schema types (OBJECT, STRING). Converts them recursively. */
internal fun JsonObject.toGeminiSchema(): JsonObject = JsonObject(
    mapValues { (key, value) ->
        if (key == "type" && value is JsonPrimitive && value.isString) {
            JsonPrimitive(value.content.uppercase())
        } else {
            convertSchemaValue(value)
        }
    },
)

private fun convertSchemaValue(value: JsonElement): JsonElement = when (value) {
    is JsonObject -> value.toGeminiSchema()
    is JsonArray -> JsonArray(value.map(::convertSchemaValue))
    else -> value
}

internal fun toModelTurn(response: GenerateContentResponse): AppResult<ModelTurn> {
    response.error?.let { return failure(ErrorCode.API_ERROR, it.message ?: "Gemini returned an error") }
    val candidate = response.candidates?.firstOrNull()
        ?: return failure(ErrorCode.API_ERROR, "Gemini returned no candidates")
    val parts = candidate.content?.parts.orEmpty()

    val calls = parts.mapNotNull { it.functionCall }.map { fc ->
        ToolCall(
            id = UUID.randomUUID().toString(),
            name = fc.name,
            args = fc.args ?: JsonObject(emptyMap()),
        )
    }
    val text = parts.mapNotNull { it.text }.joinToString("").trim()

    return when {
        calls.isNotEmpty() -> AppResult.Success(ModelTurn.ToolCalls(calls, text.ifEmpty { null }))
        text.isNotEmpty() -> AppResult.Success(ModelTurn.Text(text))
        else -> failure(ErrorCode.API_ERROR, "Empty response (finish reason: ${candidate.finishReason})")
    }
}

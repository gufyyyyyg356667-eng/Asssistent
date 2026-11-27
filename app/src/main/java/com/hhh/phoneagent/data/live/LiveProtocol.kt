package com.hhh.phoneagent.data.live

import com.hhh.phoneagent.data.remote.gemini.toGeminiSchema
import com.hhh.phoneagent.domain.model.ToolCall
import com.hhh.phoneagent.domain.tool.ToolResult
import com.hhh.phoneagent.domain.tool.ToolSchema
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.util.Base64

sealed interface LiveEvent {
    data object SetupComplete : LiveEvent
    data class Audio(val pcm: ByteArray) : LiveEvent
    data object Interrupted : LiveEvent
    data object TurnComplete : LiveEvent
    data class ToolCalls(val calls: List<ToolCall>) : LiveEvent
    data class Error(val message: String) : LiveEvent
}

/** Builds client messages and parses server messages for the Gemini Live WebSocket. */
object LiveProtocol {
    const val INPUT_SAMPLE_RATE = 16_000
    const val OUTPUT_SAMPLE_RATE = 24_000

    fun setup(model: String, systemPrompt: String, tools: List<ToolSchema>): JsonObject = buildJsonObject {
        putJsonObject("setup") {
            put("model", "models/$model")
            putJsonObject("generationConfig") {
                putJsonArray("responseModalities") { add(JsonPrimitive("AUDIO")) }
            }
            putJsonObject("systemInstruction") {
                putJsonArray("parts") { add(buildJsonObject { put("text", systemPrompt) }) }
            }
            if (tools.isNotEmpty()) {
                putJsonArray("tools") {
                    add(buildJsonObject {
                        putJsonArray("functionDeclarations") {
                            tools.forEach { add(declaration(it)) }
                        }
                    })
                }
            }
        }
    }

    fun audioChunk(pcm: ByteArray): JsonObject = buildJsonObject {
        putJsonObject("realtimeInput") {
            putJsonObject("audio") {
                put("data", Base64.getEncoder().encodeToString(pcm))
                put("mimeType", "audio/pcm;rate=$INPUT_SAMPLE_RATE")
            }
        }
    }

    fun toolResponse(call: ToolCall, result: ToolResult): JsonObject = buildJsonObject {
        putJsonObject("toolResponse") {
            putJsonArray("functionResponses") {
                add(buildJsonObject {
                    put("id", call.id)
                    put("name", call.name)
                    putJsonObject("response") {
                        put("status", result.status.name)
                        put("content", result.content)
                    }
                })
            }
        }
    }

    fun parse(text: String, json: Json): List<LiveEvent> {
        val root = json.parseToJsonElement(text).jsonObject
        val events = mutableListOf<LiveEvent>()

        if (root.containsKey("setupComplete")) events += LiveEvent.SetupComplete

        root["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content?.let {
            events += LiveEvent.Error(it)
        }

        root["serverContent"]?.jsonObject?.let { content ->
            content["modelTurn"]?.jsonObject?.get("parts")?.jsonArray?.forEach { part ->
                part.jsonObject["inlineData"]?.jsonObject?.get("data")?.jsonPrimitive?.content?.let { data ->
                    events += LiveEvent.Audio(Base64.getDecoder().decode(data))
                }
            }
            if (content["interrupted"]?.jsonPrimitive?.booleanOrNull == true) events += LiveEvent.Interrupted
            if (content["turnComplete"]?.jsonPrimitive?.booleanOrNull == true) events += LiveEvent.TurnComplete
        }

        root["toolCall"]?.jsonObject?.get("functionCalls")?.jsonArray?.let { array ->
            val calls = array.map { element ->
                val call = element.jsonObject
                ToolCall(
                    id = call["id"]?.jsonPrimitive?.content.orEmpty(),
                    name = call["name"]?.jsonPrimitive?.content.orEmpty(),
                    args = call["args"]?.jsonObject ?: JsonObject(emptyMap()),
                )
            }
            if (calls.isNotEmpty()) events += LiveEvent.ToolCalls(calls)
        }
        return events
    }

    private fun declaration(schema: ToolSchema): JsonObject = buildJsonObject {
        put("name", schema.name)
        put("description", schema.description)
        put("parameters", schema.parameters.toGeminiSchema())
    }
}

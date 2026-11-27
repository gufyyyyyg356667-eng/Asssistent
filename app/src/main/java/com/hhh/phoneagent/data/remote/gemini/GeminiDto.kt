package com.hhh.phoneagent.data.remote.gemini

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class GenerateContentRequest(
    val contents: List<Content>,
    val tools: List<GeminiTool>? = null,
    val systemInstruction: Content? = null,
    val generationConfig: GenerationConfig? = null,
)

@Serializable
data class Content(
    val role: String? = null,
    val parts: List<Part>,
)

@Serializable
data class Part(
    val text: String? = null,
    val functionCall: FunctionCall? = null,
    val functionResponse: FunctionResponse? = null,
)

@Serializable
data class FunctionCall(
    val name: String,
    val args: JsonObject? = null,
)

@Serializable
data class FunctionResponse(
    val name: String,
    val response: JsonObject,
)

@Serializable
data class GeminiTool(
    val functionDeclarations: List<FunctionDeclaration>,
)

@Serializable
data class FunctionDeclaration(
    val name: String,
    val description: String,
    val parameters: JsonObject? = null,
)

@Serializable
data class GenerationConfig(
    val temperature: Double? = null,
    val maxOutputTokens: Int? = null,
)

@Serializable
data class GenerateContentResponse(
    val candidates: List<Candidate>? = null,
    val error: ErrorBody? = null,
)

@Serializable
data class Candidate(
    val content: Content? = null,
    val finishReason: String? = null,
)

@Serializable
data class ErrorEnvelope(val error: ErrorBody? = null)

@Serializable
data class ErrorBody(
    val code: Int? = null,
    val message: String? = null,
    val status: String? = null,
)

package com.hhh.phoneagent.data.remote.gemini

import com.hhh.phoneagent.core.common.AppResult
import com.hhh.phoneagent.core.common.flatMap
import com.hhh.phoneagent.domain.model.ChatMessage
import com.hhh.phoneagent.domain.model.ModelTurn
import com.hhh.phoneagent.domain.repository.LanguageModel
import com.hhh.phoneagent.domain.tool.ToolSchema
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiLanguageModel @Inject constructor(
    private val api: GeminiApi,
) : LanguageModel {

    override suspend fun generate(
        history: List<ChatMessage>,
        systemPrompt: String,
        tools: List<ToolSchema>,
    ): AppResult<ModelTurn> = api
        .generateContent(DEFAULT_MODEL, toGeminiRequest(history, systemPrompt, tools))
        .flatMap(::toModelTurn)

    companion object {
        /** Change this here or move it to settings. Confirm the current model id before release. */
        const val DEFAULT_MODEL = "gemini-3.8-flash"
    }
}

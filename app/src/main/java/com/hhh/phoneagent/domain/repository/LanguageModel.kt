package com.hhh.phoneagent.domain.repository

import com.hhh.phoneagent.core.common.AppResult
import com.hhh.phoneagent.domain.model.ChatMessage
import com.hhh.phoneagent.domain.model.ModelTurn
import com.hhh.phoneagent.domain.tool.ToolSchema

interface LanguageModel {
    suspend fun generate(
        history: List<ChatMessage>,
        systemPrompt: String,
        tools: List<ToolSchema>,
    ): AppResult<ModelTurn>
}

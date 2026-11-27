package com.hhh.phoneagent.domain.usecase

import com.hhh.phoneagent.core.common.AppResult
import com.hhh.phoneagent.core.common.Clock
import com.hhh.phoneagent.domain.model.ChatMessage
import com.hhh.phoneagent.domain.model.ModelTurn
import com.hhh.phoneagent.domain.model.ToolCall
import com.hhh.phoneagent.domain.repository.LanguageModel
import com.hhh.phoneagent.domain.security.ApprovalDecision
import com.hhh.phoneagent.domain.security.ApprovalGate
import com.hhh.phoneagent.domain.security.ApprovalRequest
import com.hhh.phoneagent.domain.tool.AgentTool
import com.hhh.phoneagent.domain.tool.ToolRegistry
import com.hhh.phoneagent.domain.tool.ToolSchema
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class RunAgentTurnUseCaseTest {

    private val registry = object : ToolRegistry {
        override fun find(name: String): AgentTool? = null
        override fun all(): List<AgentTool> = emptyList()
        override fun schemas(): List<ToolSchema> = emptyList()
    }

    private val approvals = object : ApprovalGate {
        override suspend fun requestApproval(request: ApprovalRequest) = ApprovalDecision.DENIED
    }

    @Test
    fun unknownToolIsReportedBackAndModelContinues() = runTest {
        var calls = 0
        val model = object : LanguageModel {
            override suspend fun generate(
                history: List<ChatMessage>,
                systemPrompt: String,
                tools: List<ToolSchema>,
            ): AppResult<ModelTurn> {
                calls++
                return if (calls == 1) {
                    AppResult.Success(ModelTurn.ToolCalls(listOf(ToolCall("1", "missing", JsonObject(emptyMap()))), null))
                } else {
                    AppResult.Success(ModelTurn.Text("Done"))
                }
            }
        }
        val session = AgentSession()
        val useCase = RunAgentTurnUseCase(
            model, registry, ExecuteToolUseCase(registry, approvals, Clock { 0L }), session,
        )

        assertEquals(AppResult.Success("Done"), useCase("open settings"))
        assertEquals(2, calls)
        assertEquals(4, session.snapshot().size)
    }
}

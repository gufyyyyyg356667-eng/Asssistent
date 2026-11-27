package com.hhh.phoneagent.domain.usecase

import com.hhh.phoneagent.core.common.AppError
import com.hhh.phoneagent.core.common.AppResult
import com.hhh.phoneagent.core.common.failure
import com.hhh.phoneagent.domain.model.ChatMessage
import com.hhh.phoneagent.domain.model.ModelTurn
import com.hhh.phoneagent.domain.repository.LanguageModel
import com.hhh.phoneagent.domain.tool.ToolRegistry
import javax.inject.Inject

/** One user turn: the model may call tools several times before it gives a final text answer. */
class RunAgentTurnUseCase @Inject constructor(
    private val model: LanguageModel,
    private val registry: ToolRegistry,
    private val executeTool: ExecuteToolUseCase,
    private val session: AgentSession,
) {
    suspend operator fun invoke(userText: String): AppResult<String> {
        session.append(ChatMessage.User(userText))
        repeat(MAX_STEPS) {
            val turn = when (val generated = model.generate(session.snapshot(), AgentPrompts.SYSTEM, registry.schemas())) {
                is AppResult.Failure -> return generated
                is AppResult.Success -> generated.value
            }
            when (turn) {
                is ModelTurn.Text -> {
                    session.append(ChatMessage.ModelText(turn.text))
                    return AppResult.Success(turn.text)
                }
                is ModelTurn.ToolCalls -> {
                    turn.preamble?.let { session.append(ChatMessage.ModelText(it)) }
                    turn.calls.forEach { call ->
                        session.append(ChatMessage.ModelToolCall(call))
                        session.append(ChatMessage.ToolResponse(call, executeTool(call)))
                    }
                }
            }
        }
        return failure(AppError.ErrorCode.API_ERROR, "Stopped after $MAX_STEPS steps without a final answer")
    }

    private companion object {
        const val MAX_STEPS = 8
    }
}

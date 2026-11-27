package com.hhh.phoneagent.domain.usecase

import com.hhh.phoneagent.core.common.Clock
import com.hhh.phoneagent.domain.model.ToolCall
import com.hhh.phoneagent.domain.security.ApprovalDecision
import com.hhh.phoneagent.domain.security.ApprovalGate
import com.hhh.phoneagent.domain.security.ApprovalRequest
import com.hhh.phoneagent.domain.security.RiskPolicy
import com.hhh.phoneagent.domain.tool.ToolRegistry
import com.hhh.phoneagent.domain.tool.ToolResult
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/**
 * The single entry point for running a tool. Order: lookup, assess risk, approval gate
 * (HIGH_RISK only), execute. Any failure before execution means nothing ran.
 */
class ExecuteToolUseCase @Inject constructor(
    private val registry: ToolRegistry,
    private val approvals: ApprovalGate,
    private val clock: Clock,
) {
    suspend operator fun invoke(call: ToolCall): ToolResult {
        val tool = registry.find(call.name) ?: return ToolResult.error("Unknown tool '${call.name}'")
        return try {
            val risk = tool.assessRisk(call.args)
            if (RiskPolicy.requiresApproval(risk)) {
                val decision = approvals.requestApproval(
                    ApprovalRequest(
                        id = call.id,
                        toolName = call.name,
                        risk = risk,
                        summary = tool.describeAction(call.args),
                        requestedAtMillis = clock.nowMillis(),
                    ),
                )
                when (decision) {
                    ApprovalDecision.APPROVED -> Unit
                    ApprovalDecision.DENIED -> return ToolResult.denied(
                        "The user denied '${call.name}'. Do not retry it. Ask the user how to proceed.",
                    )
                    ApprovalDecision.TIMED_OUT -> return ToolResult.denied(
                        "Approval timed out for '${call.name}'. The action was not performed.",
                    )
                }
            }
            tool.execute(call.args)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ToolResult.error("'${call.name}' failed: ${e.message ?: e::class.simpleName}")
        }
    }
}

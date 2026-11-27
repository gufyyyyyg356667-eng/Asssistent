package com.hhh.phoneagent.domain.usecase

import com.hhh.phoneagent.core.common.Clock
import com.hhh.phoneagent.domain.model.RiskLevel
import com.hhh.phoneagent.domain.model.ToolCall
import com.hhh.phoneagent.domain.security.ApprovalDecision
import com.hhh.phoneagent.domain.security.ApprovalGate
import com.hhh.phoneagent.domain.security.ApprovalRequest
import com.hhh.phoneagent.domain.tool.AgentTool
import com.hhh.phoneagent.domain.tool.ToolRegistry
import com.hhh.phoneagent.domain.tool.ToolResult
import com.hhh.phoneagent.domain.tool.ToolSchema
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class ExecuteToolUseCaseTest {

    private class FakeTool(
        override val name: String,
        private val risk: RiskLevel,
    ) : AgentTool {
        override val description = "fake"
        override val parameters: JsonObject = JsonObject(emptyMap())
        var executed = false

        override suspend fun assessRisk(args: JsonObject): RiskLevel = risk

        override suspend fun execute(args: JsonObject): ToolResult {
            executed = true
            return ToolResult.ok("done")
        }
    }

    private class FakeRegistry(private val tools: List<AgentTool>) : ToolRegistry {
        override fun find(name: String): AgentTool? = tools.firstOrNull { it.name == name }
        override fun all(): List<AgentTool> = tools
        override fun schemas(): List<ToolSchema> = emptyList()
    }

    private class FakeGate(private val decision: ApprovalDecision) : ApprovalGate {
        var asked = 0
        override suspend fun requestApproval(request: ApprovalRequest): ApprovalDecision {
            asked++
            return decision
        }
    }

    private val clock = Clock { 1_000L }

    private fun call(name: String) = ToolCall(id = "id-$name", name = name, args = JsonObject(emptyMap()))

    @Test
    fun safeToolRunsWithoutApproval() = runTest {
        val tool = FakeTool("read", RiskLevel.SAFE)
        val gate = FakeGate(ApprovalDecision.DENIED)
        val result = ExecuteToolUseCase(FakeRegistry(listOf(tool)), gate, clock).invoke(call("read"))
        assertEquals(ToolResult.Status.OK, result.status)
        assertEquals(true, tool.executed)
        assertEquals(0, gate.asked)
    }

    @Test
    fun highRiskToolRunsWhenApproved() = runTest {
        val tool = FakeTool("send", RiskLevel.HIGH_RISK)
        val gate = FakeGate(ApprovalDecision.APPROVED)
        val result = ExecuteToolUseCase(FakeRegistry(listOf(tool)), gate, clock).invoke(call("send"))
        assertEquals(ToolResult.Status.OK, result.status)
        assertEquals(true, tool.executed)
        assertEquals(1, gate.asked)
    }

    @Test
    fun highRiskToolIsBlockedWhenDenied() = runTest {
        val tool = FakeTool("send", RiskLevel.HIGH_RISK)
        val result = ExecuteToolUseCase(FakeRegistry(listOf(tool)), FakeGate(ApprovalDecision.DENIED), clock)
            .invoke(call("send"))
        assertEquals(ToolResult.Status.DENIED, result.status)
        assertEquals(false, tool.executed)
    }

    @Test
    fun highRiskToolIsBlockedOnTimeout() = runTest {
        val tool = FakeTool("send", RiskLevel.HIGH_RISK)
        val result = ExecuteToolUseCase(FakeRegistry(listOf(tool)), FakeGate(ApprovalDecision.TIMED_OUT), clock)
            .invoke(call("send"))
        assertEquals(ToolResult.Status.DENIED, result.status)
        assertEquals(false, tool.executed)
    }

    @Test
    fun unknownToolReturnsError() = runTest {
        val result = ExecuteToolUseCase(FakeRegistry(emptyList()), FakeGate(ApprovalDecision.APPROVED), clock)
            .invoke(call("missing"))
        assertEquals(ToolResult.Status.ERROR, result.status)
    }
}

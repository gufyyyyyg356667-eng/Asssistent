package com.hhh.phoneagent.tools.workflow

import com.hhh.phoneagent.domain.model.RiskLevel
import com.hhh.phoneagent.domain.tool.AgentTool
import com.hhh.phoneagent.domain.tool.ToolResult
import com.hhh.phoneagent.domain.tool.objectSchema
import com.hhh.phoneagent.domain.tool.string
import com.hhh.phoneagent.domain.tool.stringParam
import com.hhh.phoneagent.domain.tool.toToolResult
import com.hhh.phoneagent.data.workflow.WorkflowFileStore
import com.hhh.phoneagent.domain.workflow.RunWorkflowUseCase
import dagger.Lazy
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject

/** Lazy because the runner depends on the registry, and this tool is part of the registry. */
class RunWorkflowTool @Inject constructor(
    private val runWorkflow: Lazy<RunWorkflowUseCase>,
) : AgentTool {
    override val name = "run_workflow"
    override val description = "Run a saved workflow by name. Use list_workflows to see the names."
    override val parameters: JsonObject = objectSchema(
        properties = mapOf("name" to stringParam("Workflow name")),
        required = listOf("name"),
    )

    override suspend fun assessRisk(args: JsonObject): RiskLevel = RiskLevel.MODERATE

    override suspend fun describeAction(args: JsonObject): String =
        "Run workflow \"${args.string("name").orEmpty()}\""

    override suspend fun execute(args: JsonObject): ToolResult {
        val name = args.string("name")?.takeIf { it.isNotBlank() }
            ?: return ToolResult.error("Missing 'name'")
        return runWorkflow.get().invoke(name).toToolResult { it }
    }
}

class ListWorkflowsTool @Inject constructor(
    private val store: WorkflowFileStore,
) : AgentTool {
    override val name = "list_workflows"
    override val description = "List the names of saved workflows."
    override val parameters: JsonObject = objectSchema(emptyMap())

    override suspend fun assessRisk(args: JsonObject): RiskLevel = RiskLevel.SAFE

    override suspend fun execute(args: JsonObject): ToolResult {
        val names = store.names()
        return ToolResult.ok(if (names.isEmpty()) "No workflows saved" else names.joinToString("\n"))
    }
}

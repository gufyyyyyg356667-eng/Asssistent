package com.hhh.phoneagent.domain.workflow

import com.hhh.phoneagent.core.common.AppError
import com.hhh.phoneagent.core.common.AppResult
import com.hhh.phoneagent.core.common.failure
import com.hhh.phoneagent.data.workflow.WorkflowFileStore
import com.hhh.phoneagent.domain.model.ToolCall
import com.hhh.phoneagent.domain.usecase.ExecuteToolUseCase
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.UUID
import javax.inject.Inject

/**
 * Runs a stored workflow step by step. Each tool step goes through ExecuteToolUseCase,
 * so HIGH_RISK steps still wait for the user's approval. The first failed step stops the run
 * unless that step sets continueOnError.
 */
class RunWorkflowUseCase @Inject constructor(
    private val store: WorkflowFileStore,
    private val executeTool: ExecuteToolUseCase,
) {
    suspend operator fun invoke(name: String): AppResult<String> {
        val workflow = when (val loaded = store.load(name)) {
            is AppResult.Failure -> return loaded
            is AppResult.Success -> loaded.value
        }
        val variables = HashMap<String, String>()
        val log = StringBuilder()

        workflow.steps.forEachIndexed { index, step ->
            val position = index + 1
            when (step) {
                is WorkflowStep.Wait -> {
                    delay(step.millis)
                    log.append("$position. waited ${step.millis} ms\n")
                }
                is WorkflowStep.Stop -> {
                    log.append("$position. stopped${if (step.message.isNotBlank()) ": ${step.message}" else ""}\n")
                    return AppResult.Success(log.toString().trimEnd())
                }
                is WorkflowStep.Tool -> {
                    val args = substitute(step.args, variables)
                    val result = executeTool(
                        ToolCall(id = UUID.randomUUID().toString(), name = step.tool, args = args),
                    )
                    log.append("$position. ${step.tool}: ${result.status}\n")
                    if (result.isSuccess) {
                        step.saveAs?.let { variables[it] = result.content }
                    } else if (!step.continueOnError) {
                        return failure(
                            AppError.ErrorCode.ACTION_REJECTED,
                            "Step $position (${step.tool}) stopped the workflow: ${result.content}\n" +
                                log.toString().trimEnd(),
                        )
                    }
                }
            }
        }
        return AppResult.Success(log.toString().trimEnd())
    }
}

/** Replaces {{name}} in every string value with a saved variable. Unknown names are left as written. */
internal fun substitute(element: JsonElement, variables: Map<String, String>): JsonObject =
    substituteElement(element, variables) as? JsonObject ?: JsonObject(emptyMap())

private fun substituteElement(element: JsonElement, variables: Map<String, String>): JsonElement = when (element) {
    is JsonPrimitive -> if (element.isString) {
        JsonPrimitive(PLACEHOLDER.replace(element.content) { match ->
            variables[match.groupValues[1]] ?: match.value
        })
    } else {
        element
    }
    is JsonObject -> JsonObject(element.mapValues { substituteElement(it.value, variables) })
    is JsonArray -> JsonArray(element.map { substituteElement(it, variables) })
}

private val PLACEHOLDER = Regex("\\{\\{(\\w+)\\}\\}")

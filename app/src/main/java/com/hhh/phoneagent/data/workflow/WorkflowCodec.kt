package com.hhh.phoneagent.data.workflow

import com.hhh.phoneagent.core.common.AppError
import com.hhh.phoneagent.core.common.AppResult
import com.hhh.phoneagent.core.common.failure
import com.hhh.phoneagent.domain.workflow.Workflow
import com.hhh.phoneagent.domain.workflow.WorkflowLimits
import com.hhh.phoneagent.domain.workflow.WorkflowStep
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** Parses and validates workflow JSON. Invalid workflows are rejected before anything runs. */
@Singleton
class WorkflowCodec @Inject constructor(private val json: Json) {

    fun parse(text: String): AppResult<Workflow> {
        val workflow = try {
            json.decodeFromString(Workflow.serializer(), text)
        } catch (e: Exception) {
            return failure(AppError.ErrorCode.INVALID_INPUT, "Invalid workflow JSON: ${e.message}", e)
        }
        return validate(workflow)
    }

    fun encode(workflow: Workflow): String =
        json.encodeToString(Workflow.serializer(), workflow)

    fun validate(workflow: Workflow): AppResult<Workflow> {
        if (workflow.name.isBlank()) return invalid("Workflow name is empty")
        if (workflow.steps.isEmpty()) return invalid("Workflow has no steps")
        if (workflow.steps.size > WorkflowLimits.MAX_STEPS) {
            return invalid("Too many steps (max ${WorkflowLimits.MAX_STEPS})")
        }
        workflow.steps.forEachIndexed { index, step ->
            val position = index + 1
            when (step) {
                is WorkflowStep.Tool -> {
                    if (step.tool.isBlank()) return invalid("Step $position has no tool name")
                    if (step.tool == WorkflowLimits.NESTED_TOOL) {
                        return invalid("Step $position: workflows cannot call run_workflow")
                    }
                }
                is WorkflowStep.Wait -> {
                    if (step.millis !in 0..WorkflowLimits.MAX_WAIT_MILLIS) {
                        return invalid("Step $position: wait must be 0 to ${WorkflowLimits.MAX_WAIT_MILLIS} ms")
                    }
                }
                is WorkflowStep.Stop -> Unit
            }
        }
        return AppResult.Success(workflow)
    }

    private fun invalid(message: String): AppResult.Failure =
        failure(AppError.ErrorCode.INVALID_INPUT, message)
}

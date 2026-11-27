package com.hhh.phoneagent.domain.workflow

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** A no-code workflow stored as JSON. Steps run in order; tool steps go through the same risk gate as chat. */
@Serializable
data class Workflow(
    val name: String,
    val description: String = "",
    val steps: List<WorkflowStep>,
)

@Serializable
sealed interface WorkflowStep {

    /** Calls a registered tool. [saveAs] stores the result text for later steps as {{name}}. */
    @Serializable
    @SerialName("tool")
    data class Tool(
        val tool: String,
        val args: JsonObject = JsonObject(emptyMap()),
        val saveAs: String? = null,
        val continueOnError: Boolean = false,
    ) : WorkflowStep

    @Serializable
    @SerialName("wait")
    data class Wait(val millis: Long) : WorkflowStep

    /** Ends the workflow early with an optional message. */
    @Serializable
    @SerialName("stop")
    data class Stop(val message: String = "") : WorkflowStep
}

object WorkflowLimits {
    const val MAX_STEPS = 50
    const val MAX_WAIT_MILLIS = 600_000L
    const val MAX_NAME_LENGTH = 40
    const val NESTED_TOOL = "run_workflow"
}

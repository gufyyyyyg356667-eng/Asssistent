package com.hhh.phoneagent.data.workflow

import android.content.Context
import com.hhh.phoneagent.core.common.AppError
import com.hhh.phoneagent.core.common.AppResult
import com.hhh.phoneagent.core.common.failure
import com.hhh.phoneagent.core.common.runCatchingApp
import com.hhh.phoneagent.domain.workflow.Workflow
import com.hhh.phoneagent.domain.workflow.WorkflowLimits
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Stores workflows as <name>.json under app-private files. Names are restricted to safe file names. */
@Singleton
class WorkflowFileStore @Inject constructor(
    @ApplicationContext context: Context,
    private val codec: WorkflowCodec,
) {
    private val dir = File(context.filesDir, "workflows").apply { mkdirs() }

    fun names(): List<String> =
        dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }
            ?.map { it.name.removeSuffix(".json") }
            ?.sorted()
            ?: emptyList()

    fun load(name: String): AppResult<Workflow> {
        if (!isSafeName(name)) return failure(AppError.ErrorCode.INVALID_INPUT, "Invalid workflow name '$name'")
        val file = File(dir, "$name.json")
        if (!file.exists()) return failure(AppError.ErrorCode.NODE_NOT_FOUND, "No workflow named '$name'")
        return runCatchingApp(AppError.ErrorCode.UNKNOWN) { file.readText() }.let { read ->
            when (read) {
                is AppResult.Failure -> read
                is AppResult.Success -> codec.parse(read.value)
            }
        }
    }

    fun save(name: String, workflow: Workflow): AppResult<Unit> {
        if (!isSafeName(name)) return failure(AppError.ErrorCode.INVALID_INPUT, "Invalid workflow name '$name'")
        return codec.validate(workflow).let { valid ->
            when (valid) {
                is AppResult.Failure -> valid
                is AppResult.Success -> runCatchingApp(AppError.ErrorCode.UNKNOWN) {
                    File(dir, "$name.json").writeText(codec.encode(workflow))
                }
            }
        }
    }

    private fun isSafeName(name: String): Boolean =
        name.length in 1..WorkflowLimits.MAX_NAME_LENGTH && NAME_PATTERN.matches(name)

    private companion object {
        val NAME_PATTERN = Regex("[a-z0-9_-]+")
    }
}

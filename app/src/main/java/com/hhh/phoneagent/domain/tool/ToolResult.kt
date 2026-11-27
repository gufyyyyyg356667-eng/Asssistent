package com.hhh.phoneagent.domain.tool

import com.hhh.phoneagent.core.common.AppResult

data class ToolResult(
    val status: Status,
    val content: String,
) {
    enum class Status { OK, ERROR, DENIED }

    val isSuccess: Boolean get() = status == Status.OK

    companion object {
        fun ok(content: String) = ToolResult(Status.OK, content)
        fun error(content: String) = ToolResult(Status.ERROR, content)
        fun denied(content: String) = ToolResult(Status.DENIED, content)
    }
}

inline fun <T> AppResult<T>.toToolResult(onSuccess: (T) -> String): ToolResult = when (this) {
    is AppResult.Success -> ToolResult.ok(onSuccess(value))
    is AppResult.Failure -> ToolResult.error("${error.code}: ${error.message}")
}

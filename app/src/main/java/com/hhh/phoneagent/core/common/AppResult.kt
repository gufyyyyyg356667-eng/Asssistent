package com.hhh.phoneagent.core.common

import kotlin.coroutines.cancellation.CancellationException

sealed interface AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}

data class AppError(
    val code: ErrorCode,
    val message: String,
    val cause: Throwable? = null,
) {
    enum class ErrorCode {
        SERVICE_UNAVAILABLE,
        NODE_NOT_FOUND,
        STALE_REFERENCE,
        ACTION_REJECTED,
        GESTURE_FAILED,
        INVALID_INPUT,
        RATE_LIMITED,
        AUTH_FAILED,
        NETWORK,
        API_ERROR,
        UNKNOWN,
    }
}

fun failure(code: AppError.ErrorCode, message: String, cause: Throwable? = null): AppResult.Failure =
    AppResult.Failure(AppError(code, message, cause))

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(value))
    is AppResult.Failure -> this
}

inline fun <T, R> AppResult<T>.flatMap(transform: (T) -> AppResult<R>): AppResult<R> = when (this) {
    is AppResult.Success -> transform(value)
    is AppResult.Failure -> this
}

inline fun <T> runCatchingApp(
    code: AppError.ErrorCode = AppError.ErrorCode.UNKNOWN,
    block: () -> T,
): AppResult<T> = try {
    AppResult.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    failure(code, e.message ?: "Unexpected error", e)
}

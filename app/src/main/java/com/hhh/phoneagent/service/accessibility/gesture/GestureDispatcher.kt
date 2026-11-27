package com.hhh.phoneagent.service.accessibility.gesture

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import com.hhh.phoneagent.core.common.AppError
import com.hhh.phoneagent.core.common.AppResult
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Dispatches touch gestures through the Accessibility Service and suspends until done. */
class GestureDispatcher(private val service: AccessibilityService) {

    suspend fun tap(x: Float, y: Float): AppResult<Unit> =
        dispatch(linePath(x, y, x + 1f, y), TAP_MILLIS)

    suspend fun longPress(x: Float, y: Float): AppResult<Unit> =
        dispatch(linePath(x, y, x + 1f, y), LONG_PRESS_MILLIS)

    suspend fun swipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMillis: Long = SWIPE_MILLIS,
    ): AppResult<Unit> = dispatch(linePath(startX, startY, endX, endY), durationMillis)

    private fun linePath(x1: Float, y1: Float, x2: Float, y2: Float): Path =
        Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }

    private suspend fun dispatch(path: Path, durationMillis: Long): AppResult<Unit> {
        val stroke = GestureDescription.StrokeDescription(
            path,
            0L,
            durationMillis.coerceIn(MIN_MILLIS, MAX_MILLIS),
        )
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        return suspendCancellableCoroutine<AppResult<Unit>> { continuation ->
            val accepted = service.dispatchGesture(
                gesture,
                object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        if (continuation.isActive) continuation.resume(AppResult.Success(Unit))
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        if (continuation.isActive) {
                            continuation.resume(
                                AppResult.Failure(AppError(AppError.ErrorCode.GESTURE_FAILED, "Gesture was cancelled")),
                            )
                        }
                    }
                },
                null,
            )
            if (!accepted && continuation.isActive) {
                continuation.resume(
                    AppResult.Failure(AppError(AppError.ErrorCode.GESTURE_FAILED, "System rejected the gesture")),
                )
            }
        }
    }

    private companion object {
        const val TAP_MILLIS = 60L
        const val LONG_PRESS_MILLIS = 600L
        const val SWIPE_MILLIS = 300L
        const val MIN_MILLIS = 1L
        const val MAX_MILLIS = 60_000L
    }
}

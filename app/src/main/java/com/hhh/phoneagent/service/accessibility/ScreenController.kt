package com.hhh.phoneagent.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.hhh.phoneagent.core.common.AppError.ErrorCode
import com.hhh.phoneagent.core.common.AppResult
import com.hhh.phoneagent.core.common.failure
import com.hhh.phoneagent.core.common.map
import com.hhh.phoneagent.core.common.runCatchingApp
import com.hhh.phoneagent.service.accessibility.eyes.NodeBounds
import com.hhh.phoneagent.service.accessibility.eyes.ScreenEyes
import com.hhh.phoneagent.service.accessibility.eyes.ScreenNode
import com.hhh.phoneagent.service.accessibility.eyes.ScreenSearch
import com.hhh.phoneagent.service.accessibility.eyes.ScreenSnapshot
import com.hhh.phoneagent.service.accessibility.gesture.GestureDispatcher

enum class GlobalAction(internal val code: Int) {
    BACK(AccessibilityService.GLOBAL_ACTION_BACK),
    HOME(AccessibilityService.GLOBAL_ACTION_HOME),
    RECENTS(AccessibilityService.GLOBAL_ACTION_RECENTS),
    NOTIFICATIONS(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS),
    QUICK_SETTINGS(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS),
}

/**
 * The public API for everything that touches the screen: read, find, click, type, scroll,
 * gesture and global actions. Every call returns an [AppResult] and never throws.
 */
class ScreenController(
    private val service: AccessibilityService,
    private val eyes: ScreenEyes,
) {
    private val gestures = GestureDispatcher(service)

    fun foregroundPackage(): String? {
        val root = service.rootInActiveWindow ?: return null
        return try {
            root.packageName?.toString()
        } finally {
            root.recycleCompat()
        }
    }

    fun snapshot(): AppResult<ScreenSnapshot> = runCatchingApp(ErrorCode.SERVICE_UNAVAILABLE) {
        val root = service.rootInActiveWindow ?: throw IllegalStateException("No active window")
        try {
            eyes.capture(root)
        } finally {
            root.recycleCompat()
        }
    }

    fun findNodes(query: String, limit: Int = 20): AppResult<List<ScreenNode>> =
        snapshot().map { ScreenSearch.find(it.root, query, limit) }

    suspend fun click(ref: String): AppResult<Unit> {
        val outcome = withNode(ref) { node ->
            val performed = performOnSelfOrAncestor(node, AccessibilityNodeInfo.ACTION_CLICK) { it.isClickable }
            Pair(performed, boundsOf(node))
        }
        return when (outcome) {
            is AppResult.Failure -> outcome
            is AppResult.Success -> {
                val (performed, bounds) = outcome.value
                if (performed) AppResult.Success(Unit) else tapFallback(bounds, longPress = false)
            }
        }
    }

    suspend fun longClick(ref: String): AppResult<Unit> {
        val outcome = withNode(ref) { node ->
            val performed = performOnSelfOrAncestor(node, AccessibilityNodeInfo.ACTION_LONG_CLICK) { it.isLongClickable }
            Pair(performed, boundsOf(node))
        }
        return when (outcome) {
            is AppResult.Failure -> outcome
            is AppResult.Success -> {
                val (performed, bounds) = outcome.value
                if (performed) AppResult.Success(Unit) else tapFallback(bounds, longPress = true)
            }
        }
    }

    suspend fun setText(ref: String, text: String): AppResult<Unit> {
        val result = withNode(ref) { node ->
            if (!node.isEditableCompat()) {
                false
            } else {
                node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                val args = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                }
                node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            }
        }
        return result.toUnit("Target is not an editable field, or it rejected the text")
    }

    fun scroll(ref: String, forward: Boolean): AppResult<Unit> {
        val action = if (forward) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }
        val result = withNode(ref) { node -> performOnSelfOrAncestor(node, action) { it.isScrollable } }
        return result.toUnit("No scrollable element at $ref")
    }

    fun global(action: GlobalAction): AppResult<Unit> =
        if (service.performGlobalAction(action.code)) {
            AppResult.Success(Unit)
        } else {
            failure(ErrorCode.ACTION_REJECTED, "Global action ${action.name} was rejected")
        }

    suspend fun tap(x: Int, y: Int): AppResult<Unit> = gestures.tap(x.toFloat(), y.toFloat())

    suspend fun longPress(x: Int, y: Int): AppResult<Unit> = gestures.longPress(x.toFloat(), y.toFloat())

    suspend fun swipe(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        durationMillis: Long = 300L,
    ): AppResult<Unit> = gestures.swipe(
        startX.toFloat(), startY.toFloat(), endX.toFloat(), endY.toFloat(), durationMillis,
    )

    private suspend fun tapFallback(bounds: NodeBounds, longPress: Boolean): AppResult<Unit> {
        if (bounds.isEmpty) return failure(ErrorCode.ACTION_REJECTED, "Element has no visible bounds")
        val x = bounds.centerX.toFloat()
        val y = bounds.centerY.toFloat()
        return if (longPress) gestures.longPress(x, y) else gestures.tap(x, y)
    }

    /**
     * Resolves [ref] to a live node, checks it still matches the snapshot, runs [block],
     * and recycles what it borrowed. Non-suspending on purpose: node calls are synchronous.
     */
    private inline fun <T> withNode(ref: String, block: (AccessibilityNodeInfo) -> T): AppResult<T> {
        val root = service.rootInActiveWindow
            ?: return failure(ErrorCode.SERVICE_UNAVAILABLE, "No active window")
        val node = eyes.resolve(root, ref)
        if (node == null) {
            root.recycleCompat()
            return failure(ErrorCode.NODE_NOT_FOUND, "No element with reference $ref")
        }
        return try {
            if (!eyes.verify(node, ref)) {
                failure(ErrorCode.STALE_REFERENCE, "Screen changed since the last snapshot; take a new snapshot")
            } else {
                AppResult.Success(block(node))
            }
        } catch (e: Exception) {
            failure(ErrorCode.UNKNOWN, e.message ?: "Action failed", e)
        } finally {
            node.recycleCompat()
            if (node !== root) root.recycleCompat()
        }
    }

    /** Performs [action] on [node] or the nearest ancestor matching [predicate]. */
    private fun performOnSelfOrAncestor(
        node: AccessibilityNodeInfo,
        action: Int,
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): Boolean {
        if (predicate(node)) return node.performAction(action)
        var current: AccessibilityNodeInfo? = node.parent
        var hops = 0
        while (current != null && hops < MAX_ANCESTOR_HOPS) {
            if (predicate(current)) {
                val performed = current.performAction(action)
                current.recycleCompat()
                return performed
            }
            val next = current.parent
            current.recycleCompat()
            current = next
            hops++
        }
        current?.recycleCompat()
        return false
    }

    private fun boundsOf(node: AccessibilityNodeInfo): NodeBounds {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        return NodeBounds(rect.left, rect.top, rect.right, rect.bottom)
    }

    private fun AppResult<Boolean>.toUnit(message: String): AppResult<Unit> = when (this) {
        is AppResult.Failure -> this
        is AppResult.Success -> if (value) AppResult.Success(Unit) else failure(ErrorCode.ACTION_REJECTED, message)
    }

    private companion object {
        const val MAX_ANCESTOR_HOPS = 8
    }
}

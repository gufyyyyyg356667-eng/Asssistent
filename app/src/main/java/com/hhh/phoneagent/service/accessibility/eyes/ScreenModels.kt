package com.hhh.phoneagent.service.accessibility.eyes

data class NodeBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2
    val isEmpty: Boolean get() = right <= left || bottom <= top
}

/**
 * One node of a screen snapshot. [ref] is a path such as "0.2.1", valid only for the
 * snapshot that produced it and checked again before every action.
 */
data class ScreenNode(
    val ref: String,
    val className: String?,
    val text: String?,
    val contentDescription: String?,
    val viewId: String?,
    val bounds: NodeBounds,
    val clickable: Boolean,
    val longClickable: Boolean,
    val editable: Boolean,
    val scrollable: Boolean,
    val enabled: Boolean,
    val children: List<ScreenNode>,
)

data class ScreenSnapshot(
    val packageName: String?,
    val capturedAtMillis: Long,
    val root: ScreenNode?,
    val nodeCount: Int,
    val truncated: Boolean,
)

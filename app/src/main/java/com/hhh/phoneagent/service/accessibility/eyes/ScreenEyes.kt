package com.hhh.phoneagent.service.accessibility.eyes

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.hhh.phoneagent.service.accessibility.isEditableCompat
import com.hhh.phoneagent.service.accessibility.recycleCompat
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the active window into [ScreenSnapshot] and maps refs back to live nodes.
 * Keeps a fingerprint for each ref from the latest capture, so actions can detect
 * that the screen changed under them.
 */
@Singleton
class ScreenEyes @Inject constructor() {

    private val lock = Any()
    private var fingerprints: Map<String, NodeFingerprint> = emptyMap()

    @Suppress("DEPRECATION") // getChild(int) is deprecated on API 33+ but required below it.
    fun capture(
        rootNode: AccessibilityNodeInfo,
        maxNodes: Int = MAX_NODES,
        maxDepth: Int = MAX_DEPTH,
    ): ScreenSnapshot {
        val prints = HashMap<String, NodeFingerprint>()
        var count = 0
        var truncated = false

        fun build(node: AccessibilityNodeInfo, ref: String, depth: Int): ScreenNode {
            count++
            prints[ref] = fingerprintOf(node)
            val rect = Rect()
            node.getBoundsInScreen(rect)
            val children = mutableListOf<ScreenNode>()
            val total = node.childCount
            if (depth < maxDepth) {
                for (i in 0 until total) {
                    if (count >= maxNodes) {
                        truncated = true
                        break
                    }
                    val child = node.getChild(i) ?: continue
                    try {
                        children += build(child, "$ref.$i", depth + 1)
                    } finally {
                        child.recycleCompat()
                    }
                }
            } else if (total > 0) {
                truncated = true
            }
            return ScreenNode(
                ref = ref,
                className = node.className?.toString(),
                text = node.text?.toString(),
                contentDescription = node.contentDescription?.toString(),
                viewId = node.viewIdResourceName,
                bounds = NodeBounds(rect.left, rect.top, rect.right, rect.bottom),
                clickable = node.isClickable,
                longClickable = node.isLongClickable,
                editable = node.isEditableCompat(),
                scrollable = node.isScrollable,
                enabled = node.isEnabled,
                children = children,
            )
        }

        val tree = build(rootNode, ROOT_REF, 0)
        synchronized(lock) { fingerprints = prints }

        return ScreenSnapshot(
            packageName = rootNode.packageName?.toString(),
            capturedAtMillis = System.currentTimeMillis(),
            root = tree,
            nodeCount = count,
            truncated = truncated,
        )
    }

    /**
     * Walks [ref] from [rootNode]. If the ref is the root, returns [rootNode] itself and the
     * caller must not recycle it twice. Otherwise the returned node is owned by the caller.
     */
    @Suppress("DEPRECATION")
    fun resolve(rootNode: AccessibilityNodeInfo, ref: String): AccessibilityNodeInfo? {
        val parts = ref.split('.')
        if (parts.firstOrNull() != ROOT_REF) return null

        var current: AccessibilityNodeInfo = rootNode
        var owned = false
        for (part in parts.drop(1)) {
            val index = part.toIntOrNull()
            val next = if (index != null && index >= 0) current.getChild(index) else null
            if (owned) current.recycleCompat()
            if (next == null) return null
            current = next
            owned = true
        }
        return current
    }

    /** True only if the live node still matches what the snapshot recorded for [ref]. */
    fun verify(node: AccessibilityNodeInfo, ref: String): Boolean {
        val expected = synchronized(lock) { fingerprints[ref] } ?: return false
        return expected == fingerprintOf(node)
    }

    private fun fingerprintOf(node: AccessibilityNodeInfo) = NodeFingerprint(
        className = node.className?.toString(),
        text = node.text?.toString(),
        viewId = node.viewIdResourceName,
    )

    private data class NodeFingerprint(val className: String?, val text: String?, val viewId: String?)

    companion object {
        const val ROOT_REF = "0"
        const val MAX_NODES = 400
        const val MAX_DEPTH = 32
    }
}

package com.hhh.phoneagent.service.accessibility.eyes

/** Depth-first lookup of a node by its ref within a snapshot tree. */
fun ScreenNode?.findByRef(ref: String): ScreenNode? {
    if (this == null) return null
    if (this.ref == ref) return this
    for (child in children) {
        val hit = child.findByRef(ref)
        if (hit != null) return hit
    }
    return null
}

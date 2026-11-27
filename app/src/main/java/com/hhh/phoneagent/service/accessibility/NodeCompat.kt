package com.hhh.phoneagent.service.accessibility

import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo

/** recycle() is a no-op on API 33+ and deprecated there, so only call it below that. */
@Suppress("DEPRECATION")
internal fun AccessibilityNodeInfo.recycleCompat() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) recycle()
}

/** AccessibilityNodeInfo.isEditable exists only from API 28; minSdk here is 26. */
internal fun AccessibilityNodeInfo.isEditableCompat(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        isEditable
    } else {
        val name = className?.toString().orEmpty()
        name.contains("EditText") || name.contains("TextField")
    }

package com.mark.android

import android.app.Notification
import android.service.notification.NotificationListenerService

/** Lets Mark read and clear notifications by voice. Needs "Notification access" turned on once. */
class NotifService : NotificationListenerService() {
    companion object { var instance: NotifService? = null }
    override fun onListenerConnected() { instance = this }
    override fun onListenerDisconnected() { instance = null }

    fun recent(): String {
        val l = (activeNotifications ?: return "No notifications.").filter { !it.isOngoing || it.notification.category == Notification.CATEGORY_MESSAGE }
            .sortedByDescending { it.postTime }.take(15)
        if (l.isEmpty()) return "No notifications."
        val pm = packageManager
        return l.joinToString("\n") { sbn ->
            val app = try { pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString() } catch (e: Exception) { sbn.packageName }
            val ex = sbn.notification.extras
            val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
            val text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
            "$app: $title — ${text.take(200)}"
        }
    }
    fun clearAll() { cancelAllNotifications() }
}

package de.snowworks.ariana.notify

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import de.snowworks.ariana.ArianaGate
import de.snowworks.ariana.Feature
import de.snowworks.ariana.session.SessionRegistry

/**
 * Reads notifications only after explicit Android listener permission and an
 * active local feature session. Content remains in a bounded in-memory store.
 */
class ArianaNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn ?: return
        val gate = ArianaGate(this)
        if (!gate.isMasterEnabled || gate.isBlocked) return
        if (!SessionRegistry.isActive(Feature.NOTIFY_READ)) return
        NotificationStore.add(this, notification)
    }
}

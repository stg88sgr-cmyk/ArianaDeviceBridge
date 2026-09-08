package de.snowworks.ariana.notify

import android.app.Notification
import android.content.Context
import android.service.notification.StatusBarNotification
import java.util.ArrayDeque

data class NotificationEntry(
    val packageName: String,
    val appLabel: String,
    val title: String?,
    val text: String?,
    val postTime: Long,
    val key: String,
)

/** In-memory only, bounded local notification history. */
object NotificationStore {
    private const val MAX_ENTRIES = 100
    private val entries = ArrayDeque<NotificationEntry>()
    private val lock = Any()

    fun add(context: Context, sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val appLabel = runCatching {
            val info = context.packageManager.getApplicationInfo(sbn.packageName, 0)
            context.packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(sbn.packageName)

        synchronized(lock) {
            entries.addFirst(
                NotificationEntry(
                    packageName = sbn.packageName,
                    appLabel = appLabel,
                    title = title,
                    text = text,
                    postTime = sbn.postTime,
                    key = sbn.key,
                ),
            )
            while (entries.size > MAX_ENTRIES) entries.removeLast()
        }
    }

    fun listRecent(): List<NotificationEntry> = synchronized(lock) { entries.toList() }

    fun clear() = synchronized(lock) { entries.clear() }
}

package de.snowworks.ariana.xspace

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import de.snowworks.app.R

/** Visible fallback for approval requests created while the app is not foreground. */
object XSpaceApprovalNotifier {
    private const val CHANNEL_ID = "x88_xspace_approvals"
    private const val CHANNEL_NAME = "Ariana X-88 Freigaben"

    fun post(context: Context, review: XSpaceApprovalRequestStore.ReviewRequest) {
        val app = context.applicationContext
        ensureChannel(app)
        if (!NotificationManagerCompat.from(app).areNotificationsEnabled()) return

        val intent = Intent(app, XSpaceApprovalActivity::class.java)
            .putExtra("proposalId", review.proposalId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(
            app,
            notificationId(review.proposalId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_snowworks)
            .setContentTitle("Ariana X-88 · Freigabe erforderlich")
            .setContentText(review.summary.take(180))
            .setStyle(NotificationCompat.BigTextStyle().bigText(review.summary))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setTimeoutAfter((review.expiresAtMs - System.currentTimeMillis()).coerceAtLeast(1_000L))
            .build()

        NotificationManagerCompat.from(app).notify(notificationId(review.proposalId), notification)
    }

    fun cancel(context: Context, proposalId: String) {
        NotificationManagerCompat.from(context.applicationContext)
            .cancel(notificationId(proposalId))
    }

    fun cancelAll(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.activeNotifications
            .filter { it.notification.channelId == CHANNEL_ID }
            .forEach { manager.cancel(it.id) }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Sichtbare Bestätigungen für externe X-Space-Aktionen"
                setShowBadge(true)
            },
        )
    }

    private fun notificationId(proposalId: String): Int =
        0x58000000 or (proposalId.hashCode() and 0x00FFFFFF)
}

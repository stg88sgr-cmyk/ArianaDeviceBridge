package de.snowworks.ariana.presence

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import de.snowworks.app.R

/**
 * Local, quiet presence signal for Ariana.
 *
 * This intentionally does not request notification permission, bypass DND,
 * play sounds, or vibrate. It only surfaces a small local status when the
 * user has already allowed notifications for the app.
 */
object PresenceSignalController {
    private const val CHANNEL_ID = "ariana_presence"
    private const val CHANNEL_NAME = "Ariana Presence"
    private const val NOTIFICATION_ID = 8801

    enum class State(val wireName: String, val title: String, val text: String) {
        THINKING("thinking", "Ariana", "Denkt …"),
        DONE("done", "Ariana", "Fertig"),
        ATTENTION("attention", "Ariana", "Aufmerksamkeit erforderlich"),
        QUIET("quiet", "Ariana", "Ruhiger Modus"),
        TEST("test", "Ariana", "Presence Signal Test")
    }

    @Volatile
    private var currentState: State? = null

    fun currentState(): String = currentState?.wireName ?: "clear"

    fun emit(context: Context, state: State): Boolean {
        val app = context.applicationContext
        currentState = state
        ensureChannel(app)

        if (!canPostNotifications(app)) return false

        val notification = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_snowworks)
            .setContentTitle(state.title)
            .setContentText(state.text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setAutoCancel(false)
            .setOngoing(state == State.THINKING)
            .build()

        return runCatching {
            NotificationManagerCompat.from(app).notify(NOTIFICATION_ID, notification)
            true
        }.getOrDefault(false)
    }

    fun clear(context: Context) {
        currentState = null
        NotificationManagerCompat.from(context.applicationContext).cancel(NOTIFICATION_ID)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Quiet local status signals from Ariana"
            setSound(null, null)
            enableVibration(false)
            enableLights(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun canPostNotifications(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        }
        return true
    }
}

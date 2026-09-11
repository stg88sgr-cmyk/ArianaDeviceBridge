package de.snowworks.presencelite

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private val channelId = "snowworks_presence_state_v2"
    private val quietChannelId = "snowworks_presence_quiet_v2"
    private val requestCodeNotifications = 1001
    private val presenceNotificationId = 100
    private var pendingSignal: PresenceSignal? = null

    private enum class PresenceSignal(
        val title: String,
        val text: String,
        val quiet: Boolean = false
    ) {
        TEST("Ariana · Presence Test", "Signalweg funktioniert."),
        THINKING("Ariana · Thinking", "Ich arbeite."),
        DONE("Ariana · Done", "Fertig."),
        ATTENTION("Ariana · Attention", "Bitte ansehen."),
        QUIET("Ariana · Quiet", "Ruhemodus aktiv.", quiet = true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createChannels()
        setContentView(buildUi())
    }

    private fun buildUi(): ScrollView {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(8, 9, 13))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(48, 80, 48, 80)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val title = TextView(this).apply {
            text = "ARIANA · Presence Lite v2.3"
            textSize = 28f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        val subtitle = TextView(this).apply {
            text = "Genau ein Ariana-Status gleichzeitig. Nur Benachrichtigungen. Kein Internet, keine Kamera, kein Mikrofon, kein Standort, kein Accessibility."
            textSize = 16f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 36)
        }

        root.addView(title)
        root.addView(subtitle)
        root.addView(signalButton("PRESENCE TEST", PresenceSignal.TEST))
        root.addView(signalButton("THINKING", PresenceSignal.THINKING))
        root.addView(signalButton("DONE", PresenceSignal.DONE))
        root.addView(signalButton("ATTENTION", PresenceSignal.ATTENTION))
        root.addView(signalButton("QUIET", PresenceSignal.QUIET))
        root.addView(actionButton("CLEAR") { clearPresence() })

        scroll.addView(root)
        return scroll
    }

    private fun signalButton(label: String, signal: PresenceSignal): Button =
        actionButton(label) { requestOrSend(signal) }

    private fun actionButton(label: String, action: () -> Unit): Button {
        return Button(this).apply {
            text = label
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 12
            }
        }
    }

    private fun requestOrSend(signal: PresenceSignal) {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingSignal = signal
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), requestCodeNotifications)
            return
        }
        sendSignal(signal)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == requestCodeNotifications &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        ) {
            pendingSignal?.let { sendSignal(it) }
        } else {
            Toast.makeText(this, "Benachrichtigung nicht erlaubt.", Toast.LENGTH_SHORT).show()
        }
        pendingSignal = null
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            val stateChannel = NotificationChannel(
                channelId,
                "Ariana Presence",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Lokale Ariana Presence Statussignale"
                enableVibration(false)
                setSound(null, null)
            }

            val quietChannel = NotificationChannel(
                quietChannelId,
                "Ariana Presence Quiet",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Leiser Ariana Presence Status"
                enableVibration(false)
                setSound(null, null)
            }

            manager.createNotificationChannel(stateChannel)
            manager.createNotificationChannel(quietChannel)
        }
    }

    private fun sendSignal(signal: PresenceSignal) {
        val manager = getSystemService(NotificationManager::class.java)

        // Presence Lite owns only Presence notifications. Clear all app notifications first
        // so legacy IDs, channel changes, or previous test builds can never stack.
        manager.cancelAll()

        val targetChannel = if (signal.quiet) quietChannelId else channelId
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.Notification.Builder(this, targetChannel)
        } else {
            @Suppress("DEPRECATION")
            android.app.Notification.Builder(this)
        }

        val notification = builder
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(signal.title)
            .setContentText(signal.text)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .build()

        manager.notify(presenceNotificationId, notification)
        Toast.makeText(this, "${signal.name.lowercase()} aktiv.", Toast.LENGTH_SHORT).show()
    }

    private fun clearPresence() {
        getSystemService(NotificationManager::class.java).cancelAll()
        Toast.makeText(this, "Presence gelöscht.", Toast.LENGTH_SHORT).show()
    }
}

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
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private val channelId = "snowworks_presence_test"
    private val requestCodeNotifications = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createChannel()
        setContentView(buildUi())
    }

    private fun buildUi(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            setBackgroundColor(Color.rgb(8, 9, 13))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val title = TextView(this).apply {
            text = "ARIANA · Presence Lite"
            textSize = 28f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        val subtitle = TextView(this).apply {
            text = "Nur Benachrichtigungen. Kein Internet, keine Kamera, kein Mikrofon, kein Standort, kein Accessibility."
            textSize = 16f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 36)
        }

        val testButton = Button(this).apply {
            text = "Presence Test"
            setOnClickListener { requestOrSendTest() }
        }

        root.addView(title)
        root.addView(subtitle)
        root.addView(testButton)
        return root
    }

    private fun requestOrSendTest() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), requestCodeNotifications)
            return
        }
        sendTestNotification()
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
            sendTestNotification()
        } else {
            Toast.makeText(this, "Benachrichtigung nicht erlaubt.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                channelId,
                "Ariana Presence Test",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Lokaler Testkanal für Ariana Presence Signal"
                enableVibration(false)
                setSound(null, null)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun sendTestNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.Notification.Builder(this, channelId)
        } else {
            @Suppress("DEPRECATION")
            android.app.Notification.Builder(this)
        }

        val notification = builder
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Ariana · Presence Test")
            .setContentText("Signalweg funktioniert.")
            .setAutoCancel(true)
            .build()

        manager.notify(100, notification)
        Toast.makeText(this, "Presence Test gesendet.", Toast.LENGTH_SHORT).show()
    }
}

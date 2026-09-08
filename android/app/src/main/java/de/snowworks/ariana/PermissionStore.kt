package de.snowworks.ariana

import android.Manifest
import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import de.snowworks.ariana.files.TreePermissionStore

class PermissionStore(private val context: Context) {

    fun isGranted(feature: Feature): Boolean = when (feature) {
        Feature.CAMERA -> granted(Manifest.permission.CAMERA)
        Feature.MICROPHONE -> granted(Manifest.permission.RECORD_AUDIO)
        Feature.NOTIFY_SEND -> notificationSendGranted()
        Feature.NOTIFY_READ -> notificationListenerEnabled()
        Feature.SCREEN -> false
        Feature.FILES -> TreePermissionStore(context).get() != null
        Feature.LOCATION ->
            granted(Manifest.permission.ACCESS_FINE_LOCATION) ||
                granted(Manifest.permission.ACCESS_COARSE_LOCATION)
        Feature.BLUETOOTH -> bluetoothGranted()
    }

    fun label(feature: Feature): String = when {
        feature == Feature.NOTIFY_READ && !notificationListenerEnabled() ->
            "Systemeinstellung nötig"
        feature == Feature.SCREEN ->
            "MediaProjection-Dialog bei jeder Sitzung"
        feature == Feature.FILES && isGranted(feature) ->
            "Ordner freigegeben"
        feature == Feature.FILES ->
            "Ordnerauswahl nötig"
        isGranted(feature) -> "Erteilt"
        else -> "Nicht erteilt"
    }

    fun runtimePermissions(feature: Feature): Array<String> = when (feature) {
        Feature.CAMERA -> arrayOf(Manifest.permission.CAMERA)
        Feature.MICROPHONE -> arrayOf(Manifest.permission.RECORD_AUDIO)
        Feature.NOTIFY_SEND ->
            if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray()
        Feature.LOCATION -> arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        Feature.BLUETOOTH ->
            if (Build.VERSION.SDK_INT >= 31) {
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
            } else {
                emptyArray()
            }
        else -> emptyArray()
    }

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun notificationSendGranted(): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return if (Build.VERSION.SDK_INT >= 33) {
            granted(Manifest.permission.POST_NOTIFICATIONS) && nm.areNotificationsEnabled()
        } else {
            nm.areNotificationsEnabled()
        }
    }

    private fun notificationListenerEnabled(): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ) ?: return false
        return flat.contains(context.packageName)
    }

    private fun bluetoothGranted(): Boolean {
        if (Build.VERSION.SDK_INT >= 31) {
            return granted(Manifest.permission.BLUETOOTH_CONNECT)
        }
        val mgr = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return mgr?.adapter != null
    }
}

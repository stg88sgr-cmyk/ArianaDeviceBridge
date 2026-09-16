package de.snowworks.ariana.apk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller

class ApkInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INSTALL_STATUS) return

        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirmIntent = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                confirmIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (confirmIntent != null) context.startActivity(confirmIntent)
            }
            PackageInstaller.STATUS_SUCCESS -> {
                // Success is observable via package state on the next bridge health check.
            }
            else -> {
                // Failure detail remains available in PackageInstaller.EXTRA_STATUS_MESSAGE.
            }
        }
    }

    companion object {
        const val ACTION_INSTALL_STATUS = "de.snowworks.app.APK_INSTALL_STATUS"
        const val EXTRA_PACKAGE_NAME = "package_name"
    }
}

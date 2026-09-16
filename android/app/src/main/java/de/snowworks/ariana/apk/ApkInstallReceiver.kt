package de.snowworks.ariana.apk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller

class ApkInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INSTALL_STATUS) return

        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)
        val sessionId = intent.getIntExtra(EXTRA_SESSION_ID, -1).takeIf { it >= 0 }
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val store = ApkInstallStatusStore(context)

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                store.markPendingUserAction(packageName, sessionId, message)
                @Suppress("DEPRECATION")
                val confirmIntent = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                confirmIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (confirmIntent != null) context.startActivity(confirmIntent)
            }
            PackageInstaller.STATUS_SUCCESS -> {
                store.markSuccess(packageName, sessionId, message)
            }
            else -> {
                store.markFailure(packageName, sessionId, status, message)
            }
        }
    }

    companion object {
        const val ACTION_INSTALL_STATUS = "de.snowworks.app.APK_INSTALL_STATUS"
        const val EXTRA_PACKAGE_NAME = "package_name"
        const val EXTRA_SESSION_ID = "session_id"
    }
}

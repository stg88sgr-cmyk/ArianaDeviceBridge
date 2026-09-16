package de.snowworks.ariana.bridge

import android.content.Context
import android.content.Intent
import android.provider.Settings
import de.snowworks.ariana.apk.ApkInstaller
import de.snowworks.ariana.apk.ApkManager

/**
 * Executes the small, explicit set of local device actions that have already
 * passed ActionPolicy and a visible user confirmation.
 *
 * The exact approval grant is consumed immediately before execution. A grant
 * cannot be reused, cannot authorize a different action, and unsupported
 * actions are rejected before any grant is consumed.
 */
object LocalDeviceActionExecutor {
    data class Result(
        val ok: Boolean,
        val error: String? = null,
    )

    fun execute(context: Context, grantId: String, action: String): Result {
        if (action !in SUPPORTED_ACTIONS) {
            return Result(false, "ACTION_NOT_IMPLEMENTED")
        }

        val grant = ActionApprovalStore.consumeGrant(
            grantId = grantId,
            action = action,
        ) ?: return Result(false, "APPROVAL_INVALID_OR_EXPIRED")

        return when (grant.action) {
            ACTION_OPEN_SETTINGS -> openSettings(context.applicationContext)
            ACTION_APK_INSTALL_LATEST -> installLatestTrustedApk(context.applicationContext)
            else -> Result(false, "ACTION_NOT_IMPLEMENTED")
        }
    }

    private fun openSettings(context: Context): Result = runCatching {
        context.startActivity(
            Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        Result(true)
    }.getOrElse { error ->
        Result(false, error.javaClass.simpleName)
    }

    private fun installLatestTrustedApk(context: Context): Result = runCatching {
        val manager = ApkManager(context)
        val staged = manager.stageLatest() ?: return Result(false, "NO_APK_DOWNLOAD_FOUND")
        val install = ApkInstaller(context).startTrustedInstall(staged)
        if (install.ok) {
            manager.clearStaging(keepFileName = staged.name)
            Result(true)
        } else {
            Result(false, install.reason ?: "APK_INSTALL_REJECTED")
        }
    }.getOrElse { error ->
        Result(false, error.javaClass.simpleName)
    }

    const val ACTION_OPEN_SETTINGS = "open_settings"
    const val ACTION_APK_INSTALL_LATEST = "apk_install_latest"

    private val SUPPORTED_ACTIONS = setOf(
        ACTION_OPEN_SETTINGS,
        ACTION_APK_INSTALL_LATEST,
    )
}

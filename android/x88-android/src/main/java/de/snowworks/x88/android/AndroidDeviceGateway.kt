package de.snowworks.x88.android

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.util.UUID

class AndroidDeviceGateway(
    private val context: Context,
    private val controls: AndroidControlRegistry = AndroidControlRegistry(),
) {
    fun execute(request: AndroidActionRequest): AndroidActionResult {
        val auditId = "x88-${UUID.randomUUID()}"

        if (request.actor.isBlank() || request.reason.isBlank()) {
            return AndroidActionResult.Denied(request.action, auditId)
        }
        if (!controls.requireAllowed(request)) {
            return AndroidActionResult.Denied(request.action, auditId)
        }

        return runCatching {
            when (request.action) {
                AndroidAction.READ_DEVICE_INFO -> AndroidActionResult.Allowed(
                    AndroidDevice(
                        manufacturer = Build.MANUFACTURER,
                        model = Build.MODEL,
                        sdkInt = Build.VERSION.SDK_INT,
                        release = Build.VERSION.RELEASE,
                    ),
                    auditId,
                )
                AndroidAction.LIST_APPS -> {
                    val apps = context.packageManager
                        .getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
                        .map {
                            AndroidApp(
                                packageName = AndroidPackageName(it.packageName),
                                label = context.packageManager.getApplicationLabel(it).toString(),
                                versionName = null,
                                enabled = it.enabled,
                            )
                        }
                        .sortedBy { it.label.lowercase() }
                    AndroidActionResult.Allowed(apps, auditId)
                }
                AndroidAction.OPEN_APP,
                AndroidAction.REQUEST_PERMISSION ->
                    AndroidActionResult.Failed(
                        request.action,
                        "Capability is intentionally not implemented in V36.",
                        auditId,
                    )
            }
        }.getOrElse {
            AndroidActionResult.Failed(
                request.action,
                it.message ?: "Android operation failed",
                auditId,
            )
        }
    }
}

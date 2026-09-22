package de.snowworks.x88.android

@JvmInline value class AndroidPackageName(val value: String)
@JvmInline value class AndroidPermissionName(val value: String)
@JvmInline value class AndroidActionId(val value: String)

data class AndroidApp(
    val packageName: AndroidPackageName,
    val label: String,
    val versionName: String?,
    val enabled: Boolean,
)

data class AndroidDevice(
    val manufacturer: String,
    val model: String,
    val sdkInt: Int,
    val release: String,
)

enum class AndroidPermission {
    READ_NOTIFICATIONS,
    POST_NOTIFICATIONS,
    ACCESSIBILITY_SERVICE,
    SYSTEM_ALERT_WINDOW,
}

enum class AndroidAction {
    OPEN_APP,
    LIST_APPS,
    READ_DEVICE_INFO,
    REQUEST_PERMISSION,
}

data class AndroidActionRequest(
    val id: AndroidActionId,
    val action: AndroidAction,
    val actor: String,
    val reason: String,
    val packageName: AndroidPackageName? = null,
)

sealed interface AndroidActionResult {
    data class Allowed(val value: Any?, val auditId: String) : AndroidActionResult
    data class Denied(val action: AndroidAction, val auditId: String) : AndroidActionResult
    data class Failed(val action: AndroidAction, val message: String, val auditId: String) : AndroidActionResult
}

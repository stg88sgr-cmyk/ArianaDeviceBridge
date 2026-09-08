package de.snowworks.ariana

sealed class ArianaResult<out T> {
    data class Ok<T>(val data: T) : ArianaResult<T>()
    data class Err(val error: ArianaError) : ArianaResult<Nothing>()
}

data class ArianaError(
    val code: Code,
    val message: String,
) {
    enum class Code {
        MASTER_OFF,
        BLOCKED,
        DENIED,
        UNAVAILABLE,
        ANDROID_SETTINGS,
        ABORTED,
        SECURITY,
        UNKNOWN,
    }
}

data class FeatureStatus(
    val feature: Feature,
    val permissionGranted: Boolean,
    val permissionLabel: String,
    val enabled: Boolean,
    val sessionActive: Boolean,
    val detail: String?,
)

data class ConnectionState(
    val connected: Boolean,
    val label: String,
    val detail: String,
) {
    companion object {
        val DISCONNECTED = ConnectionState(
            connected = false,
            label = "Nicht verbunden",
            detail = "Berechtigung allein stellt keine Verbindung zu ChatGPT oder einem anderen KI-Dienst her. Ein aktiver Kanal ist nicht implementiert.",
        )
    }
}

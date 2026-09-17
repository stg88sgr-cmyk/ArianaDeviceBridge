package de.snowworks.ariana.xx88y

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class X88StateHolder(initial: X88State = defaultState()) {
    private val mutableState = MutableStateFlow(initial)
    val state: StateFlow<X88State> = mutableState.asStateFlow()

    fun setMasterEnabled(enabled: Boolean) {
        if (mutableState.value.emergencyStopActive && enabled) return
        mutableState.value = mutableState.value.copy(masterEnabled = enabled)
        audit("ARIANA CORE", "Core", Severity.INFO, "Master Control = $enabled")
    }

    fun activateEmergencyStop() {
        mutableState.value = mutableState.value.copy(
            emergencyStopActive = true,
            masterEnabled = false,
        )
        audit("ARIANA CORE", "Security", Severity.CRITICAL, "Emergency Stop activated")
    }

    fun clearEmergencyStop() {
        mutableState.value = mutableState.value.copy(emergencyStopActive = false)
        audit("ARIANA CORE", "Security", Severity.WARNING, "Emergency Stop cleared")
    }

    fun updateBuild(build: BuildState) {
        mutableState.value = mutableState.value.copy(build = build)
    }

    fun audit(
        source: String,
        category: String,
        severity: Severity,
        message: String,
        metadata: Map<String, String> = emptyMap(),
    ) {
        val event = AuditEvent(
            source = source,
            category = category,
            severity = severity,
            message = message,
            metadata = metadata,
        )
        mutableState.value = mutableState.value.copy(
            auditEvents = (listOf(event) + mutableState.value.auditEvents).take(500),
        )
    }

    companion object {
        fun defaultState(): X88State {
            val capabilities = listOf(
                "camera" to "Kamera",
                "microphone" to "Mikrofon",
                "screen" to "Bildschirmfreigabe",
                "notifications" to "Benachrichtigungen",
                "accessibility" to "Accessibility",
                "files" to "Dateien",
                "location" to "Standort",
                "bluetooth" to "Bluetooth",
            ).map { (id, title) -> CapabilityState(id = id, title = title) }

            val workers = listOf(
                WorkerState("ariana", "ARIANA CORE", "Internal", WorkerStatus.IDLE, enabled = true),
                WorkerState("luna", "LUNA XXY", "Adapter"),
                WorkerState("meta", "META", "External Provider"),
                WorkerState("claude", "CLAUDE", "External Provider"),
                WorkerState("local", "LOCAL MODEL", "Local Provider"),
            )

            val sessions = listOf(
                "x88-bridge", "x88-build", "x88-logs", "luna-worker",
                "luna-bridge", "luna-build", "luna-xxy-worker", "luna-logs",
            ).map(::RuntimeSession)

            val providers = listOf(
                ProviderState("image", "Image Provider", ProviderType.IMAGE),
                ProviderState("video", "Video Provider", ProviderType.VIDEO),
                ProviderState("audio", "Audio Provider", ProviderType.AUDIO),
                ProviderState("voice", "Voice Provider", ProviderType.VOICE),
            )

            return X88State(
                capabilities = capabilities,
                workers = workers,
                runtimeSessions = sessions,
                providers = providers,
            )
        }
    }
}

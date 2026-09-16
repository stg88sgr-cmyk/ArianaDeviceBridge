package de.snowworks.ariana.universal.v2

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ArianaRuntimeState(
    val mode: ArianaMode = ArianaMode.HOME,
    val activeProjectId: String? = null,
    val bridgeConnected: Boolean = false,
    val masterEnabled: Boolean = true,
    val emergencyStopActive: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
)

class ArianaRuntimeStateHolder {
    private val _state = MutableStateFlow(ArianaRuntimeState())
    val state: StateFlow<ArianaRuntimeState> = _state.asStateFlow()

    fun update(transform: (ArianaRuntimeState) -> ArianaRuntimeState) {
        _state.value = transform(_state.value).copy(updatedAt = System.currentTimeMillis())
    }
}

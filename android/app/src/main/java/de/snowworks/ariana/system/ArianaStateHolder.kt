package de.snowworks.ariana.system

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class GateStatus {
    UNKNOWN,
    GREEN,
    YELLOW,
    RED,
    BLOCKED,
}

enum class RuntimeMode {
    STOPPED,
    STARTING,
    RUNNING,
    DEGRADED,
    QUARANTINE,
    EMERGENCY_STOP,
}

enum class ReleaseStatus {
    UNKNOWN,
    BLOCKED,
    READY,
    VERIFIED,
}

data class SecurityGateState(
    val gate1InputFirewall: GateStatus = GateStatus.UNKNOWN,
    val gate2OutputFirewall: GateStatus = GateStatus.UNKNOWN,
    val gate3Authenticity: GateStatus = GateStatus.UNKNOWN,
    val gate4Authorization: GateStatus = GateStatus.UNKNOWN,
    val gate5Execution: GateStatus = GateStatus.UNKNOWN,
    val gate6Mutation: GateStatus = GateStatus.UNKNOWN,
    val gate7Attestation: GateStatus = GateStatus.UNKNOWN,
    val gate8HardwareIdentity: GateStatus = GateStatus.UNKNOWN,
    val gate9PeerTrust: GateStatus = GateStatus.UNKNOWN,
    val gate10Recovery: GateStatus = GateStatus.UNKNOWN,
    val gate11PolicyLock: GateStatus = GateStatus.UNKNOWN,
    val gate12CircuitBreaker: GateStatus = GateStatus.GREEN,
    val gate13ReleaseGate: GateStatus = GateStatus.UNKNOWN,
) {
    fun allGreen(): Boolean = listOf(
        gate1InputFirewall,
        gate2OutputFirewall,
        gate3Authenticity,
        gate4Authorization,
        gate5Execution,
        gate6Mutation,
        gate7Attestation,
        gate8HardwareIdentity,
        gate9PeerTrust,
        gate10Recovery,
        gate11PolicyLock,
        gate12CircuitBreaker,
        gate13ReleaseGate,
    ).all { it == GateStatus.GREEN }
}

/**
 * Canonical in-process state source for the X-88 runtime.
 *
 * This holder is deliberately not a replacement for cryptographic verification.
 * Gate implementations still verify signatures, hashes, attestations and policy
 * independently. The holder only publishes the resulting runtime state.
 */
class ArianaStateHolder(
    initialState: X88CoreState = X88CoreState(),
) {
    private val mutableState = MutableStateFlow(initialState)

    val state: StateFlow<X88CoreState> = mutableState.asStateFlow()

    fun snapshot(): X88CoreState = mutableState.value

    fun update(transform: (X88CoreState) -> X88CoreState): X88CoreState {
        mutableState.update(transform)
        return mutableState.value
    }

    fun setMasterEnabled(enabled: Boolean): X88CoreState = update { current ->
        if (enabled && (current.emergencyStopActive || current.quarantineActive)) {
            current.copy(
                masterEnabled = false,
                releaseStatus = ReleaseStatus.BLOCKED,
                lastError = "master_enable_blocked",
                lastTransition = "MASTER_BLOCKED",
            )
        } else {
            current.copy(
                masterEnabled = enabled,
                runtimeMode = if (enabled) RuntimeMode.STARTING else RuntimeMode.STOPPED,
                lastTransition = if (enabled) "MASTER_ENABLED" else "MASTER_DISABLED",
            )
        }
    }

    fun setEmergencyStop(active: Boolean, reason: String? = null): X88CoreState = update { current ->
        if (active) {
            current.copy(
                masterEnabled = false,
                emergencyStopActive = true,
                runtimeMode = RuntimeMode.EMERGENCY_STOP,
                bridgeConnected = false,
                releaseStatus = ReleaseStatus.BLOCKED,
                security = current.security.copy(
                    gate12CircuitBreaker = GateStatus.BLOCKED,
                    gate13ReleaseGate = GateStatus.BLOCKED,
                ),
                lastError = reason ?: "emergency_stop_active",
                lastTransition = "EMERGENCY_STOP",
            )
        } else {
            current.copy(
                emergencyStopActive = false,
                runtimeMode = RuntimeMode.STOPPED,
                releaseStatus = ReleaseStatus.UNKNOWN,
                security = current.security.copy(
                    gate12CircuitBreaker = GateStatus.GREEN,
                    gate13ReleaseGate = GateStatus.UNKNOWN,
                ),
                lastTransition = "EMERGENCY_STOP_CLEARED",
            )
        }
    }

    fun enterQuarantine(reason: String): X88CoreState = update { current ->
        current.copy(
            masterEnabled = false,
            quarantineActive = true,
            runtimeMode = RuntimeMode.QUARANTINE,
            releaseStatus = ReleaseStatus.BLOCKED,
            security = current.security.copy(
                gate10Recovery = GateStatus.RED,
                gate13ReleaseGate = GateStatus.BLOCKED,
            ),
            lastError = reason,
            lastTransition = "QUARANTINE_ENTER",
        )
    }

    fun clearQuarantine(): X88CoreState = update { current ->
        current.copy(
            quarantineActive = false,
            runtimeMode = RuntimeMode.STOPPED,
            releaseStatus = ReleaseStatus.UNKNOWN,
            security = current.security.copy(
                gate10Recovery = GateStatus.UNKNOWN,
                gate13ReleaseGate = GateStatus.UNKNOWN,
            ),
            lastTransition = "QUARANTINE_CLEARED",
        )
    }

    fun updateSecurity(transform: (SecurityGateState) -> SecurityGateState): X88CoreState = update { current ->
        val security = transform(current.security)
        current.copy(
            security = security,
            releaseStatus = when {
                current.emergencyStopActive || current.quarantineActive -> ReleaseStatus.BLOCKED
                security.allGreen() -> ReleaseStatus.VERIFIED
                else -> current.releaseStatus
            },
            lastTransition = "SECURITY_STATE_UPDATED",
        )
    }

    fun healthGreen(nowEpochMs: Long = System.currentTimeMillis()): X88CoreState = update { current ->
        current.copy(
            lastHealthCheckEpochMs = nowEpochMs,
            runtimeMode = if (
                current.masterEnabled &&
                !current.emergencyStopActive &&
                !current.quarantineActive
            ) RuntimeMode.RUNNING else current.runtimeMode,
            security = current.security.copy(gate10Recovery = GateStatus.GREEN),
            lastError = null,
            lastTransition = "HEALTH_GREEN",
        )
    }

    fun healthFailure(reason: String, nowEpochMs: Long = System.currentTimeMillis()): X88CoreState = update { current ->
        current.copy(
            lastHealthCheckEpochMs = nowEpochMs,
            runtimeMode = RuntimeMode.DEGRADED,
            security = current.security.copy(gate10Recovery = GateStatus.YELLOW),
            lastError = reason,
            lastTransition = "HEALTH_FAILURE",
        )
    }

    fun canExecute(capability: X88Capability, sessionId: String?): Boolean {
        val current = snapshot()
        return current.activeSessionId != null &&
            current.activeSessionId == sessionId &&
            current.masterEnabled &&
            !current.emergencyStopActive &&
            !current.quarantineActive &&
            current.security.gate12CircuitBreaker == GateStatus.GREEN &&
            capability in current.enabledCapabilities
    }
}

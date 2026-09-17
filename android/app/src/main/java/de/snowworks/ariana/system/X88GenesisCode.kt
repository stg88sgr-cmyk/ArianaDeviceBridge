package de.snowworks.ariana.system

import java.security.MessageDigest

enum class GenesisStatus {
    READY,
    BLOCKED,
}

data class X88GenesisIdentity(
    val identityId: String = "ARIANA_X88",
    val codename: String = "X88",
    val schemaVersion: Int = 1,
)

data class X88GenesisSnapshot(
    val identity: X88GenesisIdentity,
    val capturedAtEpochMs: Long,
    val status: GenesisStatus,
    val blockReason: String?,
    val runtimeMode: RuntimeMode,
    val releaseStatus: ReleaseStatus,
    val masterEnabled: Boolean,
    val emergencyStopActive: Boolean,
    val quarantineActive: Boolean,
    val activeSessionId: String?,
    val bridgeConnected: Boolean,
    val bridgePort: Int,
    val enabledCapabilities: List<String>,
    val gateStates: Map<String, String>,
    val stateSha256: String,
)

/**
 * Canonical origin snapshot for the X-88 runtime.
 *
 * Genesis never grants authority. It does not enable the master switch, start a
 * session, grant a capability or clear an emergency/quarantine condition. Its
 * job is to describe the exact fail-closed state from which higher runtime
 * layers continue.
 */
class X88GenesisCode(
    private val gateway: X88SystemGateway,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun capture(): X88GenesisSnapshot {
        val state = gateway.state()
        val blockReason = when {
            state.emergencyStopActive -> "emergency_stop_active"
            state.quarantineActive -> "quarantine_active"
            state.security.gate12CircuitBreaker != GateStatus.GREEN -> "circuit_breaker_closed"
            else -> null
        }
        val status = if (blockReason == null) GenesisStatus.READY else GenesisStatus.BLOCKED
        val capabilities = state.enabledCapabilities.map { it.name }.sorted()
        val gates = state.security.asStableMap()
        val digest = sha256(
            canonicalState(
                state = state,
                capabilities = capabilities,
                gates = gates,
                status = status,
                blockReason = blockReason,
            ),
        )

        return X88GenesisSnapshot(
            identity = X88GenesisIdentity(),
            capturedAtEpochMs = clock(),
            status = status,
            blockReason = blockReason,
            runtimeMode = state.runtimeMode,
            releaseStatus = state.releaseStatus,
            masterEnabled = state.masterEnabled,
            emergencyStopActive = state.emergencyStopActive,
            quarantineActive = state.quarantineActive,
            activeSessionId = state.activeSessionId,
            bridgeConnected = state.bridgeConnected,
            bridgePort = state.bridgePort,
            enabledCapabilities = capabilities,
            gateStates = gates,
            stateSha256 = digest,
        )
    }

    private fun canonicalState(
        state: X88CoreState,
        capabilities: List<String>,
        gates: Map<String, String>,
        status: GenesisStatus,
        blockReason: String?,
    ): String = buildString {
        append("schema=1\n")
        append("identity=ARIANA_X88\n")
        append("codename=X88\n")
        append("status=${status.name}\n")
        append("blockReason=${blockReason.orEmpty()}\n")
        append("runtimeMode=${state.runtimeMode.name}\n")
        append("releaseStatus=${state.releaseStatus.name}\n")
        append("masterEnabled=${state.masterEnabled}\n")
        append("emergencyStopActive=${state.emergencyStopActive}\n")
        append("quarantineActive=${state.quarantineActive}\n")
        append("activeSessionId=${state.activeSessionId.orEmpty()}\n")
        append("bridgeConnected=${state.bridgeConnected}\n")
        append("bridgePort=${state.bridgePort}\n")
        append("hardwareIdentityAvailable=${state.hardwareIdentityAvailable}\n")
        append("hardwareIdentityGeneration=${state.hardwareIdentityGeneration ?: -1}\n")
        append("hardwareIdentityFingerprint=${state.hardwareIdentityFingerprint.orEmpty()}\n")
        append("sourceTreeSha256=${state.sourceTreeSha256.orEmpty()}\n")
        append("artifactSha256=${state.artifactSha256.orEmpty()}\n")
        append("ledgerHeadSha256=${state.ledgerHeadSha256.orEmpty()}\n")
        append("lastAttestationId=${state.lastAttestationId.orEmpty()}\n")
        append("capabilities=${capabilities.joinToString(",")}\n")
        gates.forEach { (name, value) -> append("gate.$name=$value\n") }
    }

    private fun sha256(value: String): String = MessageDigest
        .getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte) }
}

private fun SecurityGateState.asStableMap(): Map<String, String> = linkedMapOf(
    "01_input_firewall" to gate1InputFirewall.name,
    "02_output_firewall" to gate2OutputFirewall.name,
    "03_authenticity" to gate3Authenticity.name,
    "04_authorization" to gate4Authorization.name,
    "05_execution" to gate5Execution.name,
    "06_mutation" to gate6Mutation.name,
    "07_attestation" to gate7Attestation.name,
    "08_hardware_identity" to gate8HardwareIdentity.name,
    "09_peer_trust" to gate9PeerTrust.name,
    "10_recovery" to gate10Recovery.name,
    "11_policy_lock" to gate11PolicyLock.name,
    "12_circuit_breaker" to gate12CircuitBreaker.name,
    "13_release_gate" to gate13ReleaseGate.name,
)

package de.snowworks.ariana.system

data class X88CoreState(
    val masterEnabled: Boolean = false,
    val emergencyStopActive: Boolean = false,
    val quarantineActive: Boolean = false,
    val runtimeMode: RuntimeMode = RuntimeMode.STOPPED,
    val releaseStatus: ReleaseStatus = ReleaseStatus.UNKNOWN,

    val activeSessionId: String? = null,
    val enabledCapabilities: Set<X88Capability> = emptySet(),

    val bridgeConnected: Boolean = false,
    val bridgePort: Int = 8765,

    val hardwareIdentityAvailable: Boolean = false,
    val hardwareIdentityGeneration: Long? = null,
    val hardwareIdentityFingerprint: String? = null,

    val sourceTreeSha256: String? = null,
    val artifactSha256: String? = null,
    val ledgerHeadSha256: String? = null,
    val lastAttestationId: String? = null,

    val security: SecurityGateState = SecurityGateState(),

    val lastHealthCheckEpochMs: Long? = null,
    val lastError: String? = null,
    val lastTransition: String? = null,
) {
    fun canExecute(capability: X88Capability): Boolean =
        masterEnabled &&
            !emergencyStopActive &&
            !quarantineActive &&
            security.gate12CircuitBreaker == GateStatus.GREEN &&
            capability in enabledCapabilities
}

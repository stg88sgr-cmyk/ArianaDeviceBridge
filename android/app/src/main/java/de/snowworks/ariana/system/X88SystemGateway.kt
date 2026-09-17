package de.snowworks.ariana.system

interface X88SystemGateway {
    fun state(): X88CoreState
    fun startSession(): String
    fun stopSession(sessionId: String): Boolean
    fun setMasterEnabled(enabled: Boolean): X88CoreState
    fun enable(capability: X88Capability): X88CoreState
    fun disable(capability: X88Capability): X88CoreState
    fun emergencyStop(): X88CoreState
    fun clearEmergencyStop(): X88CoreState
    fun submit(command: X88Command): X88CommandResult
}

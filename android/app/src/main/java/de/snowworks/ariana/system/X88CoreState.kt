package de.snowworks.ariana.system

data class X88CoreState(
    val masterEnabled: Boolean = false,
    val emergencyStopActive: Boolean = false,
    val activeSessionId: String? = null,
    val enabledCapabilities: Set<X88Capability> = emptySet(),
) {
    fun canExecute(capability: X88Capability): Boolean =
        masterEnabled && !emergencyStopActive && capability in enabledCapabilities
}

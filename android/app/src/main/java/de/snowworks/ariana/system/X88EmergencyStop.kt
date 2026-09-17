package de.snowworks.ariana.system

class X88EmergencyStop(
    private val broker: X88CapabilityBroker,
    private val stopCallbacks: List<() -> Unit> = emptyList(),
) {
    @Synchronized
    fun engage(): X88CoreState {
        val state = broker.setEmergencyStop(true)
        stopCallbacks.forEach { runCatching(it) }
        return state
    }

    @Synchronized
    fun release(): X88CoreState = broker.setEmergencyStop(false)
}

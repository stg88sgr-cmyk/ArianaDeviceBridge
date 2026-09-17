package de.snowworks.ariana.system

class X88CapabilityBroker(
    initialState: X88CoreState = X88CoreState(),
) {
    @Volatile
    private var state: X88CoreState = initialState

    @Synchronized
    fun setMasterEnabled(enabled: Boolean): X88CoreState {
        state = state.copy(masterEnabled = enabled)
        return state
    }

    @Synchronized
    fun enable(capability: X88Capability): X88CoreState {
        state = state.copy(enabledCapabilities = state.enabledCapabilities + capability)
        return state
    }

    @Synchronized
    fun disable(capability: X88Capability): X88CoreState {
        state = state.copy(enabledCapabilities = state.enabledCapabilities - capability)
        return state
    }

    @Synchronized
    fun attachSession(sessionId: String?): X88CoreState {
        state = state.copy(activeSessionId = sessionId)
        return state
    }

    @Synchronized
    fun setEmergencyStop(active: Boolean): X88CoreState {
        state = state.copy(emergencyStopActive = active)
        return state
    }

    fun snapshot(): X88CoreState = state

    fun canExecute(capability: X88Capability, sessionId: String?): Boolean =
        state.activeSessionId != null &&
            state.activeSessionId == sessionId &&
            state.canExecute(capability)
}

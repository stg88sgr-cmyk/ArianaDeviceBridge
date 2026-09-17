package de.snowworks.ariana.system

import kotlinx.coroutines.flow.StateFlow

class X88CapabilityBroker(
    initialState: X88CoreState = X88CoreState(),
    private val holder: ArianaStateHolder = ArianaStateHolder(initialState),
) {
    val state: StateFlow<X88CoreState> = holder.state

    @Synchronized
    fun setMasterEnabled(enabled: Boolean): X88CoreState =
        holder.setMasterEnabled(enabled)

    @Synchronized
    fun enable(capability: X88Capability): X88CoreState =
        holder.update { current ->
            current.copy(
                enabledCapabilities = current.enabledCapabilities + capability,
                lastTransition = "CAPABILITY_ENABLED:${capability.name}",
            )
        }

    @Synchronized
    fun disable(capability: X88Capability): X88CoreState =
        holder.update { current ->
            current.copy(
                enabledCapabilities = current.enabledCapabilities - capability,
                lastTransition = "CAPABILITY_DISABLED:${capability.name}",
            )
        }

    @Synchronized
    fun attachSession(sessionId: String?): X88CoreState =
        holder.update { current ->
            current.copy(
                activeSessionId = sessionId,
                lastTransition = if (sessionId == null) "SESSION_DETACHED" else "SESSION_ATTACHED",
            )
        }

    @Synchronized
    fun setEmergencyStop(active: Boolean): X88CoreState =
        holder.setEmergencyStop(active)

    @Synchronized
    fun enterQuarantine(reason: String): X88CoreState =
        holder.enterQuarantine(reason)

    @Synchronized
    fun clearQuarantine(): X88CoreState =
        holder.clearQuarantine()

    @Synchronized
    fun updateSecurity(transform: (SecurityGateState) -> SecurityGateState): X88CoreState =
        holder.updateSecurity(transform)

    fun snapshot(): X88CoreState = holder.snapshot()

    fun canExecute(capability: X88Capability, sessionId: String?): Boolean =
        holder.canExecute(capability, sessionId)
}

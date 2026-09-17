package de.snowworks.ariana.system

class X88Core(
    val sessions: X88SessionManager = X88SessionManager(),
    val capabilities: X88CapabilityBroker = X88CapabilityBroker(),
) {
    val emergencyStop: X88EmergencyStop = X88EmergencyStop(capabilities)

    @Synchronized
    fun startSession(): String {
        val id = sessions.openSession()
        capabilities.attachSession(id)
        return id
    }

    @Synchronized
    fun stopSession(sessionId: String): Boolean {
        val closed = sessions.closeSession(sessionId)
        if (closed) capabilities.attachSession(null)
        return closed
    }

    fun canExecute(capability: X88Capability, sessionId: String?): Boolean =
        sessions.isValid(sessionId) && capabilities.canExecute(capability, sessionId)
}

package de.snowworks.ariana.system

class InProcessX88SystemGateway(
    private val core: X88Core = X88Core(),
    private val executor: (X88Command) -> X88CommandResult = { command ->
        X88CommandResult.accepted(command.id)
    },
    private val auditLog: X88AuditLog = X88AuditLog(),
) : X88SystemGateway {

    override fun state(): X88CoreState = core.capabilities.snapshot()

    override fun startSession(): String = core.startSession()

    override fun stopSession(sessionId: String): Boolean = core.stopSession(sessionId)

    override fun setMasterEnabled(enabled: Boolean): X88CoreState =
        core.capabilities.setMasterEnabled(enabled)

    override fun enable(capability: X88Capability): X88CoreState =
        core.capabilities.enable(capability)

    override fun disable(capability: X88Capability): X88CoreState =
        core.capabilities.disable(capability)

    override fun emergencyStop(): X88CoreState = core.emergencyStop.engage()

    override fun clearEmergencyStop(): X88CoreState = core.emergencyStop.release()

    override fun submit(command: X88Command): X88CommandResult {
        val result = if (!core.canExecute(command.capability, command.sessionId)) {
            X88CommandResult.rejected(
                commandId = command.id,
                code = "X88_GATE_REJECTED",
            )
        } else {
            runCatching { executor(command) }
                .getOrElse { error ->
                    X88CommandResult.rejected(
                        commandId = command.id,
                        code = "X88_EXECUTION_FAILED",
                        message = error.message,
                    )
                }
        }
        auditLog.record(command, result)
        return result
    }

    override fun auditSnapshot(): List<X88AuditEvent> = auditLog.snapshot()
}

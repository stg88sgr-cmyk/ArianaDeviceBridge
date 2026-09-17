package de.snowworks.ariana.system

class X88AuditLog(
    private val maxEntries: Int = 256,
) {
    init {
        require(maxEntries > 0) { "maxEntries must be > 0" }
    }

    private val entries = ArrayDeque<X88AuditEvent>()
    private var sequence = 0L

    @Synchronized
    fun record(command: X88Command, result: X88CommandResult): X88AuditEvent {
        sequence += 1
        val event = X88AuditEvent(
            sequence = sequence,
            commandId = command.id,
            capability = command.capability,
            action = command.action,
            accepted = result.accepted,
            resultCode = result.code,
            sessionId = command.sessionId,
        )
        if (entries.size == maxEntries) entries.removeFirst()
        entries.addLast(event)
        return event
    }

    @Synchronized
    fun snapshot(): List<X88AuditEvent> = entries.toList()

    @Synchronized
    fun clear() {
        entries.clear()
    }
}

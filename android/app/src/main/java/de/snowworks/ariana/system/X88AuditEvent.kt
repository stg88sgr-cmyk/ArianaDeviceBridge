package de.snowworks.ariana.system

data class X88AuditEvent(
    val sequence: Long,
    val commandId: String,
    val capability: X88Capability,
    val action: String,
    val accepted: Boolean,
    val resultCode: String,
    val sessionId: String?,
)

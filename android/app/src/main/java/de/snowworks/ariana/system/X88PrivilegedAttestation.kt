package de.snowworks.ariana.system

/**
 * Evidence supplied by a future privileged X88 platform backend.
 * The app must never select PRIVILEGED_SYSTEM from mere package presence.
 */
data class X88PrivilegedAttestation(
    val serviceName: String,
    val protocolVersion: Int,
    val peerCertificateSha256: String,
    val trusted: Boolean,
) {
    fun isStructurallyValid(): Boolean =
        serviceName == EXPECTED_SERVICE_NAME &&
            protocolVersion >= MIN_PROTOCOL_VERSION &&
            peerCertificateSha256.matches(Regex("^[a-fA-F0-9]{64}$"))

    companion object {
        const val EXPECTED_SERVICE_NAME = "x88_system"
        const val MIN_PROTOCOL_VERSION = 1
    }
}

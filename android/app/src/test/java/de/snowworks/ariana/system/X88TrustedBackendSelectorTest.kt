package de.snowworks.ariana.system

import org.junit.Assert.assertEquals
import org.junit.Test

class X88TrustedBackendSelectorTest {

    private val selector = X88TrustedBackendSelector()

    @Test
    fun rejectsUntrustedPrivilegedAttestation() {
        val mode = selector.select(
            privilegedAttestation = X88PrivilegedAttestation(
                serviceName = X88PrivilegedAttestation.EXPECTED_SERVICE_NAME,
                protocolVersion = 1,
                peerCertificateSha256 = "a".repeat(64),
                trusted = false,
            ),
            appBinderAvailable = true,
        )

        assertEquals(X88BackendMode.APP_BINDER, mode)
    }

    @Test
    fun rejectsMalformedPrivilegedAttestation() {
        val mode = selector.select(
            privilegedAttestation = X88PrivilegedAttestation(
                serviceName = "wrong_service",
                protocolVersion = 1,
                peerCertificateSha256 = "a".repeat(64),
                trusted = true,
            ),
            appBinderAvailable = false,
        )

        assertEquals(X88BackendMode.IN_PROCESS, mode)
    }

    @Test
    fun acceptsTrustedStructurallyValidPrivilegedAttestation() {
        val mode = selector.select(
            privilegedAttestation = X88PrivilegedAttestation(
                serviceName = X88PrivilegedAttestation.EXPECTED_SERVICE_NAME,
                protocolVersion = 1,
                peerCertificateSha256 = "b".repeat(64),
                trusted = true,
            ),
            appBinderAvailable = true,
        )

        assertEquals(X88BackendMode.PRIVILEGED_SYSTEM, mode)
    }
}

package de.snowworks.ariana.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InProcessX88SystemGatewayTest {

    @Test
    fun commandRequiresMasterCapabilityAndValidSession() {
        val gateway = InProcessX88SystemGateway()
        val session = gateway.startSession()
        val command = X88Command(
            id = "cmd-1",
            capability = X88Capability.APP_CONTROL,
            sessionId = session,
            action = "open_app",
        )

        assertFalse(gateway.submit(command).accepted)

        gateway.setMasterEnabled(true)
        gateway.enable(X88Capability.APP_CONTROL)

        assertTrue(gateway.submit(command).accepted)
    }

    @Test
    fun emergencyStopBlocksUntilExplicitMasterReenable() {
        val gateway = InProcessX88SystemGateway()
        val session = gateway.startSession()
        gateway.setMasterEnabled(true)
        gateway.enable(X88Capability.MEDIA_CONTROL)
        val command = X88Command(
            id = "cmd-2",
            capability = X88Capability.MEDIA_CONTROL,
            sessionId = session,
            action = "media_play",
        )

        assertTrue(gateway.submit(command).accepted)

        gateway.emergencyStop()
        assertFalse(gateway.submit(command).accepted)

        gateway.clearEmergencyStop()
        assertFalse(gateway.submit(command).accepted)

        gateway.setMasterEnabled(true)
        assertTrue(gateway.submit(command).accepted)
    }

    @Test
    fun auditCapturesRejectedAndAcceptedCommandsInOrder() {
        val gateway = InProcessX88SystemGateway()
        val session = gateway.startSession()
        val command = X88Command(
            id = "cmd-audit",
            capability = X88Capability.APP_CONTROL,
            sessionId = session,
            action = "open_app",
        )

        gateway.submit(command)
        gateway.setMasterEnabled(true)
        gateway.enable(X88Capability.APP_CONTROL)
        gateway.submit(command.copy(id = "cmd-audit-2"))

        val audit = gateway.auditSnapshot()
        assertEquals(2, audit.size)
        assertFalse(audit[0].accepted)
        assertEquals("X88_GATE_REJECTED", audit[0].resultCode)
        assertTrue(audit[1].accepted)
        assertEquals("X88_ACCEPTED", audit[1].resultCode)
        assertTrue(audit[0].sequence < audit[1].sequence)
    }
}

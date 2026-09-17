package de.snowworks.ariana.system

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
    fun emergencyStopBlocksPreviouslyAllowedCommand() {
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
        assertTrue(gateway.submit(command).accepted)
    }
}

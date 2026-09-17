package de.snowworks.ariana.system

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class X88CapabilityBrokerTest {
    @Test
    fun requiresMasterCapabilityAndMatchingSession() {
        val broker = X88CapabilityBroker()
        broker.enable(X88Capability.CAMERA)
        broker.attachSession("session-1")

        assertFalse(broker.canExecute(X88Capability.CAMERA, "session-1"))

        broker.setMasterEnabled(true)
        assertTrue(broker.canExecute(X88Capability.CAMERA, "session-1"))
        assertFalse(broker.canExecute(X88Capability.CAMERA, "wrong"))
        assertFalse(broker.canExecute(X88Capability.MICROPHONE, "session-1"))
    }

    @Test
    fun emergencyStopBlocksExecution() {
        val broker = X88CapabilityBroker()
        broker.enable(X88Capability.CAMERA)
        broker.attachSession("session-1")
        broker.setMasterEnabled(true)
        broker.setEmergencyStop(true)

        assertFalse(broker.canExecute(X88Capability.CAMERA, "session-1"))
    }
}

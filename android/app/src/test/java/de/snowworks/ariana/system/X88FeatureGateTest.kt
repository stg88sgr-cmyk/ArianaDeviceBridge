package de.snowworks.ariana.system

import de.snowworks.ariana.Feature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class X88FeatureGateTest {

    @Test
    fun mapsEveryDeviceFeatureToCanonicalCapability() {
        assertEquals(X88Capability.CAMERA, Feature.CAMERA.toX88Capability())
        assertEquals(X88Capability.MICROPHONE, Feature.MICROPHONE.toX88Capability())
        assertEquals(X88Capability.SCREEN_CAPTURE, Feature.SCREEN.toX88Capability())
        assertEquals(X88Capability.FILES, Feature.FILES.toX88Capability())
        assertEquals(X88Capability.NOTIFICATIONS, Feature.NOTIFY_SEND.toX88Capability())
        assertEquals(X88Capability.NOTIFICATIONS, Feature.NOTIFY_READ.toX88Capability())
        assertEquals(X88Capability.LOCATION, Feature.LOCATION.toX88Capability())
        assertEquals(X88Capability.BLUETOOTH, Feature.BLUETOOTH.toX88Capability())
    }

    @Test
    fun explicitAuthorizationStillRequiresMaster() {
        val gateway = InProcessX88SystemGateway()
        val gate = X88FeatureGate(gateway)

        assertFalse(gate.authorizeForExplicitStart(Feature.CAMERA))
        assertTrue(X88Capability.CAMERA in gateway.state().enabledCapabilities)

        gateway.setMasterEnabled(true)
        assertTrue(gate.isAuthorized(Feature.CAMERA))
    }

    @Test
    fun emergencyStopOverridesPreviouslyAuthorizedFeature() {
        val gateway = InProcessX88SystemGateway()
        val gate = X88FeatureGate(gateway)
        gateway.setMasterEnabled(true)

        assertTrue(gate.authorizeForExplicitStart(Feature.MICROPHONE))

        gateway.emergencyStop()
        assertFalse(gate.isAuthorized(Feature.MICROPHONE))
    }

    @Test
    fun revokeRemovesCapability() {
        val gateway = InProcessX88SystemGateway()
        val gate = X88FeatureGate(gateway)
        gateway.setMasterEnabled(true)
        assertTrue(gate.authorizeForExplicitStart(Feature.LOCATION))

        gate.revoke(Feature.LOCATION)

        assertFalse(gate.isAuthorized(Feature.LOCATION))
        assertFalse(X88Capability.LOCATION in gateway.state().enabledCapabilities)
    }
}

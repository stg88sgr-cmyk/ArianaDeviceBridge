package de.snowworks.ariana.system

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersistingX88SystemGatewayTest {

    @Test
    fun restoresCapabilitiesButNeverMasterAuthority() {
        val store = MemoryStore(
            X88PersistentState(enabledCapabilities = setOf(X88Capability.CAMERA)),
        )
        val gateway = PersistingX88SystemGateway(InProcessX88SystemGateway(), store)

        assertTrue(X88Capability.CAMERA in gateway.state().enabledCapabilities)
        assertFalse(gateway.state().masterEnabled)
    }

    @Test
    fun capabilityChangesPersist() {
        val store = MemoryStore()
        val gateway = PersistingX88SystemGateway(InProcessX88SystemGateway(), store)

        gateway.enable(X88Capability.FILES)
        assertTrue(X88Capability.FILES in store.load().enabledCapabilities)

        gateway.disable(X88Capability.FILES)
        assertFalse(X88Capability.FILES in store.load().enabledCapabilities)
    }

    @Test
    fun restartRestoresPreferencesButDropsRuntimeAuthority() {
        val store = MemoryStore()
        val first = PersistingX88SystemGateway(InProcessX88SystemGateway(), store)

        first.enable(X88Capability.CAMERA)
        first.enable(X88Capability.MICROPHONE)
        first.startSession()
        first.setMasterEnabled(true)
        first.emergencyStop()

        val restarted = PersistingX88SystemGateway(InProcessX88SystemGateway(), store)
        val state = restarted.state()

        assertTrue(X88Capability.CAMERA in state.enabledCapabilities)
        assertTrue(X88Capability.MICROPHONE in state.enabledCapabilities)
        assertFalse(state.masterEnabled)
        assertFalse(state.emergencyStopActive)
        assertFalse(state.quarantineActive)
        assertNull(state.activeSessionId)
    }

    private class MemoryStore(
        private var value: X88PersistentState = X88PersistentState(),
    ) : X88StateStore {
        override fun load(): X88PersistentState = value
        override fun save(state: X88PersistentState) { value = state }
        override fun clear() { value = X88PersistentState() }
    }
}

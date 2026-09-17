package de.snowworks.ariana.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class X88GenesisCodeTest {

    @Test
    fun captureIsFailClosedAndDoesNotGrantAuthority() {
        val gateway = InProcessX88SystemGateway()
        val genesis = X88GenesisCode(gateway) { 1234L }

        val snapshot = genesis.capture()

        assertEquals(GenesisStatus.READY, snapshot.status)
        assertFalse(snapshot.masterEnabled)
        assertNull(snapshot.activeSessionId)
        assertTrue(snapshot.enabledCapabilities.isEmpty())
        assertFalse(gateway.state().masterEnabled)
        assertNull(gateway.state().activeSessionId)
    }

    @Test
    fun emergencyStopBlocksGenesis() {
        val gateway = InProcessX88SystemGateway()
        gateway.emergencyStop()

        val snapshot = X88GenesisCode(gateway).capture()

        assertEquals(GenesisStatus.BLOCKED, snapshot.status)
        assertEquals("emergency_stop_active", snapshot.blockReason)
        assertTrue(snapshot.emergencyStopActive)
    }

    @Test
    fun sameRuntimeStateProducesSameDigestRegardlessOfCaptureTime() {
        val gateway = InProcessX88SystemGateway()
        val first = X88GenesisCode(gateway) { 100L }.capture()
        val second = X88GenesisCode(gateway) { 200L }.capture()

        assertNotEquals(first.capturedAtEpochMs, second.capturedAtEpochMs)
        assertEquals(first.stateSha256, second.stateSha256)
    }

    @Test
    fun capabilityMutationChangesGenesisDigest() {
        val gateway = InProcessX88SystemGateway()
        val genesis = X88GenesisCode(gateway)
        val before = genesis.capture()

        gateway.enable(X88Capability.CAMERA)
        val after = genesis.capture()

        assertNotEquals(before.stateSha256, after.stateSha256)
        assertEquals(listOf("CAMERA"), after.enabledCapabilities)
    }

    @Test
    fun gateMutationChangesGenesisDigest() {
        val initial = X88CoreState()
        val holder = ArianaStateHolder(initial)
        val gateway = InProcessX88SystemGateway(holder = holder)
        val genesis = X88GenesisCode(gateway)
        val before = genesis.capture()

        holder.updateSecurity { it.copy(gate1InputFirewall = GateStatus.GREEN) }
        val after = genesis.capture()

        assertNotEquals(before.stateSha256, after.stateSha256)
        assertEquals("GREEN", after.gateStates["01_input_firewall"])
    }
}

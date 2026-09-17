package de.snowworks.ariana.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArianaStateHolderTest {

    @Test
    fun executionRequiresMasterSessionCapabilityAndClosedCircuitBreaker() {
        val holder = ArianaStateHolder()

        holder.update {
            it.copy(
                enabledCapabilities = setOf(X88Capability.CAMERA),
                activeSessionId = "session-1",
            )
        }

        assertFalse(holder.canExecute(X88Capability.CAMERA, "session-1"))

        holder.setMasterEnabled(true)

        assertTrue(holder.canExecute(X88Capability.CAMERA, "session-1"))
        assertFalse(holder.canExecute(X88Capability.CAMERA, "wrong"))
        assertFalse(holder.canExecute(X88Capability.MICROPHONE, "session-1"))
    }

    @Test
    fun emergencyStopIsFailClosedAndDoesNotRestoreMasterAutomatically() {
        val holder = ArianaStateHolder(
            X88CoreState(
                masterEnabled = true,
                activeSessionId = "session-1",
                enabledCapabilities = setOf(X88Capability.CAMERA),
            )
        )

        val stopped = holder.setEmergencyStop(true, "test")

        assertTrue(stopped.emergencyStopActive)
        assertFalse(stopped.masterEnabled)
        assertEquals(RuntimeMode.EMERGENCY_STOP, stopped.runtimeMode)
        assertEquals(GateStatus.BLOCKED, stopped.security.gate12CircuitBreaker)
        assertFalse(holder.canExecute(X88Capability.CAMERA, "session-1"))

        val cleared = holder.setEmergencyStop(false)

        assertFalse(cleared.emergencyStopActive)
        assertFalse(cleared.masterEnabled)
        assertEquals(GateStatus.GREEN, cleared.security.gate12CircuitBreaker)
    }

    @Test
    fun quarantinePreventsMasterReenableUntilExplicitlyCleared() {
        val holder = ArianaStateHolder()

        holder.enterQuarantine("repeated_failure")
        val blocked = holder.setMasterEnabled(true)

        assertTrue(blocked.quarantineActive)
        assertFalse(blocked.masterEnabled)
        assertEquals(ReleaseStatus.BLOCKED, blocked.releaseStatus)

        holder.clearQuarantine()
        val enabled = holder.setMasterEnabled(true)

        assertFalse(enabled.quarantineActive)
        assertTrue(enabled.masterEnabled)
        assertEquals(RuntimeMode.STARTING, enabled.runtimeMode)
    }

    @Test
    fun stateFlowAlwaysReflectsCanonicalSnapshot() {
        val holder = ArianaStateHolder()

        holder.setMasterEnabled(true)
        holder.healthGreen(nowEpochMs = 1234L)

        assertEquals(holder.snapshot(), holder.state.value)
        assertEquals(1234L, holder.state.value.lastHealthCheckEpochMs)
        assertEquals(RuntimeMode.RUNNING, holder.state.value.runtimeMode)
    }
}

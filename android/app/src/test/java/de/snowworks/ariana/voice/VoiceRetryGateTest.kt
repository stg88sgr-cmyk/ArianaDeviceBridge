package de.snowworks.ariana.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceRetryGateTest {
    @Test
    fun invalidationRejectsAlreadyScheduledRetry() {
        val gate = VoiceRetryGate()
        val scheduled = gate.next()
        assertTrue(gate.isCurrent(scheduled))

        gate.invalidate()

        assertFalse(gate.isCurrent(scheduled))
    }

    @Test
    fun newerRetrySupersedesOlderRetry() {
        val gate = VoiceRetryGate()
        val first = gate.next()
        val second = gate.next()

        assertFalse(gate.isCurrent(first))
        assertTrue(gate.isCurrent(second))
    }
}

package de.snowworks.ariana.system

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class X88AutonomyPolicyTest {
    @Test
    fun disabledMasterNeverRunsAutonomy() {
        val decision = X88AutonomyPolicy.decide(
            userMasterEnabled = false,
            stopAllBlocked = false,
        )
        assertFalse(decision.shouldRun)
        assertFalse(decision.shouldOwnSession)
    }

    @Test
    fun stopAllAlwaysWins() {
        val decision = X88AutonomyPolicy.decide(
            userMasterEnabled = true,
            stopAllBlocked = true,
        )
        assertFalse(decision.shouldRun)
        assertFalse(decision.shouldOwnSession)
    }

    @Test
    fun enabledMasterWithoutBlockRuns() {
        val decision = X88AutonomyPolicy.decide(
            userMasterEnabled = true,
            stopAllBlocked = false,
        )
        assertTrue(decision.shouldRun)
        assertTrue(decision.shouldOwnSession)
    }
}

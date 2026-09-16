package de.snowworks.ariana.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderRecoverySupervisorTest {
    @Test
    fun firstAutomaticProbeIsDue() {
        assertTrue(AiProviderRecoverySupervisor.isDue(0L, 1_000L))
    }

    @Test
    fun recentAutomaticProbeIsRateLimited() {
        val now = 1_000_000L
        assertFalse(
            AiProviderRecoverySupervisor.isDue(
                now - AiProviderRecoverySupervisor.MIN_AUTO_PROBE_INTERVAL_MS + 1L,
                now,
            ),
        )
    }

    @Test
    fun probeBecomesDueAtIntervalBoundary() {
        val now = 1_000_000L
        assertTrue(
            AiProviderRecoverySupervisor.isDue(
                now - AiProviderRecoverySupervisor.MIN_AUTO_PROBE_INTERVAL_MS,
                now,
            ),
        )
    }

    @Test
    fun clockRollbackDoesNotPermanentlySuppressRecovery() {
        assertTrue(AiProviderRecoverySupervisor.isDue(10_000L, 9_000L))
        assertEquals(9_000L, AiProviderRecoverySupervisor.nextEligibleWallMs(10_000L, 9_000L))
    }

    @Test
    fun nextEligibleTimeMatchesRateLimitBoundary() {
        val last = 2_000_000L
        val now = last + 1_000L
        assertEquals(
            last + AiProviderRecoverySupervisor.MIN_AUTO_PROBE_INTERVAL_MS,
            AiProviderRecoverySupervisor.nextEligibleWallMs(last, now),
        )
    }

    @Test
    fun neverProbedProviderIsImmediatelyEligible() {
        assertEquals(5_000L, AiProviderRecoverySupervisor.nextEligibleWallMs(0L, 5_000L))
    }
}

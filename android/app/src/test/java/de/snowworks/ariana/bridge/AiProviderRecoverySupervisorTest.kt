package de.snowworks.ariana.bridge

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
    }
}

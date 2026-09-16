package de.snowworks.ariana.bridge

import org.junit.Assert.assertEquals
import org.junit.Test

class AiProviderQualityStoreTest {
    @Test
    fun ratioPercentHandlesZeroTotal() {
        assertEquals(0.0, AiProviderQualityStore.ratioPercent(5L, 0L), 0.0001)
    }

    @Test
    fun ratioPercentCalculatesExpectedShare() {
        assertEquals(75.0, AiProviderQualityStore.ratioPercent(3L, 4L), 0.0001)
        assertEquals(25.0, AiProviderQualityStore.ratioPercent(1L, 4L), 0.0001)
    }

    @Test
    fun snapshotRatesSeparateExecutionFromSelection() {
        val snapshot = AiProviderQualityStore.Snapshot(
            engine = AiProviderQualityStore.Engine.CLAUDE,
            selected = 10L,
            executed = 8L,
            successes = 6L,
            errors = 2L,
            circuitRejected = 2L,
            fallbackSelections = 3L,
        )

        assertEquals(75.0, snapshot.successRatePercent, 0.0001)
        assertEquals(25.0, snapshot.errorRatePercent, 0.0001)
        assertEquals(30.0, snapshot.fallbackSharePercent, 0.0001)
        assertEquals(80.0, snapshot.executionRatePercent, 0.0001)
    }
}

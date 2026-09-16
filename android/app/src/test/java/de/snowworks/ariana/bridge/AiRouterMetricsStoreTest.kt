package de.snowworks.ariana.bridge

import org.junit.Assert.assertEquals
import org.junit.Test

class AiRouterMetricsStoreTest {
    @Test
    fun zeroTotalReturnsZeroPercent() {
        assertEquals(0.0, AiRouterMetricsStore.ratioPercent(0L, 0L), 0.0001)
    }

    @Test
    fun ratioPercentCalculatesExpectedValue() {
        assertEquals(25.0, AiRouterMetricsStore.ratioPercent(1L, 4L), 0.0001)
        assertEquals(50.0, AiRouterMetricsStore.ratioPercent(2L, 4L), 0.0001)
        assertEquals(100.0, AiRouterMetricsStore.ratioPercent(4L, 4L), 0.0001)
    }
}

package de.snowworks.ariana.bridge

import org.junit.Assert.assertEquals
import org.junit.Test

class AiProviderQualityTrendStoreTest {
    @Test
    fun floorHourRoundsDown() {
        val hour = AiProviderQualityTrendStore.HOUR_MS
        assertEquals(0L, AiProviderQualityTrendStore.floorHour(0L))
        assertEquals(hour, AiProviderQualityTrendStore.floorHour(hour + 42_000L))
        assertEquals(2L * hour, AiProviderQualityTrendStore.floorHour(3L * hour - 1L))
    }

    @Test
    fun aggregateHonorsCutoffAndSumsCounters() {
        val h = AiProviderQualityTrendStore.HOUR_MS
        val buckets = listOf(
            AiProviderQualityTrendStore.Bucket(
                hourStartMs = h,
                selected = 5,
                executed = 4,
                successes = 3,
                errors = 1,
                circuitRejected = 1,
                fallbackSelections = 2,
            ),
            AiProviderQualityTrendStore.Bucket(
                hourStartMs = 2 * h,
                selected = 7,
                executed = 6,
                successes = 5,
                errors = 1,
                circuitRejected = 1,
                fallbackSelections = 3,
            ),
        )
        val result = AiProviderQualityTrendStore.aggregate(buckets, 2 * h)
        assertEquals(7L, result.selected)
        assertEquals(6L, result.executed)
        assertEquals(5L, result.successes)
        assertEquals(1L, result.errors)
        assertEquals(1L, result.circuitRejected)
        assertEquals(3L, result.fallbackSelections)
    }

    @Test
    fun windowRatiosUseExecutedAndSelectedDenominators() {
        val window = AiProviderQualityTrendStore.Window(
            selected = 10,
            executed = 8,
            successes = 6,
            errors = 2,
            circuitRejected = 2,
            fallbackSelections = 4,
        )
        assertEquals(75.0, window.successRatePercent, 0.001)
        assertEquals(25.0, window.errorRatePercent, 0.001)
        assertEquals(40.0, window.fallbackSharePercent, 0.001)
        assertEquals(80.0, window.executionRatePercent, 0.001)
    }
}

package de.snowworks.ariana.bridge

import org.junit.Assert.assertEquals
import org.junit.Test

class AiRouterTrendStoreTest {
    @Test
    fun floorHourRoundsDown() {
        val hour = AiRouterTrendStore.HOUR_MS
        assertEquals(0L, AiRouterTrendStore.floorHour(1L))
        assertEquals(hour, AiRouterTrendStore.floorHour(hour + 12_345L))
    }

    @Test
    fun aggregateHonorsCutoff() {
        val buckets = listOf(
            AiRouterTrendStore.Bucket(1_000L, 2, 2, 0, 0, 0, 0, 0),
            AiRouterTrendStore.Bucket(2_000L, 3, 0, 2, 1, 0, 1, 1),
            AiRouterTrendStore.Bucket(3_000L, 5, 1, 1, 2, 1, 2, 1),
        )
        val window = AiRouterTrendStore.aggregate(buckets, 2_000L)
        assertEquals(8L, window.total)
        assertEquals(1L, window.local)
        assertEquals(3L, window.claude)
        assertEquals(3L, window.meta)
        assertEquals(1L, window.multi)
        assertEquals(3L, window.fallbacks)
        assertEquals(2L, window.errors)
    }

    @Test
    fun aggregateEmptyWindowIsZero() {
        val window = AiRouterTrendStore.aggregate(emptyList(), 0L)
        assertEquals(0L, window.total)
        assertEquals(0.0, window.fallbackRatePercent, 0.0)
        assertEquals(0.0, window.errorRatePercent, 0.0)
    }
}

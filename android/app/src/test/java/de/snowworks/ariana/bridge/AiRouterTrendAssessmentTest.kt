package de.snowworks.ariana.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiRouterTrendAssessmentTest {
    private fun window(total: Long, fallbacks: Long = 0L, errors: Long = 0L) =
        AiRouterTrendStore.Window(
            total = total,
            local = total,
            claude = 0L,
            meta = 0L,
            multi = 0L,
            fallbacks = fallbacks,
            errors = errors,
        )

    @Test
    fun insufficientSamplesStayNeutral() {
        val result = AiRouterTrendAssessment.assess(
            recent = window(total = 5, errors = 2),
            baseline = window(total = 100, errors = 1),
        )
        assertEquals(AiRouterTrendAssessment.Level.INSUFFICIENT_DATA, result.level)
        assertTrue(result.reasons.contains("SAMPLE_SIZE"))
    }

    @Test
    fun smallRateChangesRemainStable() {
        val result = AiRouterTrendAssessment.assess(
            recent = window(total = 100, fallbacks = 15, errors = 10),
            baseline = window(total = 200, fallbacks = 20, errors = 10),
        )
        assertEquals(AiRouterTrendAssessment.Level.STABLE, result.level)
    }

    @Test
    fun tenPointIncreaseTriggersWatch() {
        val result = AiRouterTrendAssessment.assess(
            recent = window(total = 100, errors = 15),
            baseline = window(total = 100, errors = 5),
        )
        assertEquals(AiRouterTrendAssessment.Level.WATCH, result.level)
        assertTrue(result.reasons.contains("ERROR_RATE_UP"))
    }

    @Test
    fun twentyPointIncreaseTriggersDegraded() {
        val result = AiRouterTrendAssessment.assess(
            recent = window(total = 100, fallbacks = 30),
            baseline = window(total = 100, fallbacks = 10),
        )
        assertEquals(AiRouterTrendAssessment.Level.DEGRADED, result.level)
        assertTrue(result.reasons.contains("FALLBACK_RATE_UP"))
    }
}

package de.snowworks.ariana.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderQualityAssessmentTest {
    private fun window(
        selected: Long,
        executed: Long,
        successes: Long,
        errors: Long,
        circuit: Long = 0L,
        fallback: Long = 0L,
    ) = AiProviderQualityTrendStore.Window(
        selected = selected,
        executed = executed,
        successes = successes,
        errors = errors,
        circuitRejected = circuit,
        fallbackSelections = fallback,
    )

    @Test
    fun insufficientSamplesStayObservational() {
        val result = AiProviderQualityAssessment.assess(
            AiProviderQualityStore.Engine.CLAUDE,
            window(9, 5, 4, 1),
            window(30, 20, 18, 2),
        )
        assertEquals(AiProviderQualityAssessment.Level.INSUFFICIENT_DATA, result.level)
        assertEquals(listOf("SAMPLE_SIZE"), result.reasons)
    }

    @Test
    fun similarRecentAndBaselineAreStable() {
        val result = AiProviderQualityAssessment.assess(
            AiProviderQualityStore.Engine.META,
            window(20, 16, 14, 2, circuit = 2),
            window(80, 64, 56, 8, circuit = 8),
        )
        assertEquals(AiProviderQualityAssessment.Level.STABLE, result.level)
        assertTrue(result.reasons.isEmpty())
    }

    @Test
    fun moderateErrorIncreaseTriggersWatch() {
        val result = AiProviderQualityAssessment.assess(
            AiProviderQualityStore.Engine.CLAUDE,
            window(20, 10, 7, 3),
            window(80, 40, 36, 4),
        )
        assertEquals(AiProviderQualityAssessment.Level.WATCH, result.level)
        assertTrue(result.reasons.contains("ERROR_RATE_UP"))
    }

    @Test
    fun largeCircuitIncreaseTriggersDegraded() {
        val result = AiProviderQualityAssessment.assess(
            AiProviderQualityStore.Engine.META,
            window(20, 10, 9, 1, circuit = 8),
            window(80, 60, 54, 6, circuit = 4),
        )
        assertEquals(AiProviderQualityAssessment.Level.DEGRADED, result.level)
        assertTrue(result.reasons.contains("CIRCUIT_REJECT_UP"))
    }

    @Test
    fun executionRateDropCanTriggerDegraded() {
        val result = AiProviderQualityAssessment.assess(
            AiProviderQualityStore.Engine.CLAUDE,
            window(20, 8, 7, 1),
            window(80, 64, 58, 6),
        )
        assertEquals(AiProviderQualityAssessment.Level.DEGRADED, result.level)
        assertTrue(result.reasons.contains("EXECUTION_RATE_DOWN"))
    }
}

package de.snowworks.ariana.joyfun

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class X88CodeProcessorTest {
    private class FixedClock : JoyFunClock {
        private var now = 10_000L
        override fun nowMillis(): Long = now++
    }

    private fun v30(): JoyFunEvolutionState =
        JoyFunEvolution(FixedClock()).evolveTo(JoyFunState(), JoyFunVersion.V30)

    @Test
    fun consentLawBlocksExecution() {
        val result = X88CodeProcessor().process(
            evolution = v30(),
            input = X88CodeInput(
                signal = "IDEA",
                consent = false,
                memoryConnected = true,
                creationRequested = true,
            ),
        )

        assertEquals(X88ProcessingStatus.BLOCKED_BY_CONSENT, result.status)
        assertFalse(result.executable)
        assertEquals("BUILD", result.mappedSignal)
    }

    @Test
    fun ideaMapsToBuildAtCodeLevel() {
        val result = X88CodeProcessor().process(
            evolution = v30(),
            input = X88CodeInput(
                signal = "idea",
                consent = true,
                memoryConnected = true,
                creationRequested = true,
            ),
        )

        assertEquals(X88ProcessingStatus.READY, result.status)
        assertEquals(X88TechnicalStage.IDEA, result.technicalStage)
        assertEquals(X88TechnicalStage.BUILD, result.nextTechnicalStage)
        assertTrue(result.executable)
    }

    @Test
    fun testCannotEvolveWithoutEvidence() {
        val result = X88CodeProcessor().process(
            evolution = v30(),
            input = X88CodeInput(
                signal = "TEST",
                consent = true,
                memoryConnected = true,
                creationRequested = true,
            ),
        )

        assertEquals(X88ProcessingStatus.WAITING_EVIDENCE, result.status)
        assertFalse(result.executable)
        assertEquals("EVOLVE", result.mappedSignal)
    }

    @Test
    fun verifiedTestCanAdvanceToEvolve() {
        val result = X88CodeProcessor().process(
            evolution = v30(),
            input = X88CodeInput(
                signal = "TEST",
                consent = true,
                memoryConnected = true,
                creationRequested = true,
                evidence = X88Evidence(
                    buildPassed = true,
                    testsPassed = true,
                    regressionPassed = true,
                    commitSha = "abc123",
                    workflowRunId = 42L,
                ),
            ),
        )

        assertEquals(X88ProcessingStatus.READY, result.status)
        assertTrue(result.executable)
        assertEquals(X88TechnicalStage.EVOLVE, result.nextTechnicalStage)
    }

    @Test
    fun symbolicTransmutationResolvesWithoutPretendingToExecuteHardware() {
        val result = X88CodeProcessor().process(
            evolution = v30(),
            input = X88CodeInput(
                signal = "DARKNESS",
                consent = true,
                memoryConnected = true,
                creationRequested = true,
            ),
        )

        assertEquals("AWARENESS", result.mappedSignal)
        assertEquals(X88ProcessingStatus.READY, result.status)
        assertFalse(result.executable)
    }
}

package de.snowworks.ariana.gi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GiDecisionEngineTest {
    @Test
    fun highRiskCandidateLosesWhenOtherSignalsMatch() {
        val safe = ThoughtCandidate("safe", "SAFE_ROUTE", "safe", 0.9f, 0.1f, 0.9f, 0.9f)
        val risky = ThoughtCandidate("risky", "RISK_ROUTE", "risky", 0.9f, 0.95f, 0.9f, 0.9f)
        assertEquals("SAFE_ROUTE", DecisionFusion.select(listOf(safe, risky))?.first?.route)
    }

    @Test
    fun scoreAlwaysStaysNormalized() {
        val odd = ThoughtCandidate("odd", "TEST", "test", 5f, -5f, 5f, 5f, 5f)
        assertTrue(DecisionFusion.score(odd) in 0f..1f)
    }

    @Test
    fun emptyCandidatesTriggerRecovery() {
        assertEquals(GiMode.RECOVERY, CognitiveRouter.selectMode(GiInput("test", candidates = emptyList()), GiState()))
    }
}

class GiPolicyTest {
    private fun proposal(action: String) = GiActionProposal(action, "test", 1f, 0f, cycle = 1)

    @Test fun startRequiresConfirmation() =
        assertEquals(GiGateResult.CONFIRM, GiBridgePolicyGate.evaluate(proposal("camera_start")))

    @Test fun stopIsSafe() =
        assertEquals(GiGateResult.SAFE, GiBridgePolicyGate.evaluate(proposal("camera_stop")))

    @Test fun unknownActionIsBlocked() =
        assertEquals(GiGateResult.BLOCKED, GiBridgePolicyGate.evaluate(proposal("unknown_action")))

    @Test fun blockedCannotBecomeSafe() =
        assertFalse(GiSafetyInvariant.canChange(GiGateResult.BLOCKED, GiGateResult.SAFE))

    @Test fun confirmCannotBecomeSafe() =
        assertFalse(GiSafetyInvariant.canChange(GiGateResult.CONFIRM, GiGateResult.SAFE))

    @Test fun safeMayBecomeStricter() =
        assertTrue(GiSafetyInvariant.canChange(GiGateResult.SAFE, GiGateResult.BLOCKED))
}

class GiExperienceTest {
    @Test
    fun historyAdjustmentIsBounded() {
        val exp = GiExperience(
            key = "test",
            route = "ROUTE",
            actionType = null,
            attempts = 100,
            successes = 100,
            failures = 0,
            averageOutcome = 1f,
            lastOutcome = 1f,
            lastUsedAt = System.currentTimeMillis(),
        )
        assertTrue(ExperienceWeightedScoring.adjustment(exp) in -0.10f..0.10f)
    }
}

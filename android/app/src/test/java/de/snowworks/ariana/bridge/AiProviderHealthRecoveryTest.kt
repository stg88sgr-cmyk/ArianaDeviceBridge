package de.snowworks.ariana.bridge

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderHealthRecoveryTest {
    private val providerId = "claude-ai:api.anthropic.com:test"

    @After
    fun tearDown() {
        AiProviderHealth.reset(providerId)
    }

    @Test
    fun opensCircuitAfterThreeTransientFailuresAndAllowsSingleRecoveryProbe() {
        AiProviderHealth.recordFailure(providerId, "PROVIDER_NETWORK_FAILED", nowMs = 1_000L)
        AiProviderHealth.recordFailure(providerId, "PROVIDER_NETWORK_FAILED", nowMs = 1_100L)
        AiProviderHealth.recordFailure(providerId, "PROVIDER_NETWORK_FAILED", nowMs = 1_200L)

        val open = AiProviderHealth.snapshot(providerId, nowMs = 1_300L)
        assertTrue(open.circuitOpen)
        assertFalse(open.recoveryProbeReady)
        assertFalse(AiProviderHealth.acquireAttempt(providerId, nowMs = 1_300L))

        val afterCooldown = AiProviderHealth.snapshot(
            providerId,
            nowMs = 1_200L + AiProviderHealth.COOLDOWN_MS,
        )
        assertFalse(afterCooldown.circuitOpen)
        assertTrue(afterCooldown.recoveryProbeReady)

        assertTrue(
            AiProviderHealth.acquireAttempt(
                providerId,
                nowMs = 1_200L + AiProviderHealth.COOLDOWN_MS,
            ),
        )
        assertFalse(
            AiProviderHealth.acquireAttempt(
                providerId,
                nowMs = 1_200L + AiProviderHealth.COOLDOWN_MS + 1L,
            ),
        )
    }

    @Test
    fun failedHalfOpenProbeReopensCircuit() {
        repeat(AiProviderHealth.FAILURE_THRESHOLD) { index ->
            AiProviderHealth.recordFailure(providerId, "PROVIDER_UPSTREAM_FAILED", nowMs = 2_000L + index)
        }

        val recoveryTime = 2_002L + AiProviderHealth.COOLDOWN_MS
        assertTrue(AiProviderHealth.acquireAttempt(providerId, nowMs = recoveryTime))
        AiProviderHealth.recordFailure(providerId, "CLAUDE_PROVIDER_FAILED", nowMs = recoveryTime)

        val snapshot = AiProviderHealth.snapshot(providerId, nowMs = recoveryTime + 1L)
        assertTrue(snapshot.circuitOpen)
        assertFalse(snapshot.recoveryProbeReady)
    }

    @Test
    fun successfulRecoveryResetsProviderHealth() {
        repeat(AiProviderHealth.FAILURE_THRESHOLD) { index ->
            AiProviderHealth.recordFailure(providerId, "PROVIDER_DNS_FAILED", nowMs = 3_000L + index)
        }

        val recoveryTime = 3_002L + AiProviderHealth.COOLDOWN_MS
        assertTrue(AiProviderHealth.acquireAttempt(providerId, nowMs = recoveryTime))
        AiProviderHealth.recordSuccess(providerId)

        val snapshot = AiProviderHealth.snapshot(providerId, nowMs = recoveryTime + 1L)
        assertFalse(snapshot.circuitOpen)
        assertFalse(snapshot.recoveryProbeReady)
        assertTrue(snapshot.consecutiveFailures == 0)
    }
}

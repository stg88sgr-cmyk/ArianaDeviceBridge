package de.snowworks.ariana.bridge

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderHealthTest {
    private val providerId = "meta-ai:test"

    @After
    fun tearDown() {
        AiProviderHealth.reset()
    }

    @Test
    fun opensCircuitAfterThreeTransientFailures() {
        assertTrue(AiProviderHealth.acquireAttempt(providerId, nowMs = 1_000L))
        AiProviderHealth.recordFailure(providerId, "PROVIDER_NETWORK_FAILED", nowMs = 1_000L)
        AiProviderHealth.recordFailure(providerId, "PROVIDER_REMOTE_TIMEOUT", nowMs = 2_000L)
        AiProviderHealth.recordFailure(providerId, "PROVIDER_UPSTREAM_FAILED", nowMs = 3_000L)

        assertFalse(AiProviderHealth.acquireAttempt(providerId, nowMs = 3_001L))
        assertTrue(AiProviderHealth.snapshot(providerId, nowMs = 3_001L).circuitOpen)
    }

    @Test
    fun cooldownAllowsOneHalfOpenProbeAndSuccessResets() {
        repeat(3) { index ->
            AiProviderHealth.recordFailure(
                providerId,
                "PROVIDER_NETWORK_FAILED",
                nowMs = 1_000L + index,
            )
        }
        val reopenAt = 1_002L + AiProviderHealth.COOLDOWN_MS

        assertTrue(AiProviderHealth.acquireAttempt(providerId, nowMs = reopenAt))
        assertFalse(AiProviderHealth.acquireAttempt(providerId, nowMs = reopenAt))

        AiProviderHealth.recordSuccess(providerId)
        assertTrue(AiProviderHealth.acquireAttempt(providerId, nowMs = reopenAt + 1L))
        assertFalse(AiProviderHealth.snapshot(providerId, nowMs = reopenAt + 1L).circuitOpen)
    }

    @Test
    fun failedHalfOpenProbeReopensImmediately() {
        repeat(3) { index ->
            AiProviderHealth.recordFailure(
                providerId,
                "PROVIDER_NETWORK_FAILED",
                nowMs = 10_000L + index,
            )
        }
        val probeAt = 10_002L + AiProviderHealth.COOLDOWN_MS
        assertTrue(AiProviderHealth.acquireAttempt(providerId, nowMs = probeAt))

        AiProviderHealth.recordFailure(providerId, "PROVIDER_NETWORK_FAILED", nowMs = probeAt)
        assertFalse(AiProviderHealth.acquireAttempt(providerId, nowMs = probeAt + 1L))
    }

    @Test
    fun authAndConfigurationErrorsDoNotOpenCircuit() {
        repeat(6) { index ->
            AiProviderHealth.recordFailure(providerId, "PROVIDER_AUTH_FAILED", nowMs = 20_000L + index)
        }
        repeat(6) { index ->
            AiProviderHealth.recordFailure(providerId, "PROVIDER_CONFIG_INVALID", nowMs = 21_000L + index)
        }

        assertTrue(AiProviderHealth.acquireAttempt(providerId, nowMs = 22_000L))
        assertFalse(AiProviderHealth.snapshot(providerId, nowMs = 22_000L).circuitOpen)
    }
}

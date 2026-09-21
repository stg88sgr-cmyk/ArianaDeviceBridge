package de.snowworks.ariana.neuro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V39RecoveryRuntimeTest {
    @Test
    fun healthyRuntimeProducesNoRecoveryAction() {
        val runtime = V39RecoveryRuntime.createForTest()
        val snapshot = runtime.boot()

        assertEquals(X88HealthStatus.GREEN, snapshot.healthStatus)
        assertEquals(X88RecoveryAction.NONE, snapshot.lastAction)
        assertTrue(snapshot.safeToProceed)
        assertEquals(0, snapshot.incidentCount)
    }

    @Test
    fun blockedHealthProducesHoldForSnapshotRestore() {
        val evidence = X88EvidenceEngine()
        val guard = X88RecoveryGuard(evidence)
        val health = X88HealthSnapshot(
            stage = 38,
            status = X88HealthStatus.BLOCKED,
            components = listOf(
                X88HealthComponent(
                    id = "test-component",
                    status = X88HealthStatus.BLOCKED,
                    detail = "test-block",
                ),
            ),
            timestamp = 1000L,
        )

        val incident = guard.evaluate(health)

        assertEquals(X88RecoveryAction.HOLD_FOR_SNAPSHOT_RESTORE, incident?.action)
        assertEquals(1, guard.count())
        assertFalse(evidence.count() == 0)
    }

    @Test
    fun degradedHealthRequestsRecheck() {
        val guard = X88RecoveryGuard(X88EvidenceEngine())
        val health = X88HealthSnapshot(
            stage = 38,
            status = X88HealthStatus.DEGRADED,
            components = listOf(
                X88HealthComponent(
                    id = "test-component",
                    status = X88HealthStatus.DEGRADED,
                    detail = "test-degraded",
                ),
            ),
            timestamp = 2000L,
        )

        assertEquals(X88RecoveryAction.RECHECK, guard.evaluate(health)?.action)
    }
}

package de.snowworks.ariana.neuro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class V38HealthRuntimeTest {
    @Test
    fun healthyRuntimeReportsGreenAndProducesEvidence() {
        val runtime = V38HealthRuntime.initializeForTest()
        val before = runtime.base.evidence.count()
        val snapshot = runtime.health()

        assertEquals(38, snapshot.stage)
        assertEquals(X88HealthStatus.GREEN, snapshot.status)
        assertTrue(snapshot.components.isNotEmpty())
        assertTrue(runtime.base.evidence.count() > before)
    }

    @Test
    fun blockedNeuroRuntimeIsNotReportedAsGreen() {
        val base = V37EvidenceRuntime.createForTest()
        val monitor = X88HealthMonitor(base.evidence)

        val snapshot = monitor.check(base)

        assertEquals(X88HealthStatus.BLOCKED, snapshot.status)
    }
}

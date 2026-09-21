package de.snowworks.ariana.health

import org.junit.Assert.*
import org.junit.Test

class X88HealthMonitorTest {
    @Test fun healthyRequiresChecks() {
        val monitor = X88HealthMonitor()
        assertFalse(monitor.snapshot().healthy)
        assertEquals(X88HealthLevel.HEALTHY, monitor.report(
            X88HealthCheck("persistence", X88HealthLevel.HEALTHY, "ok")
        ).level)
        assertTrue(monitor.snapshot().healthy)
    }

    @Test fun degradedAndUnavailableAggregateSafely() {
        val monitor = X88HealthMonitor()
        monitor.report(X88HealthCheck("bridge", X88HealthLevel.DEGRADED, "slow"))
        assertEquals(X88HealthLevel.DEGRADED, monitor.snapshot().level)
        monitor.report(X88HealthCheck("permissions", X88HealthLevel.UNAVAILABLE, "blocked"))
        assertEquals(X88HealthLevel.UNAVAILABLE, monitor.snapshot().level)
        assertFalse(monitor.snapshot().healthy)
    }

    @Test fun componentReplacementIsDeterministic() {
        val monitor = X88HealthMonitor()
        monitor.report(X88HealthCheck("runtime", X88HealthLevel.DEGRADED, "old"))
        monitor.report(X88HealthCheck("runtime", X88HealthLevel.HEALTHY, "new"))
        val report = monitor.snapshot()
        assertEquals(1, report.checks.size)
        assertEquals("new", report.checks.single().detail)
    }
}

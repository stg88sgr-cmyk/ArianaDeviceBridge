package de.snowworks.ariana.recovery

import de.snowworks.ariana.health.X88HealthCheck
import de.snowworks.ariana.health.X88HealthLevel
import de.snowworks.ariana.health.X88HealthMonitor
import kotlin.test.Test
import kotlin.test.assertEquals

class X88RecoveryTest {
    private val monitor = X88HealthMonitor()

    @Test
    fun healthyNeedsNoRecovery() {
        val report = monitor.report(X88HealthCheck("core", X88HealthLevel.HEALTHY, "ok"))
        val decision = X88RecoveryCoordinator().decide(report)

        assertEquals(X88RecoveryAction.NO_ACTION, decision.action)
    }

    @Test
    fun degradedRequestsRecheck() {
        val report = monitor.report(X88HealthCheck("core", X88HealthLevel.DEGRADED, "slow"))
        val decision = X88RecoveryCoordinator().decide(report)

        assertEquals(X88RecoveryAction.RECHECK, decision.action)
    }

    @Test
    fun unavailableReloadsStateBeforeEscalation() {
        val report = monitor.report(X88HealthCheck("core", X88HealthLevel.UNAVAILABLE, "lost"))
        val decision = X88RecoveryCoordinator().decide(report)

        assertEquals(X88RecoveryAction.RELOAD_STATE, decision.action)
    }
}

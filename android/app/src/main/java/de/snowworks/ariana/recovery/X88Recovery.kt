package de.snowworks.ariana.recovery

import de.snowworks.ariana.health.X88HealthLevel
import de.snowworks.ariana.health.X88HealthReport

enum class X88RecoveryAction {
    NO_ACTION,
    RECHECK,
    RELOAD_STATE,
    ESCALATE,
}

data class X88RecoveryDecision(
    val stage: Int,
    val action: X88RecoveryAction,
    val reason: String,
)

class X88RecoveryCoordinator(private val stage: Int = 39) {
    fun decide(report: X88HealthReport): X88RecoveryDecision = when (report.level) {
        X88HealthLevel.HEALTHY ->
            X88RecoveryDecision(stage, X88RecoveryAction.NO_ACTION, "health is healthy")

        X88HealthLevel.DEGRADED ->
            X88RecoveryDecision(stage, X88RecoveryAction.RECHECK, "health is degraded; recheck before recovery")

        X88HealthLevel.UNAVAILABLE ->
            X88RecoveryDecision(stage, X88RecoveryAction.RELOAD_STATE, "health is unavailable; reload persisted state before escalation")
    }
}

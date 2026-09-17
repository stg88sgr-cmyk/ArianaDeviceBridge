package de.snowworks.ariana.system

/**
 * Pure policy for process-local X88 autonomy.
 * It never invents authority: autonomy may run only while the existing user master
 * gate is enabled and Stop-All is not active.
 */
data class X88AutonomyDecision(
    val shouldRun: Boolean,
    val shouldOwnSession: Boolean,
)

object X88AutonomyPolicy {
    fun decide(userMasterEnabled: Boolean, stopAllBlocked: Boolean): X88AutonomyDecision {
        val allowed = userMasterEnabled && !stopAllBlocked
        return X88AutonomyDecision(
            shouldRun = allowed,
            shouldOwnSession = allowed,
        )
    }
}

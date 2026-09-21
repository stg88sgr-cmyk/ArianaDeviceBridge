package de.snowworks.ariana.neuro

enum class X88RecoveryAction {
    NONE,
    RECHECK,
    QUARANTINE,
    HOLD_FOR_SNAPSHOT_RESTORE,
}

data class X88RecoveryIncident(
    val id: String,
    val timestamp: Long,
    val status: X88HealthStatus,
    val component: String,
    val action: X88RecoveryAction,
    val reason: String,
)

data class V39RecoverySnapshot(
    val stage: Int,
    val healthStatus: X88HealthStatus,
    val incidentCount: Int,
    val lastAction: X88RecoveryAction,
    val safeToProceed: Boolean,
)

/**
 * V39 recovery guard.
 *
 * Recovery is deliberately conservative: it can detect, quarantine and hold.
 * Actual state restoration is reserved for V40 Snapshot Core. No Android
 * permissions, provider selection or device actions are performed here.
 */
class X88RecoveryGuard(
    private val evidence: X88EvidenceEngine,
    private val maxIncidents: Int = 64,
) {
    private val incidents = java.util.ArrayDeque<X88RecoveryIncident>()

    init {
        require(maxIncidents > 0)
    }

    @Synchronized
    fun evaluate(health: X88HealthSnapshot): X88RecoveryIncident? {
        val failed = health.components.firstOrNull { it.status != X88HealthStatus.GREEN }
            ?: return null

        val action = when (failed.status) {
            X88HealthStatus.DEGRADED -> X88RecoveryAction.RECHECK
            X88HealthStatus.BLOCKED -> X88RecoveryAction.HOLD_FOR_SNAPSHOT_RESTORE
            X88HealthStatus.GREEN -> X88RecoveryAction.NONE
        }

        val incident = X88RecoveryIncident(
            id = "${health.timestamp}-${failed.id}",
            timestamp = health.timestamp,
            status = failed.status,
            component = failed.id,
            action = action,
            reason = failed.detail,
        )

        if (incidents.size == maxIncidents) incidents.removeFirst()
        incidents.addLast(incident)

        evidence.record(
            kind = EvidenceKind.HEALTH,
            source = "x88-v39-recovery-guard",
            claim = "recovery-decision",
            observed = "${failed.id}:${action.name}",
            timestamp = health.timestamp,
        )
        return incident
    }

    @Synchronized
    fun recent(limit: Int = 16): List<X88RecoveryIncident> =
        incidents.toList().takeLast(limit.coerceIn(0, maxIncidents))

    @Synchronized
    fun count(): Int = incidents.size
}

class V39RecoveryRuntime private constructor(
    val base: V38HealthRuntime,
    val guard: X88RecoveryGuard,
) {
    @Volatile
    private var booted = false

    @Synchronized
    fun boot(): V39RecoverySnapshot {
        if (!booted) {
            base.boot()
            booted = true
        }
        return check()
    }

    @Synchronized
    fun check(): V39RecoverySnapshot {
        val health = base.health()
        val incident = guard.evaluate(health)
        val action = incident?.action ?: X88RecoveryAction.NONE

        return V39RecoverySnapshot(
            stage = 39,
            healthStatus = health.status,
            incidentCount = guard.count(),
            lastAction = action,
            safeToProceed = health.status == X88HealthStatus.GREEN,
        )
    }

    companion object {
        fun createForTest(): V39RecoveryRuntime {
            val base = V38HealthRuntime.initializeForTest()
            return V39RecoveryRuntime(base, X88RecoveryGuard(base.base.evidence))
        }
    }
}

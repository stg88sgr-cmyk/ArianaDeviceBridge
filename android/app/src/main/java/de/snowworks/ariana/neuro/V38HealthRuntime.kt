package de.snowworks.ariana.neuro

enum class X88HealthStatus {
    GREEN,
    DEGRADED,
    BLOCKED,
}

data class X88HealthComponent(
    val id: String,
    val status: X88HealthStatus,
    val detail: String,
)

data class X88HealthSnapshot(
    val stage: Int,
    val status: X88HealthStatus,
    val components: List<X88HealthComponent>,
    val timestamp: Long,
)

/**
 * V38 central health monitor.
 *
 * Read-only orchestration over existing runtime health gates. It does not
 * grant permissions, select providers, or execute device actions.
 */
class X88HealthMonitor(
    private val evidence: X88EvidenceEngine,
) {
    @Synchronized
    fun check(runtime: V37EvidenceRuntime): X88HealthSnapshot {
        val neuro = runtime.health()
        val components = listOf(
            X88HealthComponent(
                id = "neuro-runtime",
                status = if (neuro.green) X88HealthStatus.GREEN else X88HealthStatus.BLOCKED,
                detail = "runtimeVersion=${neuro.version}",
            ),
            X88HealthComponent(
                id = "inneres-werden-persistence",
                status = if (runtime.base.trainer.persistenceHealthy) X88HealthStatus.GREEN
                else X88HealthStatus.DEGRADED,
                detail = "snapshot-store-health=${runtime.base.trainer.persistenceHealthy}",
            ),
            X88HealthComponent(
                id = "evidence-engine",
                status = if (runtime.evidence.id in neuro.modules) X88HealthStatus.GREEN
                else X88HealthStatus.BLOCKED,
                detail = "records=${runtime.evidence.count()}",
            ),
        )

        val overall = when {
            components.any { it.status == X88HealthStatus.BLOCKED } -> X88HealthStatus.BLOCKED
            components.any { it.status == X88HealthStatus.DEGRADED } -> X88HealthStatus.DEGRADED
            else -> X88HealthStatus.GREEN
        }

        val snapshot = X88HealthSnapshot(
            stage = 38,
            status = overall,
            components = components,
            timestamp = System.currentTimeMillis(),
        )

        evidence.record(
            kind = EvidenceKind.HEALTH,
            source = "x88-v38-health-monitor",
            claim = "v38-health-status",
            observed = overall.name,
            timestamp = snapshot.timestamp,
        )

        return snapshot
    }
}

class V38HealthRuntime private constructor(
    val base: V37EvidenceRuntime,
    val monitor: X88HealthMonitor,
) {
    @Volatile
    private var booted = false

    @Synchronized
    fun boot() {
        if (booted) return
        base.boot()
        booted = true
        monitor.check(base)
    }

    fun health(): X88HealthSnapshot = monitor.check(base)

    companion object {
        @Volatile
        private var processRuntime: V38HealthRuntime? = null

        fun createForTest(): V38HealthRuntime {
            val base = V37EvidenceRuntime.createForTest()
            return V38HealthRuntime(base, X88HealthMonitor(base.evidence))
        }

        fun initializeForTest(): V38HealthRuntime {
            return createForTest().also { it.boot() }
        }

        fun currentOrNull(): V38HealthRuntime? = processRuntime
    }
}

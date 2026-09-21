package de.snowworks.ariana.neuro

import android.content.Context

data class V37EvidenceSnapshot(
    val stage: Int,
    val runtimeVersion: Int,
    val green: Boolean,
    val evidenceCount: Int,
    val inneresWerdenPersistenceHealthy: Boolean,
)

/**
 * V37 composes V32 stabilization with a bounded evidence layer.
 * Evidence collection is observational only and cannot issue device actions.
 */
class V37EvidenceRuntime private constructor(
    val base: V32InneresWerdenRuntime,
    val evidence: X88EvidenceEngine,
) {
    @Volatile
    private var booted = false

    @Synchronized
    fun boot() {
        if (booted) return
        base.boot()
        base.base.base.fabric.register(evidence)
        booted = true

        evidence.record(
            kind = EvidenceKind.HEALTH,
            source = id,
            claim = "v37-runtime-booted",
            observed = "true",
        )
    }

    val id: String = "x88-v37-runtime"

    fun health(): NeuroHealthReport {
        val baseReport = base.health()
        val stage37 = booted &&
            baseReport.green &&
            evidence.id in base.base.base.fabric.moduleIds()

        return NeuroHealthReport(
            version = 37,
            green = stage37,
            stages = baseReport.stages + NeuroStageHealth(
                version = 37,
                name = "evidence-engine",
                healthy = stage37,
            ),
            modules = base.base.base.fabric.moduleIds(),
        )
    }

    fun snapshot(): V37EvidenceSnapshot {
        val report = health()
        return V37EvidenceSnapshot(
            stage = 37,
            runtimeVersion = report.version,
            green = report.green,
            evidenceCount = evidence.count(),
            inneresWerdenPersistenceHealthy = base.trainer.persistenceHealthy,
        )
    }

    companion object {
        @Volatile
        private var processRuntime: V37EvidenceRuntime? = null

        fun createForTest(): V37EvidenceRuntime {
            val base = V32InneresWerdenRuntime.createForTest()
            return V37EvidenceRuntime(base, X88EvidenceEngine())
        }

        fun initialize(context: Context): V37EvidenceRuntime {
            processRuntime?.let { return it }
            return synchronized(this) {
                processRuntime ?: V37EvidenceRuntime(
                    V32InneresWerdenRuntime.initialize(context),
                    X88EvidenceEngine(),
                ).also {
                    it.boot()
                    processRuntime = it
                }
            }
        }

        fun currentOrNull(): V37EvidenceRuntime? = processRuntime
    }
}

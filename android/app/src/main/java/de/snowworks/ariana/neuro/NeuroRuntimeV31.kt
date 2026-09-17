package de.snowworks.ariana.neuro

/**
 * Read-only V31 runtime snapshot for UI/diagnostics.
 *
 * The snapshot intentionally exposes only bounded runtime metadata. It never
 * contains user prompts, signal payloads, credentials or Android grants.
 */
data class V31RuntimeSnapshot(
    val stage: Int,
    val runtimeVersion: Int,
    val green: Boolean,
    val healthyStages: Int,
    val totalStages: Int,
    val moduleCount: Int,
    val lifecycleMode: String,
    val telemetryEntries: Int,
    val coherenceSignature: String,
)

/**
 * V31 - read-only runtime observability gate over V30.
 *
 * This layer does not implement [NeuroModule], does not emit ACTION_REQUEST and
 * has no execution API. It exists only to make the already-running V16-V30
 * health state inspectable by the local X-88 UI and diagnostics.
 */
class V31NeuroRuntime private constructor(
    val base: V30NeuroRuntime,
) {
    @Volatile
    private var booted: Boolean = false

    @Synchronized
    fun boot() {
        if (booted) return
        base.boot()
        booted = true
    }

    fun health(): NeuroHealthReport {
        val baseReport = base.health()
        val stage31 = booted &&
            baseReport.version == 30 &&
            baseReport.green &&
            baseReport.stages.map { it.version } == (16..30).toList()

        val stages = baseReport.stages + NeuroStageHealth(
            version = 31,
            name = "runtime-observability-gate",
            healthy = stage31,
        )

        return NeuroHealthReport(
            version = 31,
            green = stages.all { it.healthy },
            stages = stages,
            modules = baseReport.modules,
        )
    }

    fun snapshot(): V31RuntimeSnapshot {
        val report = health()
        return V31RuntimeSnapshot(
            stage = 31,
            runtimeVersion = report.version,
            green = report.green,
            healthyStages = report.stages.count { it.healthy },
            totalStages = report.stages.size,
            moduleCount = report.modules.size,
            lifecycleMode = base.lifecycle.mode.name.lowercase(),
            telemetryEntries = base.telemetry.snapshot().size,
            coherenceSignature = base.coherenceSignature,
        )
    }

    companion object {
        @Volatile
        private var processRuntime: V31NeuroRuntime? = null

        fun createForTest(): V31NeuroRuntime =
            V31NeuroRuntime(V30NeuroRuntime.createForTest())

        fun initialize(): V31NeuroRuntime {
            processRuntime?.let { return it }
            return synchronized(this) {
                processRuntime ?: V31NeuroRuntime(V30NeuroRuntime.initialize()).also { runtime ->
                    runtime.boot()
                    processRuntime = runtime
                }
            }
        }

        fun currentOrNull(): V31NeuroRuntime? = processRuntime
    }
}

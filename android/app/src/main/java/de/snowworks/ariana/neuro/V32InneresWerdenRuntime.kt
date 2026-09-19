package de.snowworks.ariana.neuro

data class V32InneresWerdenSnapshot(
    val stage: Int,
    val runtimeVersion: Int,
    val green: Boolean,
    val trainingSteps: Long,
    val modelVersion: Int,
    val coherenceSignature: String,
)

/**
 * V32 activates the trainable Inneres-Werden model on top of the verified V31
 * runtime. Training remains local, numeric and proposal-free.
 */
class V32InneresWerdenRuntime private constructor(
    val base: V31NeuroRuntime,
    val trainer: InneresWerdenTrainer,
) {
    @Volatile
    private var booted = false

    @Synchronized
    fun boot() {
        if (booted) return
        base.boot()
        base.base.fabric.register(trainer)
        booted = true
    }

    fun health(): NeuroHealthReport {
        val baseReport = base.health()
        val stage32 = booted &&
            baseReport.green &&
            trainer.id in base.base.fabric.moduleIds()

        val stages = baseReport.stages + NeuroStageHealth(
            version = 32,
            name = "inneres-werden-trainable-model",
            healthy = stage32,
        )

        return NeuroHealthReport(
            version = 32,
            green = stages.all { it.healthy },
            stages = stages,
            modules = base.base.fabric.moduleIds(),
        )
    }

    fun snapshot(): V32InneresWerdenSnapshot {
        val report = health()
        return V32InneresWerdenSnapshot(
            stage = 32,
            runtimeVersion = report.version,
            green = report.green,
            trainingSteps = trainer.snapshot().steps,
            modelVersion = trainer.snapshot().version,
            coherenceSignature = base.base.coherenceSignature,
        )
    }

    companion object {
        @Volatile
        private var processRuntime: V32InneresWerdenRuntime? = null

        fun createForTest(): V32InneresWerdenRuntime =
            V32InneresWerdenRuntime(
                base = V31NeuroRuntime.createForTest(),
                trainer = InneresWerdenTrainer(),
            )

        fun initialize(): V32InneresWerdenRuntime {
            processRuntime?.let { return it }
            return synchronized(this) {
                processRuntime ?: createForTest().also {
                    it.boot()
                    processRuntime = it
                }
            }
        }

        fun currentOrNull(): V32InneresWerdenRuntime? = processRuntime
    }
}

package de.snowworks.ariana.neuro

import android.content.Context

data class V32InneresWerdenSnapshot(
    val stage: Int,
    val runtimeVersion: Int,
    val green: Boolean,
    val trainingSteps: Long,
    val modelVersion: Int,
    val coherenceSignature: String,
    val persistenceHealthy: Boolean,
)

/**
 * V32 activates the trainable Inneres-Werden model on top of the verified V31
 * runtime. Training remains local, numeric and proposal-free.
 */
class V32InneresWerdenRuntime private constructor(
    val base: V31NeuroRuntime,
    val trainer: InneresWerdenTrainer,
    val snapshotStore: InneresWerdenSnapshotStore,
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
            trainer.id in base.base.fabric.moduleIds() &&
            trainer.persistenceHealthy

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
        val modelSnapshot = trainer.snapshot()
        return V32InneresWerdenSnapshot(
            stage = 32,
            runtimeVersion = report.version,
            green = report.green,
            trainingSteps = modelSnapshot.steps,
            modelVersion = modelSnapshot.version,
            coherenceSignature = base.base.coherenceSignature,
            persistenceHealthy = trainer.persistenceHealthy,
        )
    }

    companion object {
        @Volatile
        private var processRuntime: V32InneresWerdenRuntime? = null

        fun createForTest(
            snapshotStore: InneresWerdenSnapshotStore = InMemoryInneresWerdenSnapshotStore(),
        ): V32InneresWerdenRuntime {
            val initial = snapshotStore.load() ?: InneresWerdenModel.defaultSnapshot()
            val model = InneresWerdenModel(initial)
            return V32InneresWerdenRuntime(
                base = V31NeuroRuntime.createForTest(),
                trainer = InneresWerdenTrainer(model, snapshotStore),
                snapshotStore = snapshotStore,
            )
        }

        fun initialize(context: Context): V32InneresWerdenRuntime {
            processRuntime?.let { return it }
            return synchronized(this) {
                processRuntime ?: createForTest(
                    snapshotStore = SharedPreferencesInneresWerdenSnapshotStore(context),
                ).also {
                    it.boot()
                    processRuntime = it
                }
            }
        }

        fun currentOrNull(): V32InneresWerdenRuntime? = processRuntime
    }
}

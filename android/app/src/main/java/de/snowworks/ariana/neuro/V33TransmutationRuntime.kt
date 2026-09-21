package de.snowworks.ariana.neuro

/**
 * V33 adapter: derives a read-only TransmutationState from the existing V32
 * Inneres-Werden snapshot. No memory, trainer, resonance, or routing mutation.
 */
class V33TransmutationRuntime private constructor(
    private val inneresWerden: V32InneresWerdenRuntime,
) {
    fun evaluate(snapshot: V32InneresWerdenSnapshot): TransmutationState =
        X88TransmutationSchema.transform(
            TransmutationInput(
                experience = snapshot.trainingSteps.toDouble().coerceAtMost(1_000.0) / 1_000.0,
                valuesAlignment = snapshot.resonance.coherence.coerceIn(0.0, 1.0),
                principlesConsistency = snapshot.resonance.stability.coerceIn(0.0, 1.0),
                evidenceQuality = if (snapshot.green) 1.0 else 0.0,
                coherence = snapshot.resonance.coherence.coerceIn(0.0, 1.0),
                growth = snapshot.modelVersion.toDouble().coerceAtMost(100.0) / 100.0,
            ),
        )

    fun evaluateCurrent(): TransmutationState? =
        inneresWerden.currentOrNull()?.let { evaluate(it.snapshot()) }

    companion object {
        fun createForTest(inneresWerden: V32InneresWerdenRuntime): V33TransmutationRuntime =
            V33TransmutationRuntime(inneresWerden)

        fun initialize(): V33TransmutationRuntime? =
            V32InneresWerdenRuntime.currentOrNull()?.let(::V33TransmutationRuntime)
    }
}

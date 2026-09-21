package de.snowworks.ariana.neuro

/**
 * Small local state holder that connects Inneres-Werden predictions to X88
 * without introducing a new provider dependency or platform permission.
 */
class ResonanceRuntime(
    initial: ResonanceState = ResonanceState(
        coherence = 0.0,
        growth = 0.0,
        correction = 0.0,
        stability = 0.0,
    ),
) {
    @Volatile
    private var state: ResonanceState = initial

    fun current(): ResonanceState = state

    @Synchronized
    fun applyPrediction(
        prediction: InneresWerdenPrediction,
        correctionSignal: Double = 0.0,
    ): ResonanceState {
        val next = ResonanceState.fromInneresWerden(prediction, correctionSignal)
        state = next
        return next
    }
}

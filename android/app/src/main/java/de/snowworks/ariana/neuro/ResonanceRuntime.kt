package de.snowworks.ariana.neuro

import de.snowworks.app.resonance.ResonanceMapper
import de.snowworks.app.resonance.ResonanceSignature
import de.snowworks.app.resonance.ResonanceStabilizer
import de.snowworks.app.resonance.SpectralBands
import de.snowworks.app.resonance.StableResonanceFrame
import de.snowworks.app.resonance.X88RuneGraph

/**
 * X88 control-plane bridge for the deterministic resonance core.
 *
 * The core is local and deterministic. It is not a measurement of emotions,
 * consciousness, or physical "energy".
 */
class ResonanceRuntime(
    initial: ResonanceState = ResonanceState(0.0, 0.0, 0.0, 0.0),
) {
    @Volatile
    private var state: ResonanceState = initial

    private val stabilizer = ResonanceStabilizer()
    private val coreSignature: ResonanceSignature = ResonanceMapper.map(X88RuneGraph.default)

    fun current(): ResonanceState = state
    fun coreSignature(): ResonanceSignature = coreSignature

    @Synchronized
    fun applyPrediction(
        prediction: InneresWerdenPrediction,
        correctionSignal: Double = 0.0,
    ): ResonanceState {
        val next = ResonanceState.fromInneresWerden(prediction, correctionSignal)
        state = next
        return next
    }

    @Synchronized
    fun processFrame(
        inputEnergy: Float,
        dominantHz: Float,
        bands: SpectralBands = SpectralBands(),
    ): StableResonanceFrame =
        stabilizer.process(
            inputEnergy = inputEnergy,
            dominantHz = dominantHz,
            bands = bands,
            generatedFrequencyHz = coreSignature.fundamentalHz.toFloat(),
        )

    fun resetFrameStabilizer() {
        stabilizer.reset()
    }
}
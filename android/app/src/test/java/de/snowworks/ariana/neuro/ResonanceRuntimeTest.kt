package de.snowworks.ariana.neuro

import kotlin.test.Test
import kotlin.test.assertEquals

class ResonanceRuntimeTest {
    @Test
    fun exposesDeterministicCoreSignature() {
        val runtime = ResonanceRuntime()
        val signature = runtime.coreSignature()

        assertEquals(222.0 * (1.0 + 7.0 * 0.015) * (1.0 + 7.0 / 20.0 * 0.05), signature.fundamentalHz, 0.000001)
        assertEquals(3, signature.harmonicsHz.size)
        assertTrue(signature.fingerprint.isNotBlank())
    }

    @Test
    fun stabilizerRejectsGeneratedCoreFrequencyFeedback() {
        val runtime = ResonanceRuntime()
        val frame = runtime.processFrame(
            inputEnergy = 0.5f,
            dominantHz = runtime.coreSignature().fundamentalHz.toFloat(),
        )

        assertTrue(frame.feedbackRejected)
        assertEquals(0f, frame.bands.mids, 0f)
    }

    @Test
    fun predictionUpdatesLocalResonanceState() {
        val runtime = ResonanceRuntime()

        val state = runtime.applyPrediction(
            InneresWerdenPrediction(0.8, 0.6, 0.9),
            correctionSignal = -0.2,
        )

        assertEquals(0.8, state.coherence)
        assertEquals(0.6, state.growth)
        assertEquals(-0.2, state.correction)
        assertEquals(0.9, state.stability)
        assertEquals(state, runtime.current())
    }
}

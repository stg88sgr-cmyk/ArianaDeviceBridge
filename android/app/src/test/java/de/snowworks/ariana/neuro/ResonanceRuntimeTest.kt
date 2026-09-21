package de.snowworks.ariana.neuro

import kotlin.test.Test
import kotlin.test.assertEquals

class ResonanceRuntimeTest {
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

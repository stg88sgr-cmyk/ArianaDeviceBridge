package de.snowworks.ariana.neuro

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResonanceStateTest {
    @Test
    fun inneresWerdenPredictionMapsToBoundedResonanceState() {
        val state = ResonanceState.fromInneresWerden(
            InneresWerdenPrediction(0.8, 0.6, 0.9),
        )
        assertEquals(0.8, state.coherence)
        assertEquals(0.6, state.growth)
        assertEquals(0.9, state.stability)
        assertEquals("growth", state.marker)
    }

    @Test
    fun blendClampsWeightAndPreservesBounds() {
        val a = ResonanceState(-1.0, 0.0, 0.0, 0.2)
        val b = ResonanceState(1.0, 1.0, 1.0, 1.0, "growth")
        val state = a.blendedWith(b, 0.75)
        assertTrue(state.coherence in -1.0..1.0)
        assertTrue(state.growth in -1.0..1.0)
        assertTrue(state.correction in -1.0..1.0)
        assertTrue(state.stability in 0.0..1.0)
        assertEquals("growth", state.marker)
    }
}

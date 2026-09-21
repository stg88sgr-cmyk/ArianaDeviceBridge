package de.snowworks.ariana.neuro

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class X88TransmutationSchemaTest {

    @Test
    fun clampsInputAndProducesBoundedState() {
        val state = X88TransmutationSchema.transform(
            TransmutationInput(
                experience = 4.0,
                valuesAlignment = -1.0,
                principlesConsistency = 2.0,
                evidenceQuality = 0.5,
                coherence = 0.8,
                growth = 0.7
            )
        )

        assertTrue(state.clarity in 0.0..1.0)
        assertTrue(state.creativity in 0.0..1.0)
        assertTrue(state.unity in 0.0..1.0)
        assertTrue(state.protection in 0.0..1.0)
        assertTrue(state.reflection in 0.0..1.0)
        assertTrue(state.growth in 0.0..1.0)
        assertTrue(state.stability in 0.0..1.0)
    }

    @Test
    fun coherentEvidenceProducesClarityAndUnity() {
        val state = X88TransmutationSchema.transform(
            TransmutationInput(
                experience = 0.8,
                valuesAlignment = 0.9,
                principlesConsistency = 0.9,
                evidenceQuality = 0.95,
                coherence = 0.95,
                growth = 0.8
            )
        )

        assertTrue(state.clarity > 0.8)
        assertTrue(state.unity > 0.8)
        assertEquals(TransmutationAxis.CLARITY, state.dominantAxis)
    }

    @Test
    fun zeroStateIsDeterministic() {
        val input = TransmutationInput(0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        val a = X88TransmutationSchema.transform(input)
        val b = X88TransmutationSchema.transform(input)

        assertEquals(a, b)
    }
}

package de.snowworks.ariana.character

import de.snowworks.ariana.neuro.ResonanceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class X88CreationResonanceAdapterTest {
    @Test fun neutralStateProducesDeterministicFrame() {
        val frame = X88CreationResonanceAdapter.frame(ResonanceState(0.0, 0.0, 0.0, 0.0))
        assertEquals(0.0f, frame.presence, 0.000001f)
        assertEquals(0.0f, frame.correctionEmphasis, 0.000001f)
        assertEquals(0.0f, frame.stability, 0.000001f)
        assertEquals("neutral", frame.marker)
    }

    @Test fun promptAugmentationPreservesOriginalPromptAndUsesBoundedDirections() {
        val prompt = "Create one coherent ARIANA X-88 character in a dark studio."
        val result = X88CreationResonanceAdapter.augmentPrompt(
            prompt, ResonanceState(0.8, 0.6, -0.9, 0.9, "growth")
        )
        assertTrue(result.startsWith(prompt))
        assertTrue(result.contains("X88 RESONANCE DIRECTION"))
        assertTrue(result.contains("Symbolic marker: growth"))
        assertTrue(result.contains("Presence intensity: 0.767"))
        assertTrue(result.contains("Stability emphasis: 0.900"))
        assertTrue(result.contains("Correction emphasis: visible refinement"))
    }

    @Test fun lowCorrectionDoesNotAddCorrectionDirective() {
        val result = X88CreationResonanceAdapter.augmentPrompt(
            "base prompt", ResonanceState(0.2, 0.2, 0.4, 0.5, "neutral")
        )
        assertFalse(result.contains("Correction emphasis:"))
    }
}

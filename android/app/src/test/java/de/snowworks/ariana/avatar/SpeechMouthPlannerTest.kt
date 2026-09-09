package de.snowworks.ariana.avatar

import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechMouthPlannerTest {

    @Test
    fun `a produces an open neutral mouth`() {
        val shape = SpeechMouthPlanner.fromText("Ariana")
        assertTrue(shape.open > 0.7f)
        assertTrue(shape.form in -0.2f..0.2f)
    }

    @Test
    fun `i produces a wide mouth`() {
        val shape = SpeechMouthPlanner.fromText("sie")
        assertTrue(shape.form > 0.5f)
    }

    @Test
    fun `u produces a rounded mouth`() {
        val shape = SpeechMouthPlanner.fromText("du")
        assertTrue(shape.form < -0.5f)
    }

    @Test
    fun `consonant-only fragment falls back safely`() {
        val shape = SpeechMouthPlanner.fromText("hmm")
        assertTrue(shape.open in 0f..1f)
        assertTrue(shape.form in -1f..1f)
    }
}

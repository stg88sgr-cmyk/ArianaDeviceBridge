package de.snowworks.ariana.x88xxy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class X88XXYEngineTest {

    @Test
    fun lifecycle_reaches_verification_without_external_dependencies() {
        val engine = X88XXYEngine()

        repeat(6) { engine.advance("step-$it") }

        assertEquals(X88XXYStage.VERIFICATION, engine.state.stage)
        assertEquals(6L, engine.state.revision)
        assertEquals(false, engine.state.verified)
    }

    @Test
    fun integration_requires_explicit_verification() {
        val engine = X88XXYEngine()

        repeat(6) { engine.advance("step-$it") }

        assertThrows(IllegalArgumentException::class.java) {
            engine.advance("integration-without-verification")
        }
    }

    @Test
    fun verified_lifecycle_continues_to_evolution() {
        val engine = X88XXYEngine()

        repeat(6) { engine.advance("step-$it") }
        engine.markVerified()
        engine.advance("integration")
        engine.advance("evolution")

        assertEquals(X88XXYStage.EVOLUTION, engine.state.stage)
        assertEquals(true, engine.state.verified)
        assertEquals(9L, engine.state.revision)
    }

    @Test
    fun reset_returns_to_identity_and_keeps_monotonic_revision() {
        val engine = X88XXYEngine()
        engine.advance("connection")
        val revisionBeforeReset = engine.state.revision

        engine.reset()

        assertEquals(X88XXYStage.IDENTITY, engine.state.stage)
        assertEquals(revisionBeforeReset + 1L, engine.state.revision)
        assertEquals("reset", engine.state.lastEvent)
    }
}

package de.snowworks.ariana.neuro

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class X88EvidenceEngineTest {

    @Test
    fun evidenceIsBoundedAndDeterministic() {
        val engine = X88EvidenceEngine(capacity = 2)

        val first = engine.record(
            kind = EvidenceKind.HEALTH,
            source = "test",
            claim = "health",
            observed = "green",
            timestamp = 100L,
        )
        val same = engine.record(
            kind = EvidenceKind.HEALTH,
            source = "test",
            claim = "health",
            observed = "green",
            timestamp = 100L,
        )
        engine.record(
            kind = EvidenceKind.SIGNAL,
            source = "test",
            claim = "third",
            observed = "ok",
            timestamp = 101L,
        )

        assertEquals(first.id, same.id)
        assertEquals(2, engine.count())
        assertEquals("third", engine.recent().last().claim)
    }

    @Test
    fun v37BootProducesHealthEvidence() {
        val runtime = V37EvidenceRuntime.createForTest()
        runtime.boot()

        val snapshot = runtime.snapshot()
        assertTrue(snapshot.green)
        assertTrue(snapshot.evidenceCount >= 1)
    }

    @Test
    fun signalIsCapturedWithoutCreatingOutput() = runTest {
        val engine = X88EvidenceEngine()
        val outputs = engine.onSignal(
            NeuroSignal(
                channel = NeuroChannel.INTEGRITY,
                source = "test",
                payload = mapOf("claim" to "integrity", "observed" to "green"),
            ),
        )

        assertTrue(outputs.isEmpty())
        assertEquals(1, engine.count())
        assertEquals("integrity", engine.recent(1).single().claim)
    }
}

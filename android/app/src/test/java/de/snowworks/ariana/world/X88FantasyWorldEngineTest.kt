package de.snowworks.ariana.world

import de.snowworks.ariana.neuro.NeuroChannel
import de.snowworks.ariana.neuro.NeuroSignal
import de.snowworks.ariana.neuro.V30NeuroRuntime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class X88FantasyWorldEngineTest {

    @Test
    fun worldStateIsExplicitlyFictionalAndNonPhysical() = runTest {
        val engine = X88FantasyWorldEngine()
        val outputs = engine.onSignal(
            NeuroSignal(
                channel = NeuroChannel.EMOTION_STATE,
                source = "test",
                payload = mapOf(
                    "valence" to "0.8",
                    "arousal" to "0.8",
                    "curiosity" to "0.3",
                    "trust" to "0.9",
                    "tension" to "0.1",
                ),
            ),
        )

        val context = outputs.first { it.channel == NeuroChannel.CONTEXT }
        assertEquals("fictional_symbolic", context.payload["world.kind"])
        assertEquals("true", context.payload["world.fictional"])
        assertEquals("false", context.payload["world.physicalEffect"])
        assertEquals("golden_sanctum", context.payload["world.realm"])
        assertFalse(context.payload.containsKey("actionId"))
    }

    @Test
    fun highCuriosityMovesWorldToCyanGarden() = runTest {
        val engine = X88FantasyWorldEngine()
        engine.onSignal(
            NeuroSignal(
                channel = NeuroChannel.EMOTION_STATE,
                source = "test",
                payload = mapOf(
                    "curiosity" to "0.95",
                    "tension" to "0.1",
                ),
            ),
        )

        val state = engine.currentState()
        assertEquals(X88FantasyWorldEngine.Realm.CYAN_GARDEN, state.realm)
        assertEquals(X88FantasyWorldEngine.SacredPattern.FLOWER_OF_LIFE, state.pattern)
        assertTrue(state.resonanceIndex in 0.0..1.0)
        assertTrue(state.coherence in 0.0..1.0)
    }

    @Test
    fun bootstrapAttachesWorldToV30WithoutReplacingCoreStages() {
        val runtime = V30NeuroRuntime.createForTest()
        runtime.boot()

        val engine = X88FantasyWorldBootstrap.attach(runtime)
        val report = runtime.health()

        assertTrue(engine.id in runtime.fabric.moduleIds())
        assertEquals((16..30).toList(), report.stages.map { it.version })
        assertTrue(report.green)
    }
}

package de.snowworks.ariana.neuro

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V16ToV20NeuroRuntimeTest {

    @Test
    fun v16FabricAutoWiresDeclaredChannelsAndStopsCycles() = runTest {
        val fabric = V16NeuralFabric(maxHops = 8)
        val received = mutableListOf<NeuroSignal>()

        val producer = object : NeuroModule {
            override val id = "producer"
            override val inputs = setOf(NeuroChannel.USER_INTENT)
            override val outputs = setOf(NeuroChannel.EMOTION_STATE)

            override suspend fun onSignal(signal: NeuroSignal): List<NeuroSignal> = listOf(
                NeuroSignal(
                    channel = NeuroChannel.EMOTION_STATE,
                    source = id,
                    payload = mapOf("value" to "connected"),
                ),
            )
        }

        val consumer = object : NeuroModule {
            override val id = "consumer"
            override val inputs = setOf(NeuroChannel.EMOTION_STATE)
            override val outputs = setOf(NeuroChannel.EMOTION_STATE)

            override suspend fun onSignal(signal: NeuroSignal): List<NeuroSignal> {
                received += signal
                return listOf(
                    NeuroSignal(
                        channel = NeuroChannel.EMOTION_STATE,
                        source = id,
                        payload = signal.payload,
                    ),
                )
            }
        }

        fabric.register(producer)
        fabric.register(consumer)
        fabric.emit(
            NeuroSignal(
                channel = NeuroChannel.USER_INTENT,
                source = "test",
                payload = mapOf("text" to "hello"),
            ),
        )

        assertEquals(1, received.size)
        assertEquals("producer", received.single().source)
        assertEquals("connected", received.single().payload["value"])
        assertEquals(setOf("producer"), fabric.topology()[NeuroChannel.USER_INTENT])
        assertEquals(setOf("consumer"), fabric.topology()[NeuroChannel.EMOTION_STATE])
    }

    @Test
    fun v17EmotionEngineRegulatesInputsIntoBoundedState() = runTest {
        val engine = V17EmotionEngine()

        repeat(20) {
            engine.onSignal(
                NeuroSignal(
                    channel = NeuroChannel.USER_INTENT,
                    source = "test",
                    payload = mapOf(
                        "emotionalWeight" to "1.0",
                        "intensity" to "1.0",
                        "novelty" to "1.0",
                    ),
                ),
            )
        }

        val state = engine.state
        assertTrue(state.valence in -1.0..1.0)
        assertTrue(state.arousal in 0.0..1.0)
        assertTrue(state.curiosity in 0.0..1.0)
        assertTrue(state.valence > 0.5)
        assertTrue(state.arousal > 0.5)
    }

    @Test
    fun v18MemoryKeepsRecentMeaningfulSignalsWithoutUnboundedGrowth() = runTest {
        val memory = V18ResonanceMemory(capacity = 3)

        repeat(5) { index ->
            memory.onSignal(
                NeuroSignal(
                    channel = NeuroChannel.EMOTION_STATE,
                    source = "emotion",
                    payload = mapOf("index" to index.toString()),
                ),
            )
        }

        assertEquals(3, memory.recent().size)
        assertEquals("2", memory.recent().first().payload["index"])
        assertEquals("4", memory.recent().last().payload["index"])
    }

    @Test
    fun v19FeedbackRouterTurnsEmotionStateIntoExpressionSignal() = runTest {
        val router = V19FeedbackRouter()
        val outputs = router.onSignal(
            NeuroSignal(
                channel = NeuroChannel.EMOTION_STATE,
                source = "emotion",
                payload = mapOf(
                    "valence" to "0.7",
                    "arousal" to "0.8",
                    "curiosity" to "0.6",
                ),
            ),
        )

        assertEquals(1, outputs.size)
        assertEquals(NeuroChannel.EXPRESSION, outputs.single().channel)
        assertEquals("engaged", outputs.single().payload["mode"])
    }

    @Test
    fun v20RuntimeBootsAllStagesAndHealthGateIsGreen() = runTest {
        val runtime = V20NeuroRuntime.createForTest()
        runtime.boot()

        val report = runtime.health()
        assertTrue(report.green)
        assertEquals(20, report.version)
        assertEquals(
            setOf(16, 17, 18, 19, 20),
            report.stages.map { it.version }.toSet(),
        )
        assertTrue(report.stages.all { it.healthy })
        assertFalse(report.modules.isEmpty())
    }
}

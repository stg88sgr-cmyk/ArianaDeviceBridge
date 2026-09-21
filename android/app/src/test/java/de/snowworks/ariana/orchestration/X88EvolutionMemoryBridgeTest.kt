package de.snowworks.ariana.orchestration

import de.snowworks.ariana.memory.X88MemoryItem
import de.snowworks.ariana.neuro.InneresWerdenFeatures
import de.snowworks.ariana.neuro.InneresWerdenModel
import de.snowworks.ariana.neuro.InneresWerdenPrediction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class X88EvolutionMemoryBridgeTest {
    @Test
    fun memoryItemCanBeEvaluatedWithoutInferringFeaturesFromContent() {
        val memory = X88MemoryItem(
            id = "m1",
            content = "important X88 memory",
            source = X88MemoryItem.Source.ARIANA,
            capturedAtEpochMs = 1L,
            contentHash = "hash",
        )
        val features = InneresWerdenFeatures(
            experience = 0.4,
            valueAlignment = 0.8,
            principleConsistency = 0.7,
            evidenceQuality = 0.9,
            correctionSignal = 0.1,
        )
        val bridge = X88EvolutionMemoryBridge(InneresWerdenModel())

        val result = bridge.evaluate(memory, features)

        assertNotNull(result)
        assertEquals("m1", result.memoryId)
        assertEquals(features, result.features)
    }

    @Test
    fun bridgeReturnsModelPredictionAsEvolutionResult() {
        val memory = X88MemoryItem(
            id = "m2",
            content = "x",
            source = X88MemoryItem.Source.LOCAL,
            capturedAtEpochMs = 2L,
            contentHash = "hash2",
        )
        val model = InneresWerdenModel()
        val features = InneresWerdenFeatures(0.1, 0.2, 0.3, 0.4, 0.5)
        val bridge = X88EvolutionMemoryBridge(model)

        val result = bridge.evaluate(memory, features)

        assertEquals(model.predict(features), result.prediction)
    }
}

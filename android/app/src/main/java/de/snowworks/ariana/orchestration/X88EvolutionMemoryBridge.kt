package de.snowworks.ariana.orchestration

import de.snowworks.ariana.memory.X88MemoryItem
import de.snowworks.ariana.neuro.InneresWerdenFeatures
import de.snowworks.ariana.neuro.InneresWerdenModel
import de.snowworks.ariana.neuro.InneresWerdenPrediction

/**
 * Provider-neutral bridge from an explicit memory item into the local
 * Inneres-Werden model.
 *
 * Feature values are supplied explicitly. Memory content is never interpreted
 * as a hidden training signal.
 */
data class X88EvolutionMemoryResult(
    val memoryId: String,
    val features: InneresWerdenFeatures,
    val prediction: InneresWerdenPrediction,
)

class X88EvolutionMemoryBridge(
    private val model: InneresWerdenModel,
) {
    fun evaluate(
        memory: X88MemoryItem,
        features: InneresWerdenFeatures,
    ): X88EvolutionMemoryResult =
        X88EvolutionMemoryResult(
            memoryId = memory.id,
            features = features,
            prediction = model.predict(features),
        )
}

package de.snowworks.ariana.neuro

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.tanh

data class InneresWerdenFeatures(
    val experience: Double,
    val valueAlignment: Double,
    val principleConsistency: Double,
    val evidenceQuality: Double,
    val correctionSignal: Double,
) {
    fun asVector(): DoubleArray {
        val b = InneresWerdenFeatures(
            experience.coerceIn(-1.0, 1.0),
            valueAlignment.coerceIn(0.0, 1.0),
            principleConsistency.coerceIn(0.0, 1.0),
            evidenceQuality.coerceIn(0.0, 1.0),
            correctionSignal.coerceIn(-1.0, 1.0),
        )
        return doubleArrayOf(
            b.experience,
            b.valueAlignment,
            b.principleConsistency,
            b.evidenceQuality,
            b.correctionSignal,
        )
    }
}

data class InneresWerdenExample(
    val id: String,
    val features: InneresWerdenFeatures,
    val targetCoherence: Double,
)

data class InneresWerdenPrediction(
    val coherence: Double,
    val growthSignal: Double,
    val confidence: Double,
)

data class InneresWerdenTrainingReport(
    val examples: Int,
    val averageLoss: Double,
    val previousAverageLoss: Double?,
    val improved: Boolean,
    val steps: Long,
)

data class InneresWerdenModelSnapshot(
    val version: Int,
    val weights: List<Double>,
    val bias: Double,
    val learningRate: Double,
    val l2: Double,
    val steps: Long,
) {
    init { require(weights.size == FEATURE_COUNT) }

    companion object {
        const val FEATURE_COUNT = 5
    }
}

/**
 * Actual trainable numeric model for:
 * experience -> values -> principles -> coherence -> growth.
 *
 * This is parameter training with gradient descent, not LLM fine-tuning.
 */
class InneresWerdenModel(
    initial: InneresWerdenModelSnapshot = defaultSnapshot(),
) {
    private val weights = initial.weights.toMutableList()
    private var bias = initial.bias
    private val learningRate = initial.learningRate.coerceIn(0.0001, 1.0)
    private val l2 = initial.l2.coerceAtLeast(0.0)

    var steps: Long = initial.steps
        private set

    fun predict(features: InneresWerdenFeatures): InneresWerdenPrediction {
        val x = features.asVector()
        val raw = bias + weights.indices.sumOf { weights[it] * x[it] }
        val coherence = tanh(raw).coerceIn(-1.0, 1.0)
        val confidence = (1.0 - exp(-abs(raw))).coerceIn(0.0, 1.0)
        val growth = (coherence * 0.70 + x[4] * 0.30).coerceIn(-1.0, 1.0)
        return InneresWerdenPrediction(coherence, growth, confidence)
    }

    @Synchronized
    fun train(example: InneresWerdenExample): Double {
        val x = example.features.asVector()
        val target = example.targetCoherence.coerceIn(-1.0, 1.0)
        val raw = bias + weights.indices.sumOf { weights[it] * x[it] }
        val prediction = tanh(raw)
        val error = prediction - target
        val loss = error * error
        val gradient = 2.0 * error * (1.0 - prediction * prediction)

        weights.indices.forEach { index ->
            weights[index] -= learningRate * (gradient * x[index] + l2 * weights[index])
        }
        bias -= learningRate * gradient
        steps += 1
        return loss
    }

    @Synchronized
    fun trainBatch(examples: List<InneresWerdenExample>): InneresWerdenTrainingReport {
        if (examples.isEmpty()) {
            return InneresWerdenTrainingReport(0, 0.0, null, false, steps)
        }

        val before = evaluate(examples)
        examples.forEach { train(it) }
        val after = evaluate(examples)

        return InneresWerdenTrainingReport(
            examples = examples.size,
            averageLoss = after,
            previousAverageLoss = before,
            improved = after < before,
            steps = steps,
        )
    }

    fun evaluate(examples: List<InneresWerdenExample>): Double {
        if (examples.isEmpty()) return 0.0
        return examples.sumOf {
            val error = predict(it.features).coherence -
                it.targetCoherence.coerceIn(-1.0, 1.0)
            error * error
        } / examples.size
    }

    fun snapshot(): InneresWerdenModelSnapshot = synchronized(this) {
        InneresWerdenModelSnapshot(
            version = MODEL_VERSION,
            weights = weights.toList(),
            bias = bias,
            learningRate = learningRate,
            l2 = l2,
            steps = steps,
        )
    }

    companion object {
        const val MODEL_VERSION = 1
        const val FEATURE_COUNT = InneresWerdenModelSnapshot.FEATURE_COUNT

        fun defaultSnapshot() = InneresWerdenModelSnapshot(
            version = MODEL_VERSION,
            weights = listOf(0.10, 0.25, 0.30, 0.20, 0.15),
            bias = 0.0,
            learningRate = 0.05,
            l2 = 0.001,
            steps = 0,
        )
    }
}

class InneresWerdenTrainer(
    private val model: InneresWerdenModel = InneresWerdenModel(),
) : NeuroModule {
    override val id = "inneres-werden-trainer"
    override val inputs = setOf(
        NeuroChannel.USER_INTENT,
        NeuroChannel.PERCEPTION,
        NeuroChannel.MEMORY,
        NeuroChannel.ACTION_RESULT,
        NeuroChannel.INTEGRITY,
    )
    override val outputs = setOf(NeuroChannel.MEMORY)

    override suspend fun onSignal(signal: NeuroSignal): List<NeuroSignal> {
        val trainingEnabled = signal.payload["train"]?.toBooleanStrictOrNull() ?: false
        if (!trainingEnabled) return emptyList()

        val target = signal.payload["targetCoherence"]?.toDoubleOrNull()
            ?: return emptyList()

        val example = InneresWerdenExample(
            id = signal.payload["exampleId"].orEmpty(),
            features = InneresWerdenFeatures(
                experience = signal.payload["experience"]?.toDoubleOrNull() ?: 0.0,
                valueAlignment = signal.payload["valueAlignment"]?.toDoubleOrNull() ?: 0.0,
                principleConsistency = signal.payload["principleConsistency"]?.toDoubleOrNull() ?: 0.0,
                evidenceQuality = signal.payload["evidenceQuality"]?.toDoubleOrNull() ?: 0.0,
                correctionSignal = signal.payload["correctionSignal"]?.toDoubleOrNull() ?: 0.0,
            ),
            targetCoherence = target,
        )

        val loss = model.train(example)
        val prediction = model.predict(example.features)

        return listOf(
            NeuroSignal(
                channel = NeuroChannel.MEMORY,
                source = id,
                payload = mapOf(
                    "model" to "inneres-werden-v1",
                    "trainingStep" to model.steps.toString(),
                    "loss" to loss.toString(),
                    "coherence" to prediction.coherence.toString(),
                    "growthSignal" to prediction.growthSignal.toString(),
                    "confidence" to prediction.confidence.toString(),
                ),
            ),
        )
    }

    fun snapshot(): InneresWerdenModelSnapshot = model.snapshot()
}

package de.snowworks.ariana.neuro

/**
 * X88 Transmutation Schema v1 / V33.
 *
 * This is a deterministic symbolic state model. It does not claim that
 * colors, frequencies, geometry, or symbols have scientific causal effects.
 *
 * The model converts a validated Inneres-Werden snapshot into a compact
 * transmutation state that can be consumed by UI, avatar, audit, or routing
 * layers without changing the underlying memory.
 */
data class TransmutationInput(
    val experience: Double,
    val valuesAlignment: Double,
    val principlesConsistency: Double,
    val evidenceQuality: Double,
    val coherence: Double,
    val growth: Double
) {
    fun normalized(): TransmutationInput = copy(
        experience = experience.coerceIn(0.0, 1.0),
        valuesAlignment = valuesAlignment.coerceIn(0.0, 1.0),
        principlesConsistency = principlesConsistency.coerceIn(0.0, 1.0),
        evidenceQuality = evidenceQuality.coerceIn(0.0, 1.0),
        coherence = coherence.coerceIn(0.0, 1.0),
        growth = growth.coerceIn(0.0, 1.0)
    )
}

enum class TransmutationAxis {
    CLARITY,
    CREATIVITY,
    UNITY,
    PROTECTION,
    REFLECTION,
    GROWTH
}

data class TransmutationState(
    val version: String = "V33",
    val clarity: Double,
    val creativity: Double,
    val unity: Double,
    val protection: Double,
    val reflection: Double,
    val growth: Double,
    val dominantAxis: TransmutationAxis
) {
    val stability: Double
        get() = ((clarity + unity + protection + reflection) / 4.0).coerceIn(0.0, 1.0)
}

object X88TransmutationSchema {

    fun transform(input: TransmutationInput): TransmutationState {
        val i = input.normalized()

        val clarity = (i.evidenceQuality * 0.55 + i.principlesConsistency * 0.25 + i.coherence * 0.20)
        val creativity = (i.growth * 0.45 + i.experience * 0.30 + (1.0 - i.principlesConsistency) * 0.10 + i.valuesAlignment * 0.15)
        val unity = (i.coherence * 0.45 + i.valuesAlignment * 0.30 + i.principlesConsistency * 0.25)
        val protection = (i.principlesConsistency * 0.45 + i.valuesAlignment * 0.35 + i.evidenceQuality * 0.20)
        val reflection = (i.experience * 0.35 + i.evidenceQuality * 0.25 + i.coherence * 0.25 + i.growth * 0.15)
        val growth = (i.growth * 0.55 + i.experience * 0.20 + i.coherence * 0.15 + i.valuesAlignment * 0.10)

        val axes = linkedMapOf(
            TransmutationAxis.CLARITY to clarity,
            TransmutationAxis.CREATIVITY to creativity,
            TransmutationAxis.UNITY to unity,
            TransmutationAxis.PROTECTION to protection,
            TransmutationAxis.REFLECTION to reflection,
            TransmutationAxis.GROWTH to growth
        )

        return TransmutationState(
            clarity = clarity.coerceIn(0.0, 1.0),
            creativity = creativity.coerceIn(0.0, 1.0),
            unity = unity.coerceIn(0.0, 1.0),
            protection = protection.coerceIn(0.0, 1.0),
            reflection = reflection.coerceIn(0.0, 1.0),
            growth = growth.coerceIn(0.0, 1.0),
            dominantAxis = axes.maxBy { it.value }.key
        )
    }
}

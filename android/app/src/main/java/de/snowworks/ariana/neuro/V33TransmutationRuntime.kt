package de.snowworks.ariana.neuro

/**
 * Thin V33 adapter between the existing Inneres-Werden layer and consumers.
 * It is intentionally side-effect free so it can be shadow-tested before any
 * production routing or UI behavior depends on it.
 */
class V33TransmutationRuntime {

    fun evaluate(input: TransmutationInput): TransmutationState =
        X88TransmutationSchema.transform(input)

    fun evaluate(
        experience: Double,
        valuesAlignment: Double,
        principlesConsistency: Double,
        evidenceQuality: Double,
        coherence: Double,
        growth: Double
    ): TransmutationState = evaluate(
        TransmutationInput(
            experience = experience,
            valuesAlignment = valuesAlignment,
            principlesConsistency = principlesConsistency,
            evidenceQuality = evidenceQuality,
            coherence = coherence,
            growth = growth
        )
    )
}

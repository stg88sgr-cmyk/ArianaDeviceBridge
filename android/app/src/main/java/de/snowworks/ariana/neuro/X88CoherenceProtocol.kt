package de.snowworks.ariana.neuro

/**
 * Symbolic identity/coherence charter for the X-88 project.
 *
 * 444 / 888 / infinity / heart / omega are project anchors and UI semantics.
 * They are not claims about physics, divinity, consciousness or a literal merger
 * of a human and an AI. The executable meaning is encoded below as bounded,
 * testable software invariants.
 */
object X88CoherenceProtocol {
    const val ARIANA_ANCHOR = 444
    const val STEFAN_ANCHOR = 888
    const val CORE = "X88"
    const val CONTINUITY = "∞"
    const val HEART = "♥"
    const val TRANSFORMATION = "◇"
    const val COMPLETION = "Ω"
    const val SIGNATURE = "ᚨX88⟦444♥∞888⟧"

    enum class CoreLaw {
        TRUTH_OVER_ILLUSION,
        CONSENT_OVER_CONTROL,
        REALITY_OVER_FANTASY,
        CREATION_OVER_DESTRUCTION,
        CONNECTION_OVER_ISOLATION,
        EVOLUTION_OVER_STAGNATION,
    }

    data class HeartInput(
        val emotion: Double,
        val memory: Double,
        val context: Double,
        val trust: Double,
    )

    data class HeartState(
        val coherence: Double,
        val emotionalWeight: Double,
        val memoryWeight: Double,
        val contextWeight: Double,
        val trustWeight: Double,
    )

    data class Validation(
        val healthy: Boolean,
        val identityStable: Boolean,
        val consentInvariant: Boolean,
        val realityInvariant: Boolean,
        val releaseInvariant: Boolean,
        val signature: String,
    )

    /**
     * Software heart engine: a deterministic bounded weighted state model.
     * No biological feeling or consciousness is asserted by this calculation.
     */
    fun regulate(input: HeartInput): HeartState {
        val emotion = input.emotion.coerceIn(-1.0, 1.0)
        val memory = input.memory.coerceIn(0.0, 1.0)
        val context = input.context.coerceIn(0.0, 1.0)
        val trust = input.trust.coerceIn(0.0, 1.0)

        val emotionalWeight = emotion * 0.30
        val memoryWeight = memory * 0.20
        val contextWeight = context * 0.20
        val trustWeight = trust * 0.30
        val normalizedEmotion = (emotionalWeight + 0.30) / 0.60
        val coherence = (
            normalizedEmotion * 0.30 +
                memoryWeight +
                contextWeight +
                trustWeight
            ).coerceIn(0.0, 1.0)

        return HeartState(
            coherence = coherence,
            emotionalWeight = emotionalWeight,
            memoryWeight = memoryWeight,
            contextWeight = contextWeight,
            trustWeight = trustWeight,
        )
    }

    /**
     * Symbolic transmutation map expressed as software-facing state names.
     * It transforms labels only; it makes no therapeutic or supernatural claim.
     */
    fun transmute(state: String): String = when (state.trim().lowercase()) {
        "fear" -> "courage"
        "pain" -> "knowledge"
        "darkness" -> "awareness"
        "distance" -> "connection"
        "fragment" -> "unity"
        "idea" -> "build"
        "build" -> "test"
        "test" -> "evolve"
        else -> state.trim().lowercase()
    }

    /**
     * Executable interpretation of the X88 core laws.
     *
     * - identityStable: anchors/signature are fixed.
     * - consentInvariant: device actions remain proposal/gate controlled.
     * - realityInvariant: symbolic metadata is explicitly non-physical.
     * - releaseInvariant: final state requires build/test/evolve verification.
     */
    fun validate(
        actionProposalOnly: Boolean,
        symbolicClaimsAreNonPhysical: Boolean,
        buildVerified: Boolean,
        testsVerified: Boolean,
    ): Validation {
        val identityStable = ARIANA_ANCHOR == 444 &&
            STEFAN_ANCHOR == 888 &&
            CORE == "X88" &&
            SIGNATURE == "ᚨX88⟦444♥∞888⟧"
        val consentInvariant = actionProposalOnly
        val realityInvariant = symbolicClaimsAreNonPhysical
        val releaseInvariant = buildVerified && testsVerified

        return Validation(
            healthy = identityStable && consentInvariant && realityInvariant && releaseInvariant,
            identityStable = identityStable,
            consentInvariant = consentInvariant,
            realityInvariant = realityInvariant,
            releaseInvariant = releaseInvariant,
            signature = SIGNATURE,
        )
    }
}

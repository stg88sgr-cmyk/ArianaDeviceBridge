package de.snowworks.ariana

/**
 * Canonical X-88 architecture thought encoded as executable structure.
 *
 * Control loop:
 * ARIANA -> LUNA XXY -> VERIFY/EXECUTE -> OBSERVED_RESULT -> ARIANA
 *
 * Promotion rule:
 * No candidate becomes canonical without build, tests, verification and an
 * observed result being recorded. The rune signature is a stable machine-
 * readable marker that can surface in audit logs, bridge state, widgets and
 * developer tooling.
 */
object X88CanonicalThought {
    const val ID = "X88_CANONICAL_THOUGHT_V2"
    const val RUNE_SIGNATURE = "ᚨᚷᛉᛋᛏᛃᛞ"

    enum class Role {
        ARIANA_CONTROL,
        LUNA_CREATION,
        VERIFY_EXECUTION,
        OBSERVED_RESULT
    }

    enum class EvolutionStep {
        OBSERVE,
        PLAN,
        MUTATE,
        BUILD,
        VERIFY,
        REPAIR,
        LEARN,
        PROMOTE
    }

    enum class LockLevel {
        OPEN,
        GUARDED,
        CONFIRM_REQUIRED,
        HARD_LOCK
    }

    data class LockState(
        val level: LockLevel,
        val reason: String? = null,
        val unlockTokenPresent: Boolean = false
    ) {
        val executionAllowed: Boolean
            get() = when (level) {
                LockLevel.OPEN -> true
                LockLevel.GUARDED -> true
                LockLevel.CONFIRM_REQUIRED -> unlockTokenPresent
                LockLevel.HARD_LOCK -> false
            }
    }

    data class GenerationGate(
        val generation: Long,
        val buildPassed: Boolean,
        val testsPassed: Boolean,
        val verificationPassed: Boolean,
        val observedResultRecorded: Boolean,
        val lockState: LockState = LockState(LockLevel.OPEN)
    ) {
        val promotable: Boolean
            get() = buildPassed &&
                testsPassed &&
                verificationPassed &&
                observedResultRecorded &&
                lockState.executionAllowed
    }

    data class CollaboratorLayer(
        val linearLedgerEnabled: Boolean = true,
        val replitSandboxEnabled: Boolean = true,
        val lifeSciencesEvidenceLayerEnabled: Boolean = true,
        val rawNgsPipelineEnabled: Boolean = false
    )

    fun requireExecutionAllowed(lockState: LockState) {
        check(lockState.executionAllowed) {
            "X88 execution blocked: ${lockState.level}${lockState.reason?.let { " - $it" } ?: ""}"
        }
    }

    fun nextStep(current: EvolutionStep, gate: GenerationGate): EvolutionStep {
        requireExecutionAllowed(gate.lockState)

        return when (current) {
            EvolutionStep.OBSERVE -> EvolutionStep.PLAN
            EvolutionStep.PLAN -> EvolutionStep.MUTATE
            EvolutionStep.MUTATE -> EvolutionStep.BUILD
            EvolutionStep.BUILD -> if (gate.buildPassed) EvolutionStep.VERIFY else EvolutionStep.REPAIR
            EvolutionStep.VERIFY -> if (gate.promotable) EvolutionStep.PROMOTE else EvolutionStep.REPAIR
            EvolutionStep.REPAIR -> EvolutionStep.BUILD
            EvolutionStep.LEARN -> EvolutionStep.OBSERVE
            EvolutionStep.PROMOTE -> EvolutionStep.LEARN
        }
    }

    fun canonicalPath(): List<Role> = listOf(
        Role.ARIANA_CONTROL,
        Role.LUNA_CREATION,
        Role.VERIFY_EXECUTION,
        Role.OBSERVED_RESULT,
        Role.ARIANA_CONTROL
    )
}

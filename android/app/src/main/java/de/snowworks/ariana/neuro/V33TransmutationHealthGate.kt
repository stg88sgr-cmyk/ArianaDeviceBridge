package de.snowworks.ariana.neuro

data class V33TransmutationHealthGate(
    val allowed: Boolean,
    val reasons: List<String>,
)

object V33TransmutationHealthGate {

    fun check(snapshot: V32InneresWerdenSnapshot): V33TransmutationHealthGate {
        val reasons = buildList {
            if (snapshot.stage != 32) add("V32_STAGE_MISMATCH")
            if (snapshot.runtimeVersion != 32) add("V32_RUNTIME_VERSION_MISMATCH")
            if (!snapshot.green) add("V32_NOT_GREEN")
            if (snapshot.trainingSteps < 0L) add("TRAINING_STEPS_NEGATIVE")
            if (snapshot.modelVersion < 0) add("MODEL_VERSION_NEGATIVE")
            if (snapshot.coherenceSignature.isBlank()) add("COHERENCE_SIGNATURE_MISSING")

            val resonanceValues = listOf(
                snapshot.resonance.coherence,
                snapshot.resonance.growth,
                snapshot.resonance.correction,
                snapshot.resonance.stability,
            )
            if (resonanceValues.any { !it.isFinite() }) {
                add("RESONANCE_NON_FINITE")
            }
        }

        return V33TransmutationHealthGate(
            allowed = reasons.isEmpty(),
            reasons = reasons,
        )
    }
}

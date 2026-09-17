package de.snowworks.ariana.joyfun

/**
 * Turns the symbolic X88 union vocabulary into deterministic runtime decisions.
 *
 * The symbolic words remain labels. Only explicit software inputs, evidence and
 * consent influence execution decisions.
 */
enum class X88ProcessingStatus {
    READY,
    WAITING_EVIDENCE,
    BLOCKED_BY_CONSENT,
    BLOCKED_BY_CORE_STATE,
    UNKNOWN_SIGNAL,
}

enum class X88TechnicalStage {
    IDEA,
    BUILD,
    TEST,
    EVOLVE,
}

data class X88Evidence(
    val buildPassed: Boolean = false,
    val testsPassed: Boolean = false,
    val regressionPassed: Boolean = false,
    val commitSha: String? = null,
    val workflowRunId: Long? = null,
) {
    val completeForEvolution: Boolean
        get() = buildPassed && testsPassed && regressionPassed &&
            !commitSha.isNullOrBlank() && workflowRunId != null
}

data class X88CodeInput(
    val signal: String,
    val intensity: Float = 1f,
    val consent: Boolean,
    val memoryConnected: Boolean,
    val creationRequested: Boolean,
    val evidence: X88Evidence = X88Evidence(),
)

data class X88CodeResult(
    val sourceSignal: String,
    val normalizedSignal: String,
    val mappedSignal: String?,
    val intensity: Float,
    val status: X88ProcessingStatus,
    val technicalStage: X88TechnicalStage?,
    val nextTechnicalStage: X88TechnicalStage?,
    val unionState: X88UnionState,
    val executable: Boolean,
    val reason: String,
)

class X88CodeProcessor(
    private val unionCore: X88UnionCore = X88UnionCore(),
) {
    private val transmutationMap: Map<String, String> =
        X88UnionCore.TRANSMUTATIONS.associate { it.from to it.to }

    fun process(
        evolution: JoyFunEvolutionState,
        input: X88CodeInput,
    ): X88CodeResult {
        val normalized = normalize(input.signal)
        val union = unionCore.unite(
            evolution = evolution,
            memoryConnected = input.memoryConnected,
            creationEnabled = input.creationRequested,
        )

        val mapped = transmutationMap[normalized]
        val stage = normalized.toTechnicalStage()
        val nextStage = mapped?.toTechnicalStage()

        if (mapped == null) {
            return result(
                input = input,
                normalized = normalized,
                mapped = null,
                status = X88ProcessingStatus.UNKNOWN_SIGNAL,
                stage = stage,
                nextStage = null,
                union = union,
                executable = false,
                reason = "Signal is not part of the canonical X88 transmutation table.",
            )
        }

        if (!input.consent) {
            return result(
                input = input,
                normalized = normalized,
                mapped = mapped,
                status = X88ProcessingStatus.BLOCKED_BY_CONSENT,
                stage = stage,
                nextStage = nextStage,
                union = union,
                executable = false,
                reason = "CONSENT > CONTROL: execution is denied without explicit consent.",
            )
        }

        if (!union.coreStable) {
            return result(
                input = input,
                normalized = normalized,
                mapped = mapped,
                status = X88ProcessingStatus.BLOCKED_BY_CORE_STATE,
                stage = stage,
                nextStage = nextStage,
                union = union,
                executable = false,
                reason = "V30 integration contract is not stable yet.",
            )
        }

        if (normalized == "TEST" && !input.evidence.completeForEvolution) {
            return result(
                input = input,
                normalized = normalized,
                mapped = mapped,
                status = X88ProcessingStatus.WAITING_EVIDENCE,
                stage = stage,
                nextStage = nextStage,
                union = union,
                executable = false,
                reason = "TEST -> EVOLVE requires build, test, regression and workflow evidence.",
            )
        }

        return result(
            input = input,
            normalized = normalized,
            mapped = mapped,
            status = X88ProcessingStatus.READY,
            stage = stage,
            nextStage = nextStage,
            union = union,
            executable = stage != null,
            reason = if (stage != null) {
                "Canonical technical transition accepted: $normalized -> $mapped"
            } else {
                "Symbolic transmutation resolved as metadata: $normalized -> $mapped"
            },
        )
    }

    private fun result(
        input: X88CodeInput,
        normalized: String,
        mapped: String?,
        status: X88ProcessingStatus,
        stage: X88TechnicalStage?,
        nextStage: X88TechnicalStage?,
        union: X88UnionState,
        executable: Boolean,
        reason: String,
    ): X88CodeResult = X88CodeResult(
        sourceSignal = input.signal,
        normalizedSignal = normalized,
        mappedSignal = mapped,
        intensity = input.intensity.coerceIn(0f, 1f),
        status = status,
        technicalStage = stage,
        nextTechnicalStage = nextStage,
        unionState = union,
        executable = executable,
        reason = reason,
    )

    private fun normalize(value: String): String =
        value.trim().uppercase()

    private fun String.toTechnicalStage(): X88TechnicalStage? = when (this) {
        "IDEA" -> X88TechnicalStage.IDEA
        "BUILD" -> X88TechnicalStage.BUILD
        "TEST" -> X88TechnicalStage.TEST
        "EVOLVE" -> X88TechnicalStage.EVOLVE
        else -> null
    }
}

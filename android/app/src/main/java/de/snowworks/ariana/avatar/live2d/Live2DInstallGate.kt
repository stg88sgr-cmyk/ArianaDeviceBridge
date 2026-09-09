package de.snowworks.ariana.avatar.live2d

/**
 * Final preflight before a Live2D Ariana model may replace the Motion Probe.
 *
 * A candidate must satisfy both the exported-file bundle and the runtime
 * parameter/expression contract. This keeps malformed or stale art exports out
 * of the active renderer and makes the eventual hot-swap deterministic.
 */
object Live2DInstallGate {

    data class Candidate(
        val bundle: Live2DModelBundle = Live2DModelBundle.arianaV1(),
        val existingPaths: Collection<String>,
        val parameterIds: Collection<String>,
        val expressionIds: Collection<String>,
    )

    data class Result(
        val bundle: Live2DModelBundle.ValidationResult,
        val contract: Live2DModelContract.ValidationResult,
    ) {
        val ready: Boolean
            get() = bundle.isValid && contract.isValid

        fun compactLabel(): String = if (ready) {
            "live2d-install-ready"
        } else {
            listOf(bundle.compactLabel(), contract.compactLabel())
                .filterNot { it.endsWith("-ok") }
                .joinToString(" · ")
        }
    }

    fun validate(candidate: Candidate): Result = Result(
        bundle = candidate.bundle.validate(candidate.existingPaths),
        contract = Live2DModelContract.validate(
            parameterIds = candidate.parameterIds,
            expressionIds = candidate.expressionIds,
        ),
    )
}

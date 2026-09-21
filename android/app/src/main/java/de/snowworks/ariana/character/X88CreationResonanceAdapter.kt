package de.snowworks.ariana.character

import de.snowworks.ariana.neuro.ResonanceState

/**
 * Bounded creation-plane adapter for the local X88 resonance state.
 *
 * It only adds deterministic, descriptive direction to an existing prompt.
 * It does not select a provider, add permissions, perform network calls,
 * or claim that symbolic resonance values are physical measurements.
 */
data class X88CreationResonanceFrame(
    val presence: Float,
    val correctionEmphasis: Float,
    val stability: Float,
    val marker: String,
)

object X88CreationResonanceAdapter {
    fun frame(state: ResonanceState): X88CreationResonanceFrame =
        X88CreationResonanceFrame(
            presence = ((state.coherence + state.growth + state.stability) / 3.0)
                .coerceIn(0.0, 1.0).toFloat(),
            correctionEmphasis = kotlin.math.abs(state.correction)
                .coerceIn(0.0, 1.0).toFloat(),
            stability = state.stability.coerceIn(0.0, 1.0).toFloat(),
            marker = state.marker,
        )

    fun augmentPrompt(prompt: String, state: ResonanceState): String {
        require(prompt.isNotBlank())
        val frame = frame(state)
        return buildString {
            append(prompt.trim())
            appendLine()
            appendLine()
            appendLine("X88 RESONANCE DIRECTION")
            appendLine("- Symbolic marker: ${frame.marker}")
            appendLine("- Presence intensity: ${frame.presence.format()}")
            appendLine("- Stability emphasis: ${frame.stability.format()}")
            if (frame.correctionEmphasis > 0.65f) {
                appendLine("- Correction emphasis: visible refinement, preserve identity and scene continuity")
            }
        }.trim()
    }

    private fun Float.format(): String = "%.3f".format(java.util.Locale.US, this)
}

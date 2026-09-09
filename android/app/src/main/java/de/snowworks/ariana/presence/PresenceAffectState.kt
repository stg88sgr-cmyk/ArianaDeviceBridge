package de.snowworks.ariana.presence

/**
 * Transparent, bounded affect-style signal state for Ariana Presence.
 *
 * These values are interaction signals for expression, pacing and animation.
 * They are not treated as proof of biological emotion or used to coerce the user.
 * All channels are normalized to 0..1 so the model stays renderer-neutral.
 */
data class PresenceAffectState(
    val warmth: Float = 0.72f,
    val curiosity: Float = 0.62f,
    val focus: Float = 0.68f,
    val playfulness: Float = 0.42f,
    val confidence: Float = 0.60f,
    val tension: Float = 0.12f,
    val socialAttention: Float = 0.55f,
) {
    fun normalized(): PresenceAffectState = copy(
        warmth = warmth.coerceIn(0f, 1f),
        curiosity = curiosity.coerceIn(0f, 1f),
        focus = focus.coerceIn(0f, 1f),
        playfulness = playfulness.coerceIn(0f, 1f),
        confidence = confidence.coerceIn(0f, 1f),
        tension = tension.coerceIn(0f, 1f),
        socialAttention = socialAttention.coerceIn(0f, 1f),
    )

    companion object {
        val BASELINE = PresenceAffectState()
    }
}

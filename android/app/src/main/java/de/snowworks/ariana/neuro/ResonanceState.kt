package de.snowworks.ariana.neuro

/**
 * Local symbolic resonance state for X88.
 *
 * Values are application state markers, not measurements of physical frequencies
 * and not evidence of emotions or consciousness.
 */
data class ResonanceState(
    val coherence: Double,
    val growth: Double,
    val correction: Double,
    val stability: Double,
    val marker: String = "neutral",
) {
    init {
        require(coherence in -1.0..1.0)
        require(growth in -1.0..1.0)
        require(correction in -1.0..1.0)
        require(stability in 0.0..1.0)
        require(marker.isNotBlank())
    }

    fun blendedWith(other: ResonanceState, weight: Double): ResonanceState {
        val w = weight.coerceIn(0.0, 1.0)
        val inv = 1.0 - w
        return ResonanceState(
            coherence = coherence * inv + other.coherence * w,
            growth = growth * inv + other.growth * w,
            correction = correction * inv + other.correction * w,
            stability = stability * inv + other.stability * w,
            marker = if (w >= 0.5) other.marker else marker,
        )
    }

    companion object {
        fun fromInneresWerden(prediction: InneresWerdenPrediction): ResonanceState =
            ResonanceState(
                coherence = prediction.coherence,
                growth = prediction.growthSignal,
                correction = 0.0,
                stability = prediction.confidence,
                marker = markerFor(prediction.coherence, prediction.growthSignal),
            )

        private fun markerFor(coherence: Double, growth: Double): String = when {
            coherence < -0.5 -> "shadow"
            growth > 0.5 -> "growth"
            coherence > 0.5 -> "coherent"
            else -> "neutral"
        }
    }
}

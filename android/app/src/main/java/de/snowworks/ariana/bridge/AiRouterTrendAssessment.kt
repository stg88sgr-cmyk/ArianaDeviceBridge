package de.snowworks.ariana.bridge

/**
 * Interprets recent aggregate router metrics without inspecting conversation content.
 * This is a diagnostic signal, not an automatic provider-disable policy.
 */
object AiRouterTrendAssessment {
    enum class Level { INSUFFICIENT_DATA, STABLE, WATCH, DEGRADED }

    data class Assessment(
        val level: Level,
        val errorDeltaPoints: Double,
        val fallbackDeltaPoints: Double,
        val recentSamples: Long,
        val baselineSamples: Long,
        val reasons: List<String>,
    )

    private const val MIN_RECENT_SAMPLES = 10L
    private const val MIN_BASELINE_SAMPLES = 20L
    private const val WATCH_DELTA_POINTS = 10.0
    private const val DEGRADED_DELTA_POINTS = 20.0

    fun assess(snapshot: AiRouterTrendStore.Snapshot): Assessment =
        assess(snapshot.last24Hours, snapshot.last7Days)

    internal fun assess(
        recent: AiRouterTrendStore.Window,
        baseline: AiRouterTrendStore.Window,
    ): Assessment {
        val errorDelta = recent.errorRatePercent - baseline.errorRatePercent
        val fallbackDelta = recent.fallbackRatePercent - baseline.fallbackRatePercent

        if (recent.total < MIN_RECENT_SAMPLES || baseline.total < MIN_BASELINE_SAMPLES) {
            return Assessment(
                level = Level.INSUFFICIENT_DATA,
                errorDeltaPoints = errorDelta,
                fallbackDeltaPoints = fallbackDelta,
                recentSamples = recent.total,
                baselineSamples = baseline.total,
                reasons = listOf("SAMPLE_SIZE"),
            )
        }

        val reasons = buildList {
            if (errorDelta >= WATCH_DELTA_POINTS) add("ERROR_RATE_UP")
            if (fallbackDelta >= WATCH_DELTA_POINTS) add("FALLBACK_RATE_UP")
        }
        val level = when {
            errorDelta >= DEGRADED_DELTA_POINTS || fallbackDelta >= DEGRADED_DELTA_POINTS -> Level.DEGRADED
            reasons.isNotEmpty() -> Level.WATCH
            else -> Level.STABLE
        }
        return Assessment(
            level = level,
            errorDeltaPoints = errorDelta,
            fallbackDeltaPoints = fallbackDelta,
            recentSamples = recent.total,
            baselineSamples = baseline.total,
            reasons = reasons,
        )
    }
}

package de.snowworks.ariana.bridge

/**
 * Conservative provider-specific quality assessment using aggregate counters only.
 * This produces diagnostics only and never changes routing weights or provider state.
 */
object AiProviderQualityAssessment {
    enum class Level { INSUFFICIENT_DATA, STABLE, WATCH, DEGRADED }

    data class Assessment(
        val engine: AiProviderQualityStore.Engine,
        val level: Level,
        val errorDeltaPoints: Double,
        val circuitRejectDeltaPoints: Double,
        val executionDeltaPoints: Double,
        val fallbackDeltaPoints: Double,
        val recentSelected: Long,
        val baselineSelected: Long,
        val recentExecuted: Long,
        val baselineExecuted: Long,
        val reasons: List<String>,
    )

    private const val MIN_RECENT_SELECTED = 10L
    private const val MIN_BASELINE_SELECTED = 20L
    private const val MIN_RECENT_EXECUTED = 5L
    private const val MIN_BASELINE_EXECUTED = 10L

    private const val WATCH_ERROR_UP_POINTS = 10.0
    private const val DEGRADED_ERROR_UP_POINTS = 20.0
    private const val WATCH_CIRCUIT_UP_POINTS = 10.0
    private const val DEGRADED_CIRCUIT_UP_POINTS = 20.0
    private const val WATCH_EXECUTION_DOWN_POINTS = -15.0
    private const val DEGRADED_EXECUTION_DOWN_POINTS = -30.0

    fun assess(snapshot: AiProviderQualityTrendStore.Snapshot): Assessment =
        assess(snapshot.engine, snapshot.last24Hours, snapshot.last7Days)

    internal fun assess(
        engine: AiProviderQualityStore.Engine,
        recent: AiProviderQualityTrendStore.Window,
        baseline: AiProviderQualityTrendStore.Window,
    ): Assessment {
        val errorDelta = recent.errorRatePercent - baseline.errorRatePercent
        val circuitDelta = ratioPercent(recent.circuitRejected, recent.selected) -
            ratioPercent(baseline.circuitRejected, baseline.selected)
        val executionDelta = recent.executionRatePercent - baseline.executionRatePercent
        val fallbackDelta = recent.fallbackSharePercent - baseline.fallbackSharePercent

        if (
            recent.selected < MIN_RECENT_SELECTED ||
            baseline.selected < MIN_BASELINE_SELECTED ||
            recent.executed < MIN_RECENT_EXECUTED ||
            baseline.executed < MIN_BASELINE_EXECUTED
        ) {
            return Assessment(
                engine = engine,
                level = Level.INSUFFICIENT_DATA,
                errorDeltaPoints = errorDelta,
                circuitRejectDeltaPoints = circuitDelta,
                executionDeltaPoints = executionDelta,
                fallbackDeltaPoints = fallbackDelta,
                recentSelected = recent.selected,
                baselineSelected = baseline.selected,
                recentExecuted = recent.executed,
                baselineExecuted = baseline.executed,
                reasons = listOf("SAMPLE_SIZE"),
            )
        }

        val reasons = buildList {
            if (errorDelta >= WATCH_ERROR_UP_POINTS) add("ERROR_RATE_UP")
            if (circuitDelta >= WATCH_CIRCUIT_UP_POINTS) add("CIRCUIT_REJECT_UP")
            if (executionDelta <= WATCH_EXECUTION_DOWN_POINTS) add("EXECUTION_RATE_DOWN")
        }
        val level = when {
            errorDelta >= DEGRADED_ERROR_UP_POINTS ||
                circuitDelta >= DEGRADED_CIRCUIT_UP_POINTS ||
                executionDelta <= DEGRADED_EXECUTION_DOWN_POINTS -> Level.DEGRADED
            reasons.isNotEmpty() -> Level.WATCH
            else -> Level.STABLE
        }

        return Assessment(
            engine = engine,
            level = level,
            errorDeltaPoints = errorDelta,
            circuitRejectDeltaPoints = circuitDelta,
            executionDeltaPoints = executionDelta,
            fallbackDeltaPoints = fallbackDelta,
            recentSelected = recent.selected,
            baselineSelected = baseline.selected,
            recentExecuted = recent.executed,
            baselineExecuted = baseline.executed,
            reasons = reasons,
        )
    }

    internal fun ratioPercent(part: Long, total: Long): Double =
        if (total > 0L) (part.toDouble() / total.toDouble()) * 100.0 else 0.0
}

package de.snowworks.ariana.bridge

import android.content.Context

/**
 * Provider-specific routing quality counters only.
 * Never stores prompts, replies, provider payloads, models or credentials.
 */
object AiProviderQualityStore {
    enum class Engine { CLAUDE, META }

    data class Snapshot(
        val engine: Engine,
        val selected: Long,
        val executed: Long,
        val successes: Long,
        val errors: Long,
        val circuitRejected: Long,
        val fallbackSelections: Long,
    ) {
        val successRatePercent: Double
            get() = ratioPercent(successes, executed)
        val errorRatePercent: Double
            get() = ratioPercent(errors, executed)
        val fallbackSharePercent: Double
            get() = ratioPercent(fallbackSelections, selected)
        val executionRatePercent: Double
            get() = ratioPercent(executed, selected)
    }

    private const val PREFS = "x88_ai_provider_quality"

    @Synchronized
    fun recordSelection(
        context: Context,
        engine: Engine,
        fallbackAttempt: Boolean,
    ) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val prefix = prefix(engine)
        prefs.edit()
            .putLong("${prefix}_selected", prefs.getLong("${prefix}_selected", 0L) + 1L)
            .putLong(
                "${prefix}_fallback",
                prefs.getLong("${prefix}_fallback", 0L) + if (fallbackAttempt) 1L else 0L,
            )
            .apply()
    }

    @Synchronized
    fun recordCircuitRejected(context: Context, engine: Engine) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = "${prefix(engine)}_circuit_rejected"
        prefs.edit().putLong(key, prefs.getLong(key, 0L) + 1L).apply()
    }

    @Synchronized
    fun recordExecuted(context: Context, engine: Engine, ok: Boolean) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val prefix = prefix(engine)
        prefs.edit()
            .putLong("${prefix}_executed", prefs.getLong("${prefix}_executed", 0L) + 1L)
            .putLong(
                if (ok) "${prefix}_success" else "${prefix}_error",
                prefs.getLong(if (ok) "${prefix}_success" else "${prefix}_error", 0L) + 1L,
            )
            .apply()
    }

    fun snapshot(context: Context, engine: Engine): Snapshot {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val prefix = prefix(engine)
        return Snapshot(
            engine = engine,
            selected = prefs.getLong("${prefix}_selected", 0L),
            executed = prefs.getLong("${prefix}_executed", 0L),
            successes = prefs.getLong("${prefix}_success", 0L),
            errors = prefs.getLong("${prefix}_error", 0L),
            circuitRejected = prefs.getLong("${prefix}_circuit_rejected", 0L),
            fallbackSelections = prefs.getLong("${prefix}_fallback", 0L),
        )
    }

    internal fun ratioPercent(part: Long, total: Long): Double =
        if (total > 0L) (part.toDouble() / total.toDouble()) * 100.0 else 0.0

    private fun prefix(engine: Engine): String = engine.name.lowercase()
}

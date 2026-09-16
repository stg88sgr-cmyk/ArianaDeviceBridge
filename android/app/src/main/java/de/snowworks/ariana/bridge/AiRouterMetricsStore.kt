package de.snowworks.ariana.bridge

import android.content.Context

/** Aggregate router counters only. Never stores prompts, replies or credentials. */
object AiRouterMetricsStore {
    data class Snapshot(
        val total: Long,
        val local: Long,
        val claude: Long,
        val meta: Long,
        val multi: Long,
        val fallbacks: Long,
        val errors: Long,
    ) {
        val fallbackRatePercent: Double
            get() = if (total > 0L) (fallbacks.toDouble() / total.toDouble()) * 100.0 else 0.0

        val errorRatePercent: Double
            get() = if (total > 0L) (errors.toDouble() / total.toDouble()) * 100.0 else 0.0
    }

    private const val PREFS = "x88_ai_router_metrics"
    private const val KEY_TOTAL = "total"
    private const val KEY_LOCAL = "local"
    private const val KEY_CLAUDE = "claude"
    private const val KEY_META = "meta"
    private const val KEY_MULTI = "multi"
    private const val KEY_FALLBACKS = "fallbacks"
    private const val KEY_ERRORS = "errors"

    @Synchronized
    fun record(context: Context, result: SmartAiRouter.Result) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val engineKey = when {
            result.providerId?.startsWith("claude-ai:") == true -> KEY_CLAUDE
            result.providerId?.startsWith("meta-ai:") == true -> KEY_META
            result.providerId?.startsWith("multi-ai:") == true -> KEY_MULTI
            else -> KEY_LOCAL
        }
        prefs.edit()
            .putLong(KEY_TOTAL, prefs.getLong(KEY_TOTAL, 0L) + 1L)
            .putLong(engineKey, prefs.getLong(engineKey, 0L) + 1L)
            .putLong(KEY_FALLBACKS, prefs.getLong(KEY_FALLBACKS, 0L) + if (result.fallbackUsed) 1L else 0L)
            .putLong(KEY_ERRORS, prefs.getLong(KEY_ERRORS, 0L) + if (!result.ok) 1L else 0L)
            .apply()
    }

    fun snapshot(context: Context): Snapshot {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Snapshot(
            total = prefs.getLong(KEY_TOTAL, 0L),
            local = prefs.getLong(KEY_LOCAL, 0L),
            claude = prefs.getLong(KEY_CLAUDE, 0L),
            meta = prefs.getLong(KEY_META, 0L),
            multi = prefs.getLong(KEY_MULTI, 0L),
            fallbacks = prefs.getLong(KEY_FALLBACKS, 0L),
            errors = prefs.getLong(KEY_ERRORS, 0L),
        )
    }

    internal fun ratioPercent(part: Long, total: Long): Double =
        if (total > 0L) (part.toDouble() / total.toDouble()) * 100.0 else 0.0
}

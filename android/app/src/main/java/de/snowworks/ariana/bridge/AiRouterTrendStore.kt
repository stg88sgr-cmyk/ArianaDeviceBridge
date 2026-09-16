package de.snowworks.ariana.bridge

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Bounded hourly router aggregates for recent trend diagnostics.
 * Stores counters only. Never stores prompts, replies, provider responses or credentials.
 */
object AiRouterTrendStore {
    const val HOUR_MS = 60L * 60L * 1000L
    const val WINDOW_24H_MS = 24L * HOUR_MS
    const val WINDOW_7D_MS = 7L * 24L * HOUR_MS
    private const val MAX_BUCKETS = 7 * 24 + 2
    private const val PREFS = "x88_ai_router_trends"
    private const val KEY_BUCKETS = "hourly_v1"

    data class Bucket(
        val hourStartMs: Long,
        val total: Long,
        val local: Long,
        val claude: Long,
        val meta: Long,
        val multi: Long,
        val fallbacks: Long,
        val errors: Long,
    )

    data class Window(
        val total: Long,
        val local: Long,
        val claude: Long,
        val meta: Long,
        val multi: Long,
        val fallbacks: Long,
        val errors: Long,
    ) {
        val fallbackRatePercent: Double
            get() = AiRouterMetricsStore.ratioPercent(fallbacks, total)
        val errorRatePercent: Double
            get() = AiRouterMetricsStore.ratioPercent(errors, total)
    }

    data class Snapshot(
        val last24Hours: Window,
        val last7Days: Window,
    )

    @Synchronized
    fun record(context: Context, result: SmartAiRouter.Result, nowWallMs: Long = System.currentTimeMillis()) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val hour = floorHour(nowWallMs)
        val buckets = readBuckets(prefs.getString(KEY_BUCKETS, null)).toMutableList()
        val currentIndex = buckets.indexOfFirst { it.hourStartMs == hour }
        val base = if (currentIndex >= 0) buckets.removeAt(currentIndex) else emptyBucket(hour)
        val engine = engine(result)
        val updated = base.copy(
            total = base.total + 1L,
            local = base.local + if (engine == "LOCAL") 1L else 0L,
            claude = base.claude + if (engine == "CLAUDE") 1L else 0L,
            meta = base.meta + if (engine == "META") 1L else 0L,
            multi = base.multi + if (engine == "MULTI") 1L else 0L,
            fallbacks = base.fallbacks + if (result.fallbackUsed) 1L else 0L,
            errors = base.errors + if (!result.ok) 1L else 0L,
        )
        buckets.add(updated)
        val cutoff = hour - WINDOW_7D_MS
        val compact = buckets.filter { it.hourStartMs >= cutoff }
            .sortedBy { it.hourStartMs }
            .takeLast(MAX_BUCKETS)
        prefs.edit().putString(KEY_BUCKETS, encode(compact)).apply()
    }

    fun snapshot(context: Context, nowWallMs: Long = System.currentTimeMillis()): Snapshot {
        val raw = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_BUCKETS, null)
        val buckets = readBuckets(raw)
        return Snapshot(
            last24Hours = aggregate(buckets, nowWallMs - WINDOW_24H_MS),
            last7Days = aggregate(buckets, nowWallMs - WINDOW_7D_MS),
        )
    }

    internal fun aggregate(buckets: List<Bucket>, cutoffMs: Long): Window {
        val selected = buckets.filter { it.hourStartMs >= cutoffMs }
        return Window(
            total = selected.sumOf { it.total },
            local = selected.sumOf { it.local },
            claude = selected.sumOf { it.claude },
            meta = selected.sumOf { it.meta },
            multi = selected.sumOf { it.multi },
            fallbacks = selected.sumOf { it.fallbacks },
            errors = selected.sumOf { it.errors },
        )
    }

    internal fun floorHour(valueMs: Long): Long =
        if (valueMs <= 0L) 0L else valueMs - (valueMs % HOUR_MS)

    private fun emptyBucket(hour: Long) = Bucket(hour, 0L, 0L, 0L, 0L, 0L, 0L, 0L)

    private fun engine(result: SmartAiRouter.Result): String = when {
        result.providerId?.startsWith("claude-ai:") == true -> "CLAUDE"
        result.providerId?.startsWith("meta-ai:") == true -> "META"
        result.providerId?.startsWith("multi-ai:") == true -> "MULTI"
        else -> "LOCAL"
    }

    private fun encode(buckets: List<Bucket>): String = JSONArray().apply {
        buckets.forEach { bucket ->
            put(JSONObject()
                .put("h", bucket.hourStartMs)
                .put("t", bucket.total)
                .put("l", bucket.local)
                .put("c", bucket.claude)
                .put("m", bucket.meta)
                .put("u", bucket.multi)
                .put("f", bucket.fallbacks)
                .put("e", bucket.errors))
        }
    }.toString()

    private fun readBuckets(raw: String?): List<Bucket> = runCatching {
        val array = JSONArray(raw ?: "[]")
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(Bucket(
                    hourStartMs = item.optLong("h", 0L),
                    total = item.optLong("t", 0L),
                    local = item.optLong("l", 0L),
                    claude = item.optLong("c", 0L),
                    meta = item.optLong("m", 0L),
                    multi = item.optLong("u", 0L),
                    fallbacks = item.optLong("f", 0L),
                    errors = item.optLong("e", 0L),
                ))
            }
        }
    }.getOrDefault(emptyList())
}

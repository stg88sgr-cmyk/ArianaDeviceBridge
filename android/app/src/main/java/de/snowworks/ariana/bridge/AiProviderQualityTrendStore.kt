package de.snowworks.ariana.bridge

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Bounded hourly provider-quality aggregates for recent diagnostics.
 * Stores counters only. Never stores prompts, replies, payloads, models or credentials.
 */
object AiProviderQualityTrendStore {
    const val HOUR_MS = 60L * 60L * 1000L
    const val WINDOW_24H_MS = 24L * HOUR_MS
    const val WINDOW_7D_MS = 7L * 24L * HOUR_MS
    private const val MAX_BUCKETS = 7 * 24 + 2
    private const val PREFS = "x88_ai_provider_quality_trends"

    enum class Event { SELECTED, EXECUTED_SUCCESS, EXECUTED_ERROR, CIRCUIT_REJECTED }

    data class Bucket(
        val hourStartMs: Long,
        val selected: Long,
        val executed: Long,
        val successes: Long,
        val errors: Long,
        val circuitRejected: Long,
        val fallbackSelections: Long,
    )

    data class Window(
        val selected: Long,
        val executed: Long,
        val successes: Long,
        val errors: Long,
        val circuitRejected: Long,
        val fallbackSelections: Long,
    ) {
        val successRatePercent: Double get() = AiProviderQualityStore.ratioPercent(successes, executed)
        val errorRatePercent: Double get() = AiProviderQualityStore.ratioPercent(errors, executed)
        val fallbackSharePercent: Double get() = AiProviderQualityStore.ratioPercent(fallbackSelections, selected)
        val executionRatePercent: Double get() = AiProviderQualityStore.ratioPercent(executed, selected)
    }

    data class Snapshot(
        val engine: AiProviderQualityStore.Engine,
        val last24Hours: Window,
        val last7Days: Window,
    )

    @Synchronized
    fun record(
        context: Context,
        engine: AiProviderQualityStore.Engine,
        event: Event,
        fallbackAttempt: Boolean = false,
        nowWallMs: Long = System.currentTimeMillis(),
    ) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = key(engine)
        val hour = floorHour(nowWallMs)
        val buckets = readBuckets(prefs.getString(key, null)).toMutableList()
        val currentIndex = buckets.indexOfFirst { it.hourStartMs == hour }
        val base = if (currentIndex >= 0) buckets.removeAt(currentIndex) else emptyBucket(hour)
        val updated = when (event) {
            Event.SELECTED -> base.copy(
                selected = base.selected + 1L,
                fallbackSelections = base.fallbackSelections + if (fallbackAttempt) 1L else 0L,
            )
            Event.EXECUTED_SUCCESS -> base.copy(
                executed = base.executed + 1L,
                successes = base.successes + 1L,
            )
            Event.EXECUTED_ERROR -> base.copy(
                executed = base.executed + 1L,
                errors = base.errors + 1L,
            )
            Event.CIRCUIT_REJECTED -> base.copy(circuitRejected = base.circuitRejected + 1L)
        }
        buckets.add(updated)
        val cutoff = hour - WINDOW_7D_MS
        val compact = buckets.filter { it.hourStartMs >= cutoff }
            .sortedBy { it.hourStartMs }
            .takeLast(MAX_BUCKETS)
        prefs.edit().putString(key, encode(compact)).apply()
    }

    fun snapshot(
        context: Context,
        engine: AiProviderQualityStore.Engine,
        nowWallMs: Long = System.currentTimeMillis(),
    ): Snapshot {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val buckets = readBuckets(prefs.getString(key(engine), null))
        return Snapshot(
            engine = engine,
            last24Hours = aggregate(buckets, nowWallMs - WINDOW_24H_MS),
            last7Days = aggregate(buckets, nowWallMs - WINDOW_7D_MS),
        )
    }

    internal fun aggregate(buckets: List<Bucket>, cutoffMs: Long): Window {
        val selected = buckets.filter { it.hourStartMs >= cutoffMs }
        return Window(
            selected = selected.sumOf { it.selected },
            executed = selected.sumOf { it.executed },
            successes = selected.sumOf { it.successes },
            errors = selected.sumOf { it.errors },
            circuitRejected = selected.sumOf { it.circuitRejected },
            fallbackSelections = selected.sumOf { it.fallbackSelections },
        )
    }

    internal fun floorHour(valueMs: Long): Long =
        if (valueMs <= 0L) 0L else valueMs - (valueMs % HOUR_MS)

    private fun emptyBucket(hour: Long) = Bucket(hour, 0L, 0L, 0L, 0L, 0L, 0L)

    private fun key(engine: AiProviderQualityStore.Engine): String = "${engine.name.lowercase()}_hourly_v1"

    private fun encode(buckets: List<Bucket>): String = JSONArray().apply {
        buckets.forEach { bucket ->
            put(JSONObject()
                .put("h", bucket.hourStartMs)
                .put("s", bucket.selected)
                .put("x", bucket.executed)
                .put("o", bucket.successes)
                .put("e", bucket.errors)
                .put("c", bucket.circuitRejected)
                .put("f", bucket.fallbackSelections))
        }
    }.toString()

    private fun readBuckets(raw: String?): List<Bucket> = runCatching {
        val array = JSONArray(raw ?: "[]")
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(Bucket(
                    hourStartMs = item.optLong("h", 0L),
                    selected = item.optLong("s", 0L),
                    executed = item.optLong("x", 0L),
                    successes = item.optLong("o", 0L),
                    errors = item.optLong("e", 0L),
                    circuitRejected = item.optLong("c", 0L),
                    fallbackSelections = item.optLong("f", 0L),
                ))
            }
        }
    }.getOrDefault(emptyList())
}

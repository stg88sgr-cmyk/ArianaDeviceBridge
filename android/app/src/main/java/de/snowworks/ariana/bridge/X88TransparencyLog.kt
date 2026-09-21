package de.snowworks.ariana.bridge

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Bounded metadata-only transparency journal.
 *
 * Never stores prompt text, replies, API keys or payloads.
 */
class X88TransparencyLog(context: Context) {
    data class Entry(
        val providerId: String,
        val dataClass: String,
        val consent: String,
        val ok: Boolean,
        val latencyMs: Long,
        val errorCode: String? = null,
    )

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun record(entry: Entry) {
        val current = runCatching { JSONArray(prefs.getString(KEY_ENTRIES, "[]")) }.getOrDefault(JSONArray())
        current.put(
            JSONObject()
                .put("ts", System.currentTimeMillis())
                .put("providerId", entry.providerId.take(80))
                .put("dataClass", entry.dataClass.take(40))
                .put("consent", entry.consent.take(32))
                .put("ok", entry.ok)
                .put("latencyMs", entry.latencyMs.coerceAtLeast(0L))
                .put("errorCode", entry.errorCode?.take(80)),
        )
        val start = maxOf(0, current.length() - MAX_ENTRIES)
        val bounded = JSONArray()
        for (i in start until current.length()) bounded.put(current.getJSONObject(i))
        prefs.edit().putString(KEY_ENTRIES, bounded.toString()).apply()
    }

    fun recent(limit: Int = 50): List<String> {
        val source = runCatching { JSONArray(prefs.getString(KEY_ENTRIES, "[]")) }.getOrDefault(JSONArray())
        val start = maxOf(0, source.length() - limit.coerceIn(1, MAX_ENTRIES))
        return buildList {
            for (i in start until source.length()) add(source.getJSONObject(i).toString())
        }
    }

    companion object {
        private const val PREFS = "x88_transparency"
        private const val KEY_ENTRIES = "entries"
        private const val MAX_ENTRIES = 200
    }
}

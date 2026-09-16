package de.snowworks.ariana.bridge

import android.content.Context
import org.json.JSONObject

/**
 * Persists only provider health metadata. No prompts, replies or credentials are stored here.
 */
internal class AiProviderHealthPersistence(context: Context) {
    data class Record(
        val providerId: String,
        val consecutiveFailures: Int,
        val wallOpenUntilMs: Long,
        val lastError: String?,
    )

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun loadAll(): List<Record> = prefs.all.mapNotNull { (key, raw) ->
        if (!key.startsWith(KEY_PREFIX)) return@mapNotNull null
        val encoded = raw as? String ?: return@mapNotNull null
        runCatching {
            val json = JSONObject(encoded)
            Record(
                providerId = json.getString("providerId"),
                consecutiveFailures = json.optInt("consecutiveFailures", 0).coerceAtLeast(0),
                wallOpenUntilMs = json.optLong("wallOpenUntilMs", 0L).coerceAtLeast(0L),
                lastError = json.optString("lastError", "").takeIf { it.isNotBlank() },
            )
        }.getOrNull()
    }

    fun save(record: Record) {
        val id = record.providerId.trim().take(80)
        if (id.isBlank()) return
        val json = JSONObject()
            .put("providerId", id)
            .put("consecutiveFailures", record.consecutiveFailures.coerceAtLeast(0))
            .put("wallOpenUntilMs", record.wallOpenUntilMs.coerceAtLeast(0L))
            .put("lastError", record.lastError.orEmpty().take(80))
            .toString()
        prefs.edit().putString(key(id), json).apply()
    }

    fun remove(providerId: String) {
        prefs.edit().remove(key(providerId)).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun key(providerId: String): String = KEY_PREFIX + providerId.trim().take(80)

    private companion object {
        const val PREFS = "x88_ai_provider_health_v1"
        const val KEY_PREFIX = "provider::"
    }
}

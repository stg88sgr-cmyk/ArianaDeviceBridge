package de.snowworks.ariana.bridge

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Lightweight status only. Never stores prompts, replies or credentials. */
object AiRouteStateStore {
    data class State(
        val engine: String,
        val taskClass: String,
        val fallbackUsed: Boolean,
        val updatedAtMs: Long,
    )

    data class HistoryEntry(
        val engine: String,
        val taskClass: String,
        val fallbackUsed: Boolean,
        val updatedAtMs: Long,
    )

    private const val PREFS = "x88_ai_route_state"
    private const val KEY_ENGINE = "engine"
    private const val KEY_TASK = "task"
    private const val KEY_FALLBACK = "fallback"
    private const val KEY_UPDATED = "updated"
    private const val KEY_HISTORY = "history_v1"
    private const val MAX_HISTORY = 12

    @Synchronized
    fun publish(context: Context, result: SmartAiRouter.Result) {
        val engine = when {
            result.providerId?.startsWith("claude-ai:") == true -> "CLAUDE"
            result.providerId?.startsWith("meta-ai:") == true -> "META"
            result.providerId?.startsWith("multi-ai:") == true -> "MULTI"
            else -> "LOCAL"
        }
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previous = read(app)
        val now = System.currentTimeMillis()
        val task = result.taskClass.name

        prefs.edit()
            .putString(KEY_ENGINE, engine)
            .putString(KEY_TASK, task)
            .putBoolean(KEY_FALLBACK, result.fallbackUsed)
            .putLong(KEY_UPDATED, now)
            .apply()

        if (previous.updatedAtMs == 0L ||
            previous.engine != engine ||
            previous.taskClass != task ||
            previous.fallbackUsed != result.fallbackUsed
        ) {
            appendHistory(prefs, HistoryEntry(engine, task, result.fallbackUsed, now))
        }
    }

    fun read(context: Context): State {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return State(
            engine = prefs.getString(KEY_ENGINE, "LOCAL") ?: "LOCAL",
            taskClass = prefs.getString(KEY_TASK, SmartAiRouter.TaskClass.GENERAL.name)
                ?: SmartAiRouter.TaskClass.GENERAL.name,
            fallbackUsed = prefs.getBoolean(KEY_FALLBACK, false),
            updatedAtMs = prefs.getLong(KEY_UPDATED, 0L),
        )
    }

    fun history(context: Context): List<HistoryEntry> {
        val raw = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_HISTORY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(
                        HistoryEntry(
                            engine = item.optString("engine", "LOCAL"),
                            taskClass = item.optString("task", SmartAiRouter.TaskClass.GENERAL.name),
                            fallbackUsed = item.optBoolean("fallback", false),
                            updatedAtMs = item.optLong("updated", 0L),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun label(context: Context): String {
        val state = read(context)
        return buildString {
            append("AI · ").append(state.engine)
            if (state.fallbackUsed) append(" · FALLBACK")
        }
    }

    private fun appendHistory(
        prefs: android.content.SharedPreferences,
        entry: HistoryEntry,
    ) {
        val existing = runCatching {
            JSONArray(prefs.getString(KEY_HISTORY, "[]") ?: "[]")
        }.getOrElse { JSONArray() }
        val compact = JSONArray()
        val start = maxOf(0, existing.length() - (MAX_HISTORY - 1))
        for (index in start until existing.length()) compact.put(existing.optJSONObject(index))
        compact.put(
            JSONObject()
                .put("engine", entry.engine)
                .put("task", entry.taskClass)
                .put("fallback", entry.fallbackUsed)
                .put("updated", entry.updatedAtMs),
        )
        prefs.edit().putString(KEY_HISTORY, compact.toString()).apply()
    }
}

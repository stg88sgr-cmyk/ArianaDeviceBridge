package de.snowworks.ariana.bridge

import android.content.Context

/** Lightweight status only. Never stores prompts, replies or credentials. */
object AiRouteStateStore {
    data class State(
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

    fun publish(context: Context, result: SmartAiRouter.Result) {
        val engine = when {
            result.providerId?.startsWith("claude-ai:") == true -> "CLAUDE"
            result.providerId?.startsWith("meta-ai:") == true -> "META"
            result.providerId?.startsWith("multi-ai:") == true -> "MULTI"
            else -> "LOCAL"
        }
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ENGINE, engine)
            .putString(KEY_TASK, result.taskClass.name)
            .putBoolean(KEY_FALLBACK, result.fallbackUsed)
            .putLong(KEY_UPDATED, System.currentTimeMillis())
            .apply()
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

    fun label(context: Context): String {
        val state = read(context)
        return buildString {
            append("AI · ").append(state.engine)
            if (state.fallbackUsed) append(" · FALLBACK")
        }
    }
}

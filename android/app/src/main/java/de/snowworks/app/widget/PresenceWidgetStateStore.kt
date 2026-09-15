package de.snowworks.app.widget

import android.content.Context

object PresenceWidgetStateStore {
    data class State(
        val core: String,
        val dialog: String,
        val wakeword: String,
    )

    private const val PREFS = "ariana_presence_widget"
    private const val KEY_CORE = "core"
    private const val KEY_DIALOG = "dialog"
    private const val KEY_WAKEWORD = "wakeword"

    fun read(context: Context): State {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return State(
            core = prefs.getString(KEY_CORE, "CORE · CHECKING") ?: "CORE · CHECKING",
            dialog = prefs.getString(KEY_DIALOG, "DIALOG · READY") ?: "DIALOG · READY",
            wakeword = prefs.getString(KEY_WAKEWORD, "WAKEWORD · OFF") ?: "WAKEWORD · OFF",
        )
    }

    fun publishCore(context: Context, value: String) = publish(context, KEY_CORE, value)
    fun publishDialog(context: Context, value: String) = publish(context, KEY_DIALOG, value)
    fun publishWakeword(context: Context, value: String) = publish(context, KEY_WAKEWORD, value)

    private fun publish(context: Context, key: String, raw: String) {
        val value = raw.replace(Regex("\\s+"), " ").trim().take(96)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(key, null) == value) return
        prefs.edit().putString(key, value).apply()
        ArianaPresenceWidgetProvider.updateAll(context)
    }
}

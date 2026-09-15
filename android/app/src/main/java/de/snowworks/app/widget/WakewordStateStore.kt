package de.snowworks.app.widget

import android.content.Context

object WakewordStateStore {
    private const val PREFS = "ariana_presence_wakeword"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_STATUS = "status"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
        setStatus(context, if (enabled) "WAKEWORD · STARTET" else "WAKEWORD · OFF")
    }

    fun setStatus(context: Context, status: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_STATUS, status).apply()
        PresenceWidgetStateStore.publishWakeword(context, status)
    }

    fun status(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_STATUS, "WAKEWORD · OFF") ?: "WAKEWORD · OFF"
}

package de.snowworks.ariana.bridge

import android.content.Context

/**
 * Process-wide outbound AI gate.
 *
 * Cloud access is disabled by default. The persisted switch is loaded once at
 * application startup and every remote provider must check this gate before it
 * can open a network connection.
 */
object CloudAccessGate {
    private const val PREFS = "x88_cloud_access"
    private const val KEY_ENABLED = "enabled"

    @Volatile
    private var enabled: Boolean = false

    fun initialize(context: Context) {
        enabled = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)
    }

    fun isEnabled(): Boolean = enabled

    fun setEnabled(context: Context, value: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, value)
            .apply()
        enabled = value
        X88SecurityAudit.record(
            actor = X88EgressPolicy.Actor.ARIANA_X88.name,
            event = if (value) "cloud_access_enabled" else "cloud_access_disabled",
            detail = "source=user_controlled_gate",
        )
    }
}

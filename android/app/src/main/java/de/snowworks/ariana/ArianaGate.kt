package de.snowworks.ariana

import android.content.Context

/**
 * Master switch and stop-all latch. Does not grant or revoke Android permissions.
 * Never auto-enables after install, process death, or reboot — default is off.
 */
class ArianaGate(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val isMasterEnabled: Boolean
        get() = prefs.getBoolean(KEY_MASTER, false)

    val isBlocked: Boolean
        get() = prefs.getBoolean(KEY_BLOCKED, false)

    fun setMasterEnabled(enabled: Boolean) {
        if (enabled) {
            prefs.edit().putBoolean(KEY_MASTER, true).putBoolean(KEY_BLOCKED, false).apply()
        } else {
            prefs.edit().putBoolean(KEY_MASTER, false).apply()
        }
    }

    fun blockAfterStopAll() {
        prefs.edit().putBoolean(KEY_MASTER, false).putBoolean(KEY_BLOCKED, true).apply()
    }

    fun assertCanUse(): ArianaResult<Unit> {
        if (isBlocked) {
            return ArianaResult.Err(
                ArianaError(
                    ArianaError.Code.BLOCKED,
                    "Alles stoppen ist aktiv. Schalte Ariana-Gerätezugriff wieder ein.",
                ),
            )
        }
        if (!isMasterEnabled) {
            return ArianaResult.Err(
                ArianaError(
                    ArianaError.Code.MASTER_OFF,
                    "Ariana-Gerätezugriff ist aus. Der Schalter ersetzt keine Android-Berechtigung.",
                ),
            )
        }
        return ArianaResult.Ok(Unit)
    }

    companion object {
        private const val PREFS = "ariana_gate"
        private const val KEY_MASTER = "master"
        private const val KEY_BLOCKED = "blocked"
    }
}

package de.snowworks.app.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock

/**
 * Passive X-88 boot entry point.
 *
 * Receiving BOOT_COMPLETED starts the application process, which lets
 * [de.snowworks.app.SnowworksApp] restore only the already-approved local
 * core surfaces. This receiver deliberately does not start camera,
 * microphone, media-projection or data-sync foreground services because
 * Android 15+ forbids those service types from BOOT_COMPLETED.
 */
class X88BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action !in ACCEPTED_ACTIONS) return

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_BOOT_EVENT_ELAPSED_MS, SystemClock.elapsedRealtime())
            .putString(KEY_LAST_BOOT_ACTION, action)
            .apply()

        // No explicit service start is needed here: Android creates the app
        // process before dispatching this receiver, so SnowworksApp.onCreate()
        // has already restored the user-approved X-88 local core state.
    }

    companion object {
        private const val PREFS = "x88_boot_state"
        private const val KEY_LAST_BOOT_EVENT_ELAPSED_MS = "last_boot_event_elapsed_ms"
        private const val KEY_LAST_BOOT_ACTION = "last_boot_action"

        private val ACCEPTED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
    }
}

package de.snowworks.ariana.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Safe boot hook. It only asks the autonomy supervisor to reconcile existing user
 * authority; it does not enable master control, capabilities, sensors or capture.
 */
class X88BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        X88AutonomyController.initialize(context.applicationContext)
    }
}

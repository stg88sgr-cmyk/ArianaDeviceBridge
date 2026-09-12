package de.snowworks.app

import android.app.Application
import de.snowworks.ariana.ArianaGate
import de.snowworks.ariana.bridge.AiProviderManager
import de.snowworks.ariana.bridge.LocalAiProviderManager
import de.snowworks.ariana.bridge.LocalBridgeServer
import de.snowworks.ariana.session.SessionRegistry

class SnowworksApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // No device/session restore after process death.
        SessionRegistry.clear()

        // Restore only the local loopback core when the user-controlled master gate
        // was already enabled and Stop-All has not blocked new actions.
        val gate = ArianaGate(this)
        if (gate.isMasterEnabled && !gate.isBlocked) {
            LocalBridgeServer.start(this)
        }

        // Prefer an explicitly enabled on-device model. Fall back to a previously
        // configured HTTPS provider only when no local model is active.
        if (!LocalAiProviderManager.activateConfigured(this)) {
            AiProviderManager.activateConfigured(this)
        }
    }
}

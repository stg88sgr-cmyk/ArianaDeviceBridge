package de.snowworks.app

import android.app.Application
import android.util.Log
import de.snowworks.ariana.ArianaGate
import de.snowworks.ariana.bridge.AiProviderManager
import de.snowworks.ariana.bridge.LocalAiProviderManager
import de.snowworks.ariana.bridge.LocalBridgeServer
import de.snowworks.ariana.session.SessionRegistry

class SnowworksApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // App boot must never crash because an optional subsystem fails.
        runCatching { SessionRegistry.clear() }
            .onFailure { Log.e(TAG, "SessionRegistry.clear failed", it) }

        val gate = runCatching { ArianaGate(this) }
            .onFailure { Log.e(TAG, "ArianaGate init failed", it) }
            .getOrNull()

        if (gate != null && gate.isMasterEnabled && !gate.isBlocked) {
            runCatching { LocalBridgeServer.start(this) }
                .onFailure { Log.e(TAG, "LocalBridgeServer.start failed", it) }
        }

        // Provider activation is optional. A broken model/provider must not block UI startup.
        val localActivated = runCatching { LocalAiProviderManager.activateConfigured(this) }
            .onFailure { Log.e(TAG, "Local AI provider activation failed", it) }
            .getOrDefault(false)

        if (!localActivated) {
            runCatching { AiProviderManager.activateConfigured(this) }
                .onFailure { Log.e(TAG, "Remote AI provider activation failed", it) }
        }
    }

    private companion object {
        const val TAG = "SnowworksApp"
    }
}

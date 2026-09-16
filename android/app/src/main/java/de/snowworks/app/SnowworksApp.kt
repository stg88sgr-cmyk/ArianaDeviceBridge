package de.snowworks.app

import android.app.Application
import android.util.Log
import de.snowworks.ariana.ArianaGate
import de.snowworks.ariana.bridge.AiProviderManager
import de.snowworks.ariana.bridge.DialogueRouter
import de.snowworks.ariana.bridge.LocalAiProviderManager
import de.snowworks.ariana.bridge.LocalBridgeServer
import de.snowworks.ariana.session.SessionRegistry
import de.snowworks.ariana.thermal.ThermalSafetyController

class SnowworksApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // App boot must never crash because an optional subsystem fails.
        runCatching { SessionRegistry.clear() }
            .onFailure { Log.e(TAG, "SessionRegistry.clear failed", it) }

        // Multi-AI command routing needs only an application-scoped context.
        runCatching { DialogueRouter.initialize(this) }
            .onFailure { Log.e(TAG, "DialogueRouter.initialize failed", it) }

        // Thermal safety starts before bridge/provider activation so a hot device
        // cannot start heavyweight local work during process boot.
        runCatching { ThermalSafetyController.start(this) }
            .onFailure { Log.e(TAG, "ThermalSafetyController.start failed", it) }

        val gate = runCatching { ArianaGate(this) }
            .onFailure { Log.e(TAG, "ArianaGate init failed", it) }
            .getOrNull()

        if (gate != null && gate.isMasterEnabled && !gate.isBlocked) {
            runCatching { LocalBridgeServer.start(this) }
                .onFailure { Log.e(TAG, "LocalBridgeServer.start failed", it) }
        }

        // Provider activation is optional. A broken model/provider must not block UI startup.
        // LocalAiProviderManager also checks the live thermal policy before loading a model.
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

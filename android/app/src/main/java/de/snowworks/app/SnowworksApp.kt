package de.snowworks.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import de.snowworks.app.ui.HomeAiEngineIndicator
import de.snowworks.app.ui.X88HomeActivity
import de.snowworks.ariana.ArianaGate
import de.snowworks.ariana.bridge.AiProviderHealth
import de.snowworks.ariana.bridge.AiProviderManager
import de.snowworks.ariana.bridge.AiProviderRecoverySupervisor
import de.snowworks.ariana.bridge.DialogueRouter
import de.snowworks.ariana.bridge.LocalAiProviderManager
import de.snowworks.ariana.bridge.LoopbackArianaProviderManager
import de.snowworks.ariana.bridge.LocalBridgeServer
import de.snowworks.ariana.bridge.UniversalBridgeStateStore
import de.snowworks.ariana.session.SessionRegistry

class SnowworksApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Device capture sessions remain intentionally non-restorable after process death.
        SessionRegistry.clear()

        // Durable Universal Bridge V2 configuration survives process/app restarts.
        UniversalBridgeStateStore(this).ensureDefaults(
            currentBuild = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        )

        // Restore cloud provider circuit-breaker/recovery metadata before any AI
        // provider can be selected or called. No prompts, replies or credentials
        // are persisted by this health layer.
        AiProviderHealth.initialize(this)
        DialogueRouter.initialize(this)
        registerHomeAiIndicator()

        // Restore only the local loopback core when the user-controlled master gate
        // was already enabled and Stop-All has not blocked new actions.
        val gate = ArianaGate(this)
        if (gate.isMasterEnabled && !gate.isBlocked) {
            LocalBridgeServer.start(this)
        }

        // Prefer Ariana's local providers. A configured HTTPS provider remains
        // available as a secondary multi-AI reviewer when local Ariana is active.
        if (!LoopbackArianaProviderManager.activateIfAvailable() &&
            !LocalAiProviderManager.activateConfigured(this)
        ) {
            AiProviderManager.activateConfigured(this)
        }

        // Process-local background recovery: probes only configured providers that
        // have completed their cooldown and are RECOVERY READY. It never changes
        // DialogueRouter's active provider and creates no new Android permission need.
        AiProviderRecoverySupervisor.start(this)
    }

    private fun registerHomeAiIndicator() {
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit

            override fun onActivityResumed(activity: Activity) {
                if (activity is X88HomeActivity) HomeAiEngineIndicator.attach(activity)
            }

            override fun onActivityPaused(activity: Activity) {
                if (activity is X88HomeActivity) HomeAiEngineIndicator.detach(activity)
            }

            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) {
                if (activity is X88HomeActivity) HomeAiEngineIndicator.detach(activity)
            }
        })
    }
}

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
import de.snowworks.ariana.bridge.CloudAccessGate
import de.snowworks.ariana.bridge.DialogueRouter
import de.snowworks.ariana.bridge.LocalAiProviderManager
import de.snowworks.ariana.bridge.LoopbackArianaProviderManager
import de.snowworks.ariana.bridge.LocalBridgeServer
import de.snowworks.ariana.bridge.UniversalBridgeStateStore
import de.snowworks.ariana.neuro.V37EvidenceRuntime
import de.snowworks.ariana.neuro.X88HealthMonitor
import de.snowworks.ariana.session.SessionRegistry
import de.snowworks.ariana.universal.v2.ArianaUniversalRuntimeV2
import de.snowworks.ariana.world.X88FantasyWorldBootstrap

class SnowworksApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SessionRegistry.clear()
        CloudAccessGate.initialize(this)
        UniversalBridgeStateStore(this).ensureDefaults(
            currentBuild = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        )
        ArianaUniversalRuntimeV2.initialize(this)

        // V36 stabilization restores the durable local model snapshot before the neuro fabric starts.
        val x88Runtime = V37EvidenceRuntime.initialize(this)
        // V38 observes the composed runtime through a read-only central health monitor.
        X88HealthMonitor(x88Runtime.evidence).check(x88Runtime)
        val inneresWerdenRuntime = x88Runtime.base
        val neuroRuntime = inneresWerdenRuntime.base

        X88FantasyWorldBootstrap.attach(neuroRuntime.base)
        AiProviderHealth.initialize(this)
        DialogueRouter.initialize(this)
        registerHomeAiIndicator()

        val gate = ArianaGate(this)
        if (gate.isMasterEnabled && !gate.isBlocked) {
            LocalBridgeServer.start(this)
        }

        if (!LocalAiProviderManager.activateConfigured(this) &&
            !LoopbackArianaProviderManager.activateIfAvailable() &&
            CloudAccessGate.isEnabled()
        ) {
            AiProviderManager.activateConfigured(this)
        }

        if (CloudAccessGate.isEnabled()) {
            AiProviderRecoverySupervisor.start(this)
        }
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

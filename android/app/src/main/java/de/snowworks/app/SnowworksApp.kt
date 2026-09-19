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
import de.snowworks.ariana.neuro.V32InneresWerdenRuntime
import de.snowworks.ariana.session.SessionRegistry
import de.snowworks.ariana.universal.v2.ArianaUniversalRuntimeV2
import de.snowworks.ariana.world.X88FantasyWorldBootstrap

class SnowworksApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Device capture sessions remain intentionally non-restorable after process death.
        SessionRegistry.clear()

        // Outbound AI access is deny-by-default and must be explicitly enabled.
        CloudAccessGate.initialize(this)

        // Durable Universal Bridge V2 configuration survives process/app restarts.
        UniversalBridgeStateStore(this).ensureDefaults(
            currentBuild = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        )

        // Consolidated Universal Core V2 runtime. It mirrors the current master/stop state
        // and becomes the single home for task scheduling, telemetry and adaptive runtime work.
        ArianaUniversalRuntimeV2.initialize(this)

        // V16-V31 neuro runtime is an internal local signal layer. V21-V29 extend the
        // original fabric with attention, context, explicit intent stabilization,
        // proposal-only planning, action-result integration, lifecycle regulation,
        // metadata-only telemetry and integrity monitoring. V30 is the composed health
        // gate; V31 adds read-only observability for UI/diagnostics. Neither grants
        // Android permissions or bypasses X-88 SecurityChain.
        val inneresWerdenRuntime = V32InneresWerdenRuntime.initialize()
        val neuroRuntime = inneresWerdenRuntime.base

        // Symbolic/fantasy world layer remains attached to the underlying V30 fabric.
        // It is explicitly fictional/non-physical and has no ACTION_REQUEST output,
        // so real device actions remain behind X-88 gates.
        X88FantasyWorldBootstrap.attach(neuroRuntime.base)

        // Restore cloud provider circuit-breaker/recovery metadata before any AI
        // provider can be selected or called. No prompts, replies or credentials
        // are persisted by this health layer.
        AiProviderHealth.initialize(this)
        DialogueRouter.initialize(this)
        registerHomeAiIndicator()

        // Restore only the local loopback control bridge when the user-controlled master gate
        // was already enabled and Stop-All has not blocked new actions.
        val gate = ArianaGate(this)
        if (gate.isMasterEnabled && !gate.isBlocked) {
            LocalBridgeServer.start(this)
        }

        // Stable local-first boot: prefer a real installed on-device model. If none is
        // available, register the same-device Ariana core. Remote AI becomes an active
        // fallback only when the explicit outbound gate has been enabled by the user.
        if (!LocalAiProviderManager.activateConfigured(this) &&
            !LoopbackArianaProviderManager.activateIfAvailable() &&
            CloudAccessGate.isEnabled()
        ) {
            AiProviderManager.activateConfigured(this)
        }

        // Remote recovery probes are network-capable, so they only run when the
        // explicit outbound gate is enabled.
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

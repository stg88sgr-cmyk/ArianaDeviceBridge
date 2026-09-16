package de.snowworks.ariana.bridge

import android.content.Context
import java.net.URI

/**
 * Read-only AI routing health snapshot.
 *
 * This reporter never calls a cloud provider. It only combines persisted
 * provider configuration, the in-process circuit breaker state and the last
 * lightweight routing status for diagnostics.
 */
object AiHealthReporter {
    data class ProviderStatus(
        val engine: String,
        val configured: Boolean,
        val providerId: String? = null,
        val model: String? = null,
        val circuitOpen: Boolean = false,
        val consecutiveFailures: Int = 0,
        val halfOpenProbeInFlight: Boolean = false,
        val lastError: String? = null,
    )

    data class Snapshot(
        val activeLocalProviderId: String?,
        val route: AiRouteStateStore.State,
        val claude: ProviderStatus,
        val meta: ProviderStatus,
    )

    fun snapshot(context: Context): Snapshot {
        val app = context.applicationContext
        val registry = CloudProviderRegistry(app).also { it.migrateActiveIfNeeded() }
        return Snapshot(
            activeLocalProviderId = DialogueRouter.providerId(),
            route = AiRouteStateStore.read(app),
            claude = status("CLAUDE", "claude", registry.load(CloudProviderRegistry.Slot.CLAUDE)),
            meta = status("META", "meta", registry.load(CloudProviderRegistry.Slot.META)),
        )
    }

    private fun status(
        engine: String,
        prefix: String,
        config: SecureAiProviderStore.Config?,
    ): ProviderStatus {
        if (config == null) return ProviderStatus(engine = engine, configured = false)
        val providerId = providerId(prefix, config)
        val health = AiProviderHealth.snapshot(providerId)
        return ProviderStatus(
            engine = engine,
            configured = true,
            providerId = providerId,
            model = config.model,
            circuitOpen = health.circuitOpen,
            consecutiveFailures = health.consecutiveFailures,
            halfOpenProbeInFlight = health.halfOpenProbeInFlight,
            lastError = health.lastError,
        )
    }

    private fun providerId(prefix: String, config: SecureAiProviderStore.Config): String {
        val host = runCatching { URI(config.endpoint).host.orEmpty() }.getOrDefault("")
            .lowercase()
            .replace(Regex("[^a-z0-9.-]"), "-")
            .take(30)
        val model = config.model.lowercase()
            .replace(Regex("[^a-z0-9._-]"), "-")
            .take(28)
        return "$prefix-ai:$host:$model".take(80)
    }
}

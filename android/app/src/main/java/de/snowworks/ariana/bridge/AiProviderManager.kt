package de.snowworks.ariana.bridge

import android.content.Context
import java.net.URI

/** Coordinates encrypted provider configuration and DialogueRouter registration. */
object AiProviderManager {
    data class Status(
        val configured: Boolean,
        val active: Boolean,
        val providerId: String?,
        val endpointHost: String?,
        val model: String?,
        val apiKeyPresent: Boolean,
    )

    @Synchronized
    fun activateConfigured(context: Context): Boolean {
        val config = SecureAiProviderStore(context).load() ?: run {
            if (DialogueRouter.providerId()?.startsWith("https-ai:") == true) {
                DialogueRouter.unregister()
            }
            return false
        }
        val host = runCatching { URI(config.endpoint).host }.getOrNull().orEmpty()
        if (host.isBlank()) {
            if (DialogueRouter.providerId()?.startsWith("https-ai:") == true) {
                DialogueRouter.unregister()
            }
            return false
        }

        // The on-device Ariana model is authoritative whenever it is active.
        // A configured HTTPS provider (for example Meta Model API) remains
        // available to MultiAiRouter as a secondary reviewer/co-pilot.
        if (DialogueRouter.providerId() == LocalAiProviderManager.PROVIDER_ID) {
            return true
        }

        val generator = HttpsDialogueProvider(config)
        return DialogueRouter.register(providerId(host, config.model)) { text -> generator.generate(text) }
    }

    @Synchronized
    fun configure(
        context: Context,
        endpoint: String,
        model: String,
        apiKey: String,
    ): Boolean {
        val config = SecureAiProviderStore.Config(
            endpoint = endpoint.trim(),
            model = model.trim(),
            apiKey = apiKey.trim(),
        )
        SecureAiProviderStore(context).save(config)

        // Saving a secondary provider must not evict an already active local Ariana.
        if (DialogueRouter.providerId() == LocalAiProviderManager.PROVIDER_ID) {
            return true
        }
        return activateConfigured(context)
    }

    @Synchronized
    fun clear(context: Context) {
        SecureAiProviderStore(context).clear()
        if (DialogueRouter.providerId()?.startsWith("https-ai:") == true) {
            DialogueRouter.unregister()
        }
    }

    fun status(context: Context): Status {
        val config = SecureAiProviderStore(context).load()
        val activeId = DialogueRouter.providerId()
        if (config == null) {
            return Status(false, false, activeId, null, null, false)
        }
        val host = runCatching { URI(config.endpoint).host }.getOrNull()
        val configuredProviderId = host?.takeIf { it.isNotBlank() }?.let { providerId(it, config.model) }
        return Status(
            configured = true,
            active = activeId != null && activeId == configuredProviderId,
            providerId = activeId,
            endpointHost = host,
            model = config.model,
            apiKeyPresent = config.apiKey.isNotBlank(),
        )
    }

    private fun providerId(host: String, model: String): String {
        val safeHost = host.lowercase().replace(Regex("[^a-z0-9.-]"), "-").take(36)
        val safeModel = model.lowercase().replace(Regex("[^a-z0-9._-]"), "-").take(28)
        return "https-ai:$safeHost:$safeModel".take(80)
    }
}

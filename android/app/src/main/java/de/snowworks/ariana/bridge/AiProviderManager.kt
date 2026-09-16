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
        val recoveryAvailable: Boolean,
    )

    data class ProbeResult(
        val ok: Boolean,
        val providerHost: String? = null,
        val model: String? = null,
        val replyPreview: String? = null,
        val error: String? = null,
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

    @Synchronized
    fun restorePrevious(context: Context): Boolean {
        val restored = SecureAiProviderStore(context).restorePrevious()
        if (!restored) return false
        AiProviderHealth.reset()
        return activateConfigured(context)
    }

    /**
     * Performs one minimal live request with the encrypted, already-saved config.
     * It does not switch providers, persist the reply, or expose the API key.
     */
    fun testConfigured(context: Context): ProbeResult {
        val config = SecureAiProviderStore(context.applicationContext).load()
            ?: return ProbeResult(ok = false, error = "PROVIDER_NOT_CONFIGURED")
        if (config.apiKey.isBlank()) {
            return ProbeResult(ok = false, error = "PROVIDER_KEY_MISSING")
        }

        val host = runCatching { URI(config.endpoint).host }.getOrNull()
        if (host.isNullOrBlank()) {
            return ProbeResult(ok = false, model = config.model, error = "PROVIDER_CONFIG_INVALID")
        }

        return try {
            val reply = HttpsDialogueProvider(config).generate(
                "Connectivity smoke test for Ariana X-88. Reply briefly with META_SMOKE_OK.",
            )
            ProbeResult(
                ok = reply.isNotBlank(),
                providerHost = host,
                model = config.model,
                replyPreview = reply
                    .replace(Regex("[\\u0000-\\u001f\\u007f]+"), " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                    .take(120),
                error = if (reply.isBlank()) "PROVIDER_EMPTY_REPLY" else null,
            )
        } catch (error: DialogueRouter.ProviderException) {
            ProbeResult(
                ok = false,
                providerHost = host,
                model = config.model,
                error = error.code,
            )
        } catch (_: Exception) {
            ProbeResult(
                ok = false,
                providerHost = host,
                model = config.model,
                error = "PROVIDER_PROBE_FAILED",
            )
        }
    }

    fun status(context: Context): Status {
        val store = SecureAiProviderStore(context)
        val config = store.load()
        val activeId = DialogueRouter.providerId()
        if (config == null) {
            return Status(
                configured = false,
                active = false,
                providerId = activeId,
                endpointHost = null,
                model = null,
                apiKeyPresent = false,
                recoveryAvailable = store.hasRecoverySnapshot(),
            )
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
            recoveryAvailable = store.hasRecoverySnapshot(),
        )
    }

    private fun providerId(host: String, model: String): String {
        val safeHost = host.lowercase().replace(Regex("[^a-z0-9.-]"), "-").take(36)
        val safeModel = model.lowercase().replace(Regex("[^a-z0-9._-]"), "-").take(28)
        return "https-ai:$safeHost:$safeModel".take(80)
    }
}

package de.snowworks.ariana.bridge

import android.content.Context
import java.net.URI

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
            if (DialogueRouter.providerId()?.startsWith("https-ai:") == true) DialogueRouter.unregister()
            return false
        }
        val host = runCatching { URI(config.endpoint).host }.getOrNull().orEmpty()
        if (host.isBlank()) {
            if (DialogueRouter.providerId()?.startsWith("https-ai:") == true) DialogueRouter.unregister()
            return false
        }
        if (DialogueRouter.providerId() == LocalAiProviderManager.PROVIDER_ID) return true
        val generate = generatorFor(config)
        return DialogueRouter.register(providerId(host, config.model)) { text -> generate(text) }
    }

    @Synchronized
    fun configure(context: Context, endpoint: String, model: String, apiKey: String): Boolean {
        val config = SecureAiProviderStore.Config(endpoint.trim(), model.trim(), apiKey.trim())
        SecureAiProviderStore(context).save(config)
        if (DialogueRouter.providerId() == LocalAiProviderManager.PROVIDER_ID) return true
        return activateConfigured(context)
    }

    @Synchronized
    fun clear(context: Context) {
        SecureAiProviderStore(context).clear()
        if (DialogueRouter.providerId()?.startsWith("https-ai:") == true) DialogueRouter.unregister()
    }

    @Synchronized
    fun restorePrevious(context: Context): Boolean {
        if (!SecureAiProviderStore(context).restorePrevious()) return false
        AiProviderHealth.reset()
        return activateConfigured(context)
    }

    fun testConfigured(context: Context): ProbeResult {
        val config = SecureAiProviderStore(context.applicationContext).load()
            ?: return ProbeResult(false, error = "PROVIDER_NOT_CONFIGURED")
        if (config.apiKey.isBlank()) return ProbeResult(false, error = "PROVIDER_KEY_MISSING")
        val host = runCatching { URI(config.endpoint).host }.getOrNull()
        if (host.isNullOrBlank()) return ProbeResult(false, model = config.model, error = "PROVIDER_CONFIG_INVALID")
        return try {
            val reply = generatorFor(config)("Connectivity smoke test for Ariana X-88. Reply briefly with X88_PROVIDER_OK.")
            ProbeResult(
                ok = reply.isNotBlank(),
                providerHost = host,
                model = config.model,
                replyPreview = reply.replace(Regex("[\\u0000-\\u001f\\u007f]+"), " ").replace(Regex("\\s+"), " ").trim().take(120),
                error = if (reply.isBlank()) "PROVIDER_EMPTY_REPLY" else null,
            )
        } catch (error: DialogueRouter.ProviderException) {
            ProbeResult(false, host, config.model, error = error.code)
        } catch (_: Exception) {
            ProbeResult(false, host, config.model, error = "PROVIDER_PROBE_FAILED")
        }
    }

    fun status(context: Context): Status {
        val store = SecureAiProviderStore(context)
        val config = store.load()
        val activeId = DialogueRouter.providerId()
        if (config == null) return Status(false, false, activeId, null, null, false, store.hasRecoverySnapshot())
        val host = runCatching { URI(config.endpoint).host }.getOrNull()
        val configuredId = host?.takeIf { it.isNotBlank() }?.let { providerId(it, config.model) }
        return Status(true, activeId != null && activeId == configuredId, activeId, host, config.model, config.apiKey.isNotBlank(), store.hasRecoverySnapshot())
    }

    internal fun isClaudeConfig(config: SecureAiProviderStore.Config): Boolean {
        val uri = runCatching { URI(config.endpoint.trim()) }.getOrNull() ?: return false
        return uri.host.equals(ClaudeDialogueProvider.ANTHROPIC_HOST, ignoreCase = true)
    }

    private fun generatorFor(config: SecureAiProviderStore.Config): (String) -> String {
        return if (isClaudeConfig(config)) {
            val provider = ClaudeDialogueProvider(config)
            provider::generate
        } else {
            val provider = HttpsDialogueProvider(config)
            provider::generate
        }
    }

    private fun providerId(host: String, model: String): String {
        val safeHost = host.lowercase().replace(Regex("[^a-z0-9.-]"), "-").take(36)
        val safeModel = model.lowercase().replace(Regex("[^a-z0-9._-]"), "-").take(28)
        return "https-ai:$safeHost:$safeModel".take(80)
    }
}

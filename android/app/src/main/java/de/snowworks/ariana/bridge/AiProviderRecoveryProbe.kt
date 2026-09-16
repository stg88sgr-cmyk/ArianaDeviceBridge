package de.snowworks.ariana.bridge

import android.content.Context
import java.net.URI

/**
 * Explicit provider health probe used by the Health UI.
 *
 * It never changes DialogueRouter's active provider and never stores the
 * response body. Circuit-breaker rules stay authoritative: an OPEN provider
 * cannot be force-probed before cooldown, while RECOVERY READY allows exactly
 * one half-open attempt.
 */
object AiProviderRecoveryProbe {
    data class Result(
        val ok: Boolean,
        val engine: String,
        val providerId: String? = null,
        val model: String? = null,
        val error: String? = null,
    )

    fun run(context: Context, slot: CloudProviderRegistry.Slot): Result {
        val app = context.applicationContext
        val registry = CloudProviderRegistry(app).also { it.migrateActiveIfNeeded() }
        val config = registry.load(slot)
            ?: return Result(false, slot.name, error = "PROVIDER_NOT_CONFIGURED")
        if (config.apiKey.isBlank()) return Result(false, slot.name, model = config.model, error = "PROVIDER_KEY_MISSING")

        val prefix = when (slot) {
            CloudProviderRegistry.Slot.CLAUDE -> "claude"
            CloudProviderRegistry.Slot.META -> "meta"
        }
        val providerId = providerId(prefix, config)
        if (!AiProviderHealth.acquireAttempt(providerId)) {
            return Result(false, slot.name, providerId, config.model, "PROVIDER_CIRCUIT_OPEN")
        }

        return try {
            val prompt = "Ariana X-88 provider health probe. Reply briefly with X88_PROVIDER_OK."
            val reply = when (slot) {
                CloudProviderRegistry.Slot.CLAUDE -> ClaudeDialogueProvider(config).generate(prompt)
                CloudProviderRegistry.Slot.META -> HttpsDialogueProvider(config).generate(prompt)
            }
            if (reply.isBlank()) {
                AiProviderHealth.recordFailure(providerId, "PROVIDER_FAILED")
                Result(false, slot.name, providerId, config.model, "PROVIDER_EMPTY_REPLY")
            } else {
                AiProviderHealth.recordSuccess(providerId)
                Result(true, slot.name, providerId, config.model)
            }
        } catch (error: DialogueRouter.ProviderException) {
            AiProviderHealth.recordFailure(providerId, error.code)
            Result(false, slot.name, providerId, config.model, error.code)
        } catch (_: Exception) {
            val code = if (slot == CloudProviderRegistry.Slot.CLAUDE) "CLAUDE_PROVIDER_FAILED" else "META_PROVIDER_FAILED"
            AiProviderHealth.recordFailure(providerId, code)
            Result(false, slot.name, providerId, config.model, code)
        }
    }

    private fun providerId(prefix: String, config: SecureAiProviderStore.Config): String {
        val host = runCatching { URI(config.endpoint).host.orEmpty() }.getOrDefault("")
            .lowercase().replace(Regex("[^a-z0-9.-]"), "-").take(30)
        val model = config.model.lowercase().replace(Regex("[^a-z0-9._-]"), "-").take(28)
        return "$prefix-ai:$host:$model".take(80)
    }
}

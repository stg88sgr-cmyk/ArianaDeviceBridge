package de.snowworks.ariana.bridge

import android.content.Context
import java.net.URI

/**
 * Role-aware AI orchestration for Universal Bridge V2.
 *
 * Routing policy:
 * - local/active Ariana provider: private, device-adjacent and ordinary dialogue
 * - Claude: code, architecture, debugging and deep technical analysis
 * - Meta: general second opinion / alternative reasoning when configured
 *
 * CloudAiPolicy remains authoritative and can still force LOCAL_ONLY.
 */
object SmartAiRouter {
    enum class TaskClass {
        PRIVATE_LOCAL,
        GENERAL,
        CODE_ARCHITECTURE,
        SECOND_OPINION,
    }

    data class Result(
        val ok: Boolean,
        val taskClass: TaskClass,
        val providerId: String? = null,
        val reply: String? = null,
        val fallbackUsed: Boolean = false,
        val error: String? = null,
    )

    fun classify(rawText: String): TaskClass {
        val text = rawText.lowercase()

        if (containsAny(text, PRIVATE_MARKERS)) return TaskClass.PRIVATE_LOCAL
        if (containsAny(text, CODE_MARKERS)) return TaskClass.CODE_ARCHITECTURE
        if (containsAny(text, SECOND_OPINION_MARKERS)) return TaskClass.SECOND_OPINION
        return TaskClass.GENERAL
    }

    fun route(context: Context, rawText: String): Result {
        val text = sanitize(rawText)
        if (text.isBlank()) return publish(context, Result(false, TaskClass.GENERAL, error = "INVALID_INPUT"))

        val policy = CloudAiPolicy.evaluate(text)
        if (policy.disposition == CloudAiPolicy.Disposition.LOCAL_ONLY) {
            return publish(context, callLocal(text, TaskClass.PRIVATE_LOCAL))
        }

        val registry = CloudProviderRegistry(context.applicationContext).also { it.migrateActiveIfNeeded() }
        val result = when (val taskClass = classify(text)) {
            TaskClass.PRIVATE_LOCAL -> callLocal(text, taskClass)
            TaskClass.CODE_ARCHITECTURE -> callClaudeMetaLocal(
                registry = registry,
                cloudText = policy.text,
                originalText = text,
                taskClass = taskClass,
            )
            TaskClass.SECOND_OPINION -> callMetaClaudeLocal(
                registry = registry,
                cloudText = policy.text,
                originalText = text,
                taskClass = taskClass,
            )
            TaskClass.GENERAL -> callLocal(text, taskClass)
        }
        return publish(context, result)
    }

    private fun publish(context: Context, result: Result): Result {
        runCatching { AiRouteStateStore.publish(context, result) }
        return result
    }

    private fun callClaudeMetaLocal(
        registry: CloudProviderRegistry,
        cloudText: String,
        originalText: String,
        taskClass: TaskClass,
    ): Result {
        val claude = registry.load(CloudProviderRegistry.Slot.CLAUDE)
        if (claude != null) {
            val result = callClaude(claude, cloudText, taskClass)
            if (result.ok) return result
        }

        val meta = registry.load(CloudProviderRegistry.Slot.META)
        if (meta != null) {
            val result = callMeta(meta, cloudText, taskClass)
            if (result.ok) return result.copy(fallbackUsed = true)
        }

        return callLocal(originalText, taskClass, fallback = true)
    }

    private fun callMetaClaudeLocal(
        registry: CloudProviderRegistry,
        cloudText: String,
        originalText: String,
        taskClass: TaskClass,
    ): Result {
        val meta = registry.load(CloudProviderRegistry.Slot.META)
        if (meta != null) {
            val result = callMeta(meta, cloudText, taskClass)
            if (result.ok) return result
        }

        val claude = registry.load(CloudProviderRegistry.Slot.CLAUDE)
        if (claude != null) {
            val result = callClaude(claude, cloudText, taskClass)
            if (result.ok) return result.copy(fallbackUsed = true)
        }

        return callLocal(originalText, taskClass, fallback = true)
    }

    private fun callLocal(text: String, taskClass: TaskClass, fallback: Boolean = false): Result {
        val outcome = DialogueRouter.generateActiveProvider(text)
        return Result(
            ok = outcome.ok,
            taskClass = taskClass,
            providerId = outcome.providerId,
            reply = outcome.reply,
            fallbackUsed = fallback,
            error = outcome.error,
        )
    }

    private fun callClaude(
        config: SecureAiProviderStore.Config,
        text: String,
        taskClass: TaskClass,
    ): Result {
        val providerId = providerId("claude", config)
        if (!AiProviderHealth.acquireAttempt(providerId)) {
            return Result(false, taskClass, providerId = providerId, error = "CLAUDE_CIRCUIT_OPEN")
        }
        return try {
            val reply = ClaudeDialogueProvider(config).generate(text)
            AiProviderHealth.recordSuccess(providerId)
            Result(true, taskClass, providerId = providerId, reply = reply)
        } catch (error: DialogueRouter.ProviderException) {
            AiProviderHealth.recordFailure(providerId, error.code)
            Result(false, taskClass, providerId = providerId, error = error.code)
        } catch (_: Exception) {
            AiProviderHealth.recordFailure(providerId, "CLAUDE_PROVIDER_FAILED")
            Result(false, taskClass, providerId = providerId, error = "CLAUDE_PROVIDER_FAILED")
        }
    }

    private fun callMeta(
        config: SecureAiProviderStore.Config,
        text: String,
        taskClass: TaskClass,
    ): Result {
        val providerId = providerId("meta", config)
        if (!AiProviderHealth.acquireAttempt(providerId)) {
            return Result(false, taskClass, providerId = providerId, error = "META_CIRCUIT_OPEN")
        }
        return try {
            val reply = HttpsDialogueProvider(config).generate(text)
            AiProviderHealth.recordSuccess(providerId)
            Result(true, taskClass, providerId = providerId, reply = reply)
        } catch (error: DialogueRouter.ProviderException) {
            AiProviderHealth.recordFailure(providerId, error.code)
            Result(false, taskClass, providerId = providerId, error = error.code)
        } catch (_: Exception) {
            AiProviderHealth.recordFailure(providerId, "META_PROVIDER_FAILED")
            Result(false, taskClass, providerId = providerId, error = "META_PROVIDER_FAILED")
        }
    }

    private fun providerId(prefix: String, config: SecureAiProviderStore.Config): String {
        val host = runCatching { URI(config.endpoint).host.orEmpty() }.getOrDefault("")
            .lowercase().replace(Regex("[^a-z0-9.-]"), "-").take(30)
        val model = config.model.lowercase().replace(Regex("[^a-z0-9._-]"), "-").take(28)
        return "$prefix-ai:$host:$model".take(80)
    }

    private fun sanitize(rawText: String): String = rawText
        .replace(Regex("[\\u0000-\\u001f\\u007f]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(DialogueRouter.MAX_INPUT_CHARS)

    private fun containsAny(text: String, markers: Set<String>): Boolean = markers.any(text::contains)

    private val PRIVATE_MARKERS = setOf(
        "passwort", "password", "api key", "api-key", "token", "private key", "privater schlüssel",
        "meine nachrichten", "mein standort", "meine kontakte", "bank", "iban", "konto",
        "lokal bleiben", "offline", "nicht in die cloud", "nur lokal",
    )

    private val CODE_MARKERS = setOf(
        "code", "kotlin", "java", "python", "typescript", "javascript", "gradle", "android",
        "github", "repository", "repo", "compile", "build fehler", "build error", "stacktrace",
        "exception", "debug", "refactor", "architektur", "architecture", "api design", "review code",
        "bug", "funktion", "class ", "interface ", "ktor", "compose", "manifest", "proguard",
    )

    private val SECOND_OPINION_MARKERS = setOf(
        "zweite meinung", "gegencheck", "gegenprüfen", "alternative", "vergleich", "compare",
        "was sagt meta", "meta fragen", "zweiter ansatz", "second opinion",
    )
}

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
        val fallbackReason: String? = null,
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
                context = context.applicationContext,
                registry = registry,
                cloudText = policy.text,
                originalText = text,
                taskClass = taskClass,
                cloudClassification = policy.classification,
                cloudClassification = policy.classification,
            )
            TaskClass.SECOND_OPINION -> callMetaClaudeLocal(
                context = context.applicationContext,
                registry = registry,
                cloudText = policy.text,
                originalText = text,
                taskClass = taskClass,
                cloudClassification = policy.classification,
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
        context: Context,
        registry: CloudProviderRegistry,
        cloudText: String,
        originalText: String,
        taskClass: TaskClass,
        cloudClassification: CloudAiPolicy.Classification,
    ): Result {
        var reason: String? = null
        val claude = registry.load(CloudProviderRegistry.Slot.CLAUDE)
        if (claude != null) {
            val result = callClaude(context, claude, cloudText, taskClass, fallbackAttempt = false)
            if (result.ok) return result
            reason = appendReason(reason, result.error ?: "CLAUDE_FAILED")
        } else {
            reason = appendReason(reason, "CLAUDE_NOT_CONFIGURED")
        }

        val meta = registry.load(CloudProviderRegistry.Slot.META)
        if (meta != null) {
            val result = callMeta(context, meta, cloudText, taskClass, fallbackAttempt = true, cloudClassification = cloudClassification)
            if (result.ok) return result.copy(fallbackUsed = true, fallbackReason = reason)
            reason = appendReason(reason, result.error ?: "META_FAILED")
        } else {
            reason = appendReason(reason, "META_NOT_CONFIGURED")
        }

        return callLocal(originalText, taskClass, fallback = true, fallbackReason = reason)
    }

    private fun callMetaClaudeLocal(
        context: Context,
        registry: CloudProviderRegistry,
        cloudText: String,
        originalText: String,
        taskClass: TaskClass,
        cloudClassification: CloudAiPolicy.Classification,
    ): Result {
        var reason: String? = null
        val meta = registry.load(CloudProviderRegistry.Slot.META)
        if (meta != null) {
            val result = callMeta(context, meta, cloudText, taskClass, fallbackAttempt = false, cloudClassification = cloudClassification)
            if (result.ok) return result
            reason = appendReason(reason, result.error ?: "META_FAILED")
        } else {
            reason = appendReason(reason, "META_NOT_CONFIGURED")
        }

        val claude = registry.load(CloudProviderRegistry.Slot.CLAUDE)
        if (claude != null) {
            val result = callClaude(context, claude, cloudText, taskClass, fallbackAttempt = true)
            if (result.ok) return result.copy(fallbackUsed = true, fallbackReason = reason)
            reason = appendReason(reason, result.error ?: "CLAUDE_FAILED")
        } else {
            reason = appendReason(reason, "CLAUDE_NOT_CONFIGURED")
        }

        return callLocal(originalText, taskClass, fallback = true, fallbackReason = reason)
    }

    private fun callLocal(
        text: String,
        taskClass: TaskClass,
        fallback: Boolean = false,
        fallbackReason: String? = null,
    ): Result {
        val outcome = DialogueRouter.generateActiveProvider(text)
        return Result(
            ok = outcome.ok,
            taskClass = taskClass,
            providerId = outcome.providerId,
            reply = outcome.reply,
            fallbackUsed = fallback,
            error = outcome.error,
            fallbackReason = fallbackReason,
        )
    }

    private fun callClaude(
        context: Context,
        config: SecureAiProviderStore.Config,
        text: String,
        taskClass: TaskClass,
        fallbackAttempt: Boolean,
    ): Result {
        val providerId = providerId("claude", config)
        AiProviderQualityStore.recordSelection(context, AiProviderQualityStore.Engine.CLAUDE, fallbackAttempt)
        if (!AiProviderHealth.acquireAttempt(providerId)) {
            AiProviderQualityStore.recordCircuitRejected(context, AiProviderQualityStore.Engine.CLAUDE)
            return Result(false, taskClass, providerId = providerId, error = "CLAUDE_CIRCUIT_OPEN")
        }
        return try {
            val reply = ClaudeDialogueProvider(config).generate(text)
            AiProviderHealth.recordSuccess(providerId)
            AiProviderQualityStore.recordExecuted(context, AiProviderQualityStore.Engine.CLAUDE, ok = true)
            Result(true, taskClass, providerId = providerId, reply = reply)
        } catch (error: DialogueRouter.ProviderException) {
            AiProviderHealth.recordFailure(providerId, error.code)
            AiProviderQualityStore.recordExecuted(context, AiProviderQualityStore.Engine.CLAUDE, ok = false)
            Result(false, taskClass, providerId = providerId, error = error.code)
        } catch (_: Exception) {
            AiProviderHealth.recordFailure(providerId, "CLAUDE_PROVIDER_FAILED")
            AiProviderQualityStore.recordExecuted(context, AiProviderQualityStore.Engine.CLAUDE, ok = false)
            Result(false, taskClass, providerId = providerId, error = "CLAUDE_PROVIDER_FAILED")
        }
    }

    private fun callMeta(
        context: Context,
        config: SecureAiProviderStore.Config,
        text: String,
        taskClass: TaskClass,
        fallbackAttempt: Boolean,
        cloudClassification: CloudAiPolicy.Classification,
    ): Result {
        val providerId = providerId("meta", config)
        val consent = X88ConsentManager(context).evaluateCloudPolicy(cloudClassification, providerId)
        val log = X88TransparencyLog(context)
        if (consent == X88ConsentManager.ConsentDecision.DENY) {
            log.record(
                X88TransparencyLog.Entry(
                    providerId = providerId,
                    dataClass = cloudClassification.name,
                    consent = consent.name,
                    ok = false,
                    latencyMs = 0L,
                    errorCode = "X88_CONSENT_DENIED",
                ),
            )
            return Result(false, taskClass, providerId = providerId, error = "X88_CONSENT_DENIED")
        }
        AiProviderQualityStore.recordSelection(context, AiProviderQualityStore.Engine.META, fallbackAttempt)
        if (!AiProviderHealth.acquireAttempt(providerId)) {
            AiProviderQualityStore.recordCircuitRejected(context, AiProviderQualityStore.Engine.META)
            return Result(false, taskClass, providerId = providerId, error = "META_CIRCUIT_OPEN")
        }
        val startedAt = System.nanoTime()
        return try {
            val reply = HttpsDialogueProvider(config).generate(text)
            AiProviderHealth.recordSuccess(providerId)
            AiProviderQualityStore.recordExecuted(context, AiProviderQualityStore.Engine.META, ok = true)
            log.record(
                X88TransparencyLog.Entry(
                    providerId = providerId,
                    dataClass = cloudClassification.name,
                    consent = consent.name,
                    ok = true,
                    latencyMs = (System.nanoTime() - startedAt) / 1_000_000L,
                ),
            )
            Result(true, taskClass, providerId = providerId, reply = reply)
        } catch (error: DialogueRouter.ProviderException) {
            AiProviderHealth.recordFailure(providerId, error.code)
            AiProviderQualityStore.recordExecuted(context, AiProviderQualityStore.Engine.META, ok = false)
            log.record(
                X88TransparencyLog.Entry(
                    providerId = providerId,
                    dataClass = cloudClassification.name,
                    consent = consent.name,
                    ok = false,
                    latencyMs = (System.nanoTime() - startedAt) / 1_000_000L,
                    errorCode = error.code,
                ),
            )
            Result(false, taskClass, providerId = providerId, error = error.code)
        } catch (_: Exception) {
            AiProviderHealth.recordFailure(providerId, "META_PROVIDER_FAILED")
            AiProviderQualityStore.recordExecuted(context, AiProviderQualityStore.Engine.META, ok = false)
            log.record(
                X88TransparencyLog.Entry(
                    providerId = providerId,
                    dataClass = cloudClassification.name,
                    consent = consent.name,
                    ok = false,
                    latencyMs = (System.nanoTime() - startedAt) / 1_000_000L,
                    errorCode = "META_PROVIDER_FAILED",
                ),
            )
            Result(false, taskClass, providerId = providerId, error = "META_PROVIDER_FAILED")
        }
    }

    private fun appendReason(current: String?, next: String): String =
        if (current.isNullOrBlank()) next else "$current > $next"

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

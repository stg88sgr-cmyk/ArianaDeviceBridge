package de.snowworks.ariana.bridge

import android.content.Context
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

object DialogueRouter {
    const val MAX_INPUT_CHARS = 1200
    const val MAX_REPLY_CHARS = 2400
    const val PROVIDER_TIMEOUT_MS = 25_000L
    const val LOCAL_PROVIDER_TIMEOUT_MS = 120_000L

    class ProviderException(val code: String, cause: Throwable? = null) : RuntimeException(code, cause) {
        init { require(code.matches(Regex("[A-Z0-9_]{3,80}"))) }
    }

    data class Outcome(val ok: Boolean, val providerId: String? = null, val reply: String? = null, val error: String? = null)
    fun interface Generator { fun generate(text: String): String }
    private data class Provider(val id: String, val generator: Generator, val timeoutMs: Long)
    private data class MultiAiCommand(val mode: MultiAiRouter.Mode, val text: String)

    private val executor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "ArianaDialogueProvider").apply { isDaemon = true } }
    @Volatile private var provider: Provider? = null
    @Volatile private var appContext: Context? = null
    @Volatile private var memoryAccess: de.snowworks.ariana.memory.ArianaX88MemoryAccess? = null

    fun initialize(
        context: Context,
        durableMemoryAccess: de.snowworks.ariana.memory.ArianaX88MemoryAccess? = null,
    ) {
        val app = context.applicationContext
        appContext = app
        memoryAccess = durableMemoryAccess ?: de.snowworks.ariana.memory.ArianaX88MemoryAccess(
            de.snowworks.ariana.memory.X88MemoryRepository(
                de.snowworks.ariana.memory.DefaultX88MemoryBridge(),
                de.snowworks.ariana.memory.SharedPreferencesX88MemoryStore(app),
            )
        )
    }

    internal fun applicationContext(): Context? = appContext
    internal fun durableMemoryAccess(): de.snowworks.ariana.memory.ArianaX88MemoryAccess? = memoryAccess

    @Synchronized
    fun register(providerId: String, timeoutMs: Long = PROVIDER_TIMEOUT_MS, generator: Generator): Boolean {
        val id = providerId.trim().take(80)
        if (!id.matches(Regex("[A-Za-z0-9._:-]{1,80}"))) return false
        if (timeoutMs !in 1_000L..180_000L) return false
        provider = Provider(id, generator, timeoutMs)
        return true
    }

    @Synchronized fun unregister() { provider = null }
    fun providerId(): String? = provider?.id

    fun generate(rawText: String): Outcome {
        val command = parseMultiAiCommand(rawText)
        if (command != null) {
            val context = appContext ?: return Outcome(false, error = "MULTI_AI_NOT_INITIALIZED")
            return multiAiOutcome(MultiAiRouter.run(context, command.mode, command.text))
        }

        val context = appContext
        if (context != null) {
            val smart = SmartAiRouter.route(context, rawText)
            return Outcome(
                ok = smart.ok,
                providerId = smart.providerId,
                reply = smart.reply?.let(::sanitizeReply),
                error = smart.error,
            )
        }

        // JVM/unit-test and pre-initialization compatibility: stay on the active provider.
        return generateActiveProvider(rawText)
    }

    /**
     * Calls only the currently active Ariana provider. SmartAiRouter uses this as
     * its local/private path to avoid re-entering automatic cloud routing.
     */
    internal fun generateActiveProvider(rawText: String): Outcome {
        val text = sanitize(rawText)
        if (text.isEmpty()) return Outcome(false, error = "INVALID_INPUT")
        val current = provider ?: return Outcome(false, error = "PROVIDER_UNAVAILABLE")
        val future = executor.submit<String> { current.generator.generate(text) }
        return try {
            val reply = future.get(current.timeoutMs, TimeUnit.MILLISECONDS)
                .replace(Regex("[\\u0000-\\u001f\\u007f]+"), " ")
                .replace(Regex("\\s+"), " ").trim().take(MAX_REPLY_CHARS)
            if (reply.isEmpty()) Outcome(false, current.id, error = "EMPTY_REPLY") else Outcome(true, current.id, reply)
        } catch (_: TimeoutException) {
            future.cancel(true); Outcome(false, current.id, error = "PROVIDER_TIMEOUT")
        } catch (error: ExecutionException) {
            future.cancel(true); Outcome(false, current.id, error = (error.cause as? ProviderException)?.code ?: "PROVIDER_FAILED")
        } catch (_: Exception) {
            future.cancel(true); Outcome(false, current.id, error = "PROVIDER_FAILED")
        }
    }

    internal fun generateActiveProviderWithMemory(
        rawText: String,
        memoryAccess: de.snowworks.ariana.memory.ArianaX88MemoryAccess,
        project: String? = null,
        topic: String? = null,
    ): Outcome = generateWithMemory(rawText, memoryAccess, project, topic)

    /** Generates a reply with bounded, filtered X88 memory context. */
    fun generateWithMemory(
        rawText: String,
        memoryAccess: de.snowworks.ariana.memory.ArianaX88MemoryAccess,
        project: String? = null,
        topic: String? = null,
    ): Outcome {
        val text = sanitize(rawText)
        if (text.isEmpty()) return Outcome(false, error = "INVALID_INPUT")
        val memoryContext = DialogueMemoryContext.from(memoryAccess, project, topic)
        val enriched = if (memoryContext.isBlank()) {
            text
        } else {
            "X88-MEMORY CONTEXT:\n$memoryContext\n\nUSER:\n$text"
        }
        return generateActiveProvider(enriched)
    }

    private fun parseMultiAiCommand(rawText: String): MultiAiCommand? {
        val trimmed = rawText.trim()
        val commands = listOf(
            "/meta" to MultiAiRouter.Mode.META,
            "/parallel" to MultiAiRouter.Mode.PARALLEL,
            "/review" to MultiAiRouter.Mode.REVIEW,
            "/consensus" to MultiAiRouter.Mode.CONSENSUS,
            "/repair" to MultiAiRouter.Mode.REPAIR,
        )
        for ((prefix, mode) in commands) {
            if (trimmed.equals(prefix, true)) return MultiAiCommand(mode, "")
            if (trimmed.startsWith("$prefix ", true)) return MultiAiCommand(mode, trimmed.substring(prefix.length).trim())
        }
        return null
    }

    private fun multiAiOutcome(result: MultiAiRouter.Result): Outcome {
        if (!result.ok) return Outcome(false, "multi-ai:${result.mode.name.lowercase()}", error = result.error ?: "MULTI_AI_FAILED")
        val reply = when (result.mode) {
            MultiAiRouter.Mode.META -> result.metaReply
            MultiAiRouter.Mode.PARALLEL -> buildString {
                result.primaryReply?.let { append("Ariana: ").append(it) }
                if (!result.primaryReply.isNullOrBlank() && !result.metaReply.isNullOrBlank()) append("\n\n")
                result.metaReply?.let { append("Meta: ").append(it) }
            }
            MultiAiRouter.Mode.REVIEW -> buildString {
                result.primaryReply?.let { append("Ariana: ").append(it) }
                if (!result.primaryReply.isNullOrBlank() && !result.review.isNullOrBlank()) append("\n\n")
                result.review?.let { append("Meta-Review: ").append(it) }
            }
            MultiAiRouter.Mode.CONSENSUS, MultiAiRouter.Mode.REPAIR -> result.consensus ?: result.primaryReply ?: result.metaReply
        }
        val cleaned = sanitizeReply(reply.orEmpty())
        return if (cleaned.isEmpty()) Outcome(false, "multi-ai:${result.mode.name.lowercase()}", error = "EMPTY_REPLY")
        else Outcome(true, "multi-ai:${result.mode.name.lowercase()}", cleaned)
    }

    private fun sanitize(rawText: String) = rawText.replace(Regex("[\\u0000-\\u001f\\u007f]+"), " ").replace(Regex("\\s+"), " ").trim().take(MAX_INPUT_CHARS)
    private fun sanitizeReply(rawText: String) = rawText.replace(Regex("[\\u0000-\\u001f\\u007f]+"), " ").replace(Regex("\\s+"), " ").trim().take(MAX_REPLY_CHARS)
}

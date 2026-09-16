package de.snowworks.ariana.bridge

import android.content.Context
import java.net.URI
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Coordinates Ariana's active dialogue provider with the configured remote
 * OpenAI-compatible provider (for example Meta Model API / Muse Spark).
 *
 * The active Ariana provider remains authoritative. The remote provider acts as
 * a second opinion for analysis, review and consensus synthesis.
 */
object MultiAiRouter {
    const val META_TIMEOUT_MS = 30_000L
    const val PARALLEL_TIMEOUT_MS = 130_000L

    enum class Mode {
        META,
        PARALLEL,
        REVIEW,
        CONSENSUS,
    }

    data class Result(
        val ok: Boolean,
        val mode: Mode,
        val primaryProviderId: String? = null,
        val metaProviderId: String? = null,
        val primaryReply: String? = null,
        val metaReply: String? = null,
        val review: String? = null,
        val consensus: String? = null,
        val judgeProviderId: String? = null,
        val judgeVerdict: MultiAiJudge.Verdict? = null,
        val judgeRationale: String? = null,
        val judgeError: String? = null,
        val error: String? = null,
    )

    private data class RemoteOutcome(
        val ok: Boolean,
        val providerId: String? = null,
        val reply: String? = null,
        val error: String? = null,
    )

    private val executor = Executors.newFixedThreadPool(2) { runnable ->
        Thread(runnable, "ArianaMultiAi").apply { isDaemon = true }
    }

    fun run(context: Context, mode: Mode, rawText: String): Result {
        val text = sanitize(rawText)
        if (text.isEmpty()) return Result(false, mode, error = "INVALID_INPUT")

        return when (mode) {
            Mode.META -> askMeta(context, text)
            Mode.PARALLEL -> parallel(context, text)
            Mode.REVIEW -> review(context, text)
            Mode.CONSENSUS -> consensus(context, text)
        }
    }

    private fun askMeta(context: Context, text: String): Result {
        val meta = callMeta(context, text)
        return Result(
            ok = meta.ok,
            mode = Mode.META,
            metaProviderId = meta.providerId,
            metaReply = meta.reply,
            error = meta.error,
        )
    }

    private fun parallel(context: Context, text: String): Result {
        // If the remote provider is already Ariana's active primary provider,
        // do not query the exact same model twice and pretend it is independent.
        if (remoteProviderIsPrimary(context)) {
            val meta = callMeta(context, text)
            return Result(
                ok = meta.ok,
                mode = Mode.PARALLEL,
                primaryProviderId = DialogueRouter.providerId(),
                metaProviderId = meta.providerId,
                metaReply = meta.reply,
                error = meta.error,
            )
        }

        val primaryFuture = executor.submit<DialogueRouter.Outcome> { DialogueRouter.generate(text) }
        val metaFuture = executor.submit<RemoteOutcome> { callMeta(context, text) }

        val primary = awaitPrimary(primaryFuture)
        val meta = awaitMeta(metaFuture)
        val ok = primary.ok || meta.ok
        return Result(
            ok = ok,
            mode = Mode.PARALLEL,
            primaryProviderId = primary.providerId,
            metaProviderId = meta.providerId,
            primaryReply = primary.reply,
            metaReply = meta.reply,
            error = if (ok) null else mergeErrors(primary.error, meta.error),
        )
    }

    private fun review(context: Context, text: String): Result {
        if (remoteProviderIsPrimary(context)) {
            return Result(
                ok = false,
                mode = Mode.REVIEW,
                primaryProviderId = DialogueRouter.providerId(),
                error = "DISTINCT_PRIMARY_UNAVAILABLE",
            )
        }

        val primary = DialogueRouter.generate(text)
        if (!primary.ok || primary.reply.isNullOrBlank()) {
            return Result(
                ok = false,
                mode = Mode.REVIEW,
                primaryProviderId = primary.providerId,
                error = primary.error ?: "PRIMARY_FAILED",
            )
        }

        val reviewPrompt = buildString {
            append("Du bist der zweite technische Reviewer für Ariana. Prüfe die folgende Antwort kritisch. ")
            append("Nenne konkrete Fehler, Risiken und eine bessere Lösung, falls nötig. Aufgabe: ")
            append(text.take(420))
            append("\nAriana-Antwort: ")
            append(primary.reply.take(620))
        }
        val meta = callMeta(context, reviewPrompt)
        return Result(
            ok = meta.ok,
            mode = Mode.REVIEW,
            primaryProviderId = primary.providerId,
            metaProviderId = meta.providerId,
            primaryReply = primary.reply,
            review = meta.reply,
            error = meta.error,
        )
    }

    private fun consensus(context: Context, text: String): Result {
        val parallel = parallel(context, text)
        if (!parallel.ok) return parallel.copy(mode = Mode.CONSENSUS)

        if (parallel.primaryReply.isNullOrBlank()) {
            return Result(
                ok = true,
                mode = Mode.CONSENSUS,
                primaryProviderId = parallel.primaryProviderId,
                metaProviderId = parallel.metaProviderId,
                metaReply = parallel.metaReply,
                consensus = parallel.metaReply,
            )
        }
        if (parallel.metaReply.isNullOrBlank()) {
            return Result(
                ok = true,
                mode = Mode.CONSENSUS,
                primaryProviderId = parallel.primaryProviderId,
                primaryReply = parallel.primaryReply,
                consensus = parallel.primaryReply,
            )
        }

        val judge = MultiAiJudge.decide(
            task = text,
            primaryReply = parallel.primaryReply,
            metaReply = parallel.metaReply,
        )
        val consensus = judge.finalReply ?: parallel.primaryReply
        return Result(
            ok = consensus.isNotBlank(),
            mode = Mode.CONSENSUS,
            primaryProviderId = parallel.primaryProviderId,
            metaProviderId = parallel.metaProviderId,
            primaryReply = parallel.primaryReply,
            metaReply = parallel.metaReply,
            consensus = consensus,
            judgeProviderId = judge.judgeProviderId,
            judgeVerdict = judge.verdict,
            judgeRationale = judge.rationale,
            judgeError = if (judge.ok) null else judge.error,
            error = if (consensus.isBlank()) judge.error ?: "CONSENSUS_FAILED" else null,
        )
    }

    private fun callMeta(context: Context, text: String): RemoteOutcome {
        val config = SecureAiProviderStore(context.applicationContext).load()
            ?: return RemoteOutcome(false, error = "META_NOT_CONFIGURED")
        val providerId = remoteProviderId(config)
        return try {
            val reply = HttpsDialogueProvider(config).generate(text)
            RemoteOutcome(true, providerId, reply)
        } catch (error: DialogueRouter.ProviderException) {
            RemoteOutcome(false, providerId, error = error.code)
        } catch (_: Exception) {
            RemoteOutcome(false, providerId, error = "META_PROVIDER_FAILED")
        }
    }

    private fun remoteProviderIsPrimary(context: Context): Boolean {
        val config = SecureAiProviderStore(context.applicationContext).load() ?: return false
        return DialogueRouter.providerId() == activeRemoteProviderId(config)
    }

    private fun activeRemoteProviderId(config: SecureAiProviderStore.Config): String {
        val host = runCatching { URI(config.endpoint).host }.getOrNull().orEmpty()
        val safeHost = host.lowercase().replace(Regex("[^a-z0-9.-]"), "-").take(36)
        val safeModel = config.model.lowercase().replace(Regex("[^a-z0-9._-]"), "-").take(28)
        return "https-ai:$safeHost:$safeModel".take(80)
    }

    private fun awaitPrimary(future: java.util.concurrent.Future<DialogueRouter.Outcome>): DialogueRouter.Outcome =
        try {
            future.get(PARALLEL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            future.cancel(true)
            DialogueRouter.Outcome(false, error = "PRIMARY_TIMEOUT")
        } catch (_: ExecutionException) {
            future.cancel(true)
            DialogueRouter.Outcome(false, error = "PRIMARY_FAILED")
        } catch (_: Exception) {
            future.cancel(true)
            DialogueRouter.Outcome(false, error = "PRIMARY_FAILED")
        }

    private fun awaitMeta(future: java.util.concurrent.Future<RemoteOutcome>): RemoteOutcome =
        try {
            future.get(META_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            future.cancel(true)
            RemoteOutcome(false, error = "META_TIMEOUT")
        } catch (error: ExecutionException) {
            future.cancel(true)
            val typed = error.cause as? DialogueRouter.ProviderException
            RemoteOutcome(false, error = typed?.code ?: "META_PROVIDER_FAILED")
        } catch (_: Exception) {
            future.cancel(true)
            RemoteOutcome(false, error = "META_PROVIDER_FAILED")
        }

    private fun sanitize(rawText: String): String = rawText
        .replace(Regex("[\\u0000-\\u001f\\u007f]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(DialogueRouter.MAX_INPUT_CHARS)

    private fun mergeErrors(primary: String?, meta: String?): String =
        listOfNotNull(primary, meta).distinct().joinToString("+").ifBlank { "MULTI_AI_FAILED" }

    private fun remoteProviderId(config: SecureAiProviderStore.Config): String {
        val host = runCatching { URI(config.endpoint).host }.getOrNull().orEmpty()
            .lowercase().replace(Regex("[^a-z0-9.-]"), "-").take(36)
        val model = config.model.lowercase().replace(Regex("[^a-z0-9._-]"), "-").take(28)
        return "meta-ai:$host:$model".take(80)
    }
}

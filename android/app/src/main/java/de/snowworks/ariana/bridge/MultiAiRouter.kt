package de.snowworks.ariana.bridge

import android.content.Context
import java.net.URI
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Coordinates Ariana's active dialogue provider with an optional external reviewer.
 *
 * The active Ariana provider remains authoritative. External providers are optional
 * capability backends and are reached through the provider-neutral adapter boundary.
 */
object MultiAiRouter {
    const val EXTERNAL_REVIEWER_TIMEOUT_MS = 30_000L
    @Deprecated("Use EXTERNAL_REVIEWER_TIMEOUT_MS")
    const val META_TIMEOUT_MS = EXTERNAL_REVIEWER_TIMEOUT_MS
    const val PARALLEL_TIMEOUT_MS = 130_000L

    enum class Mode {
        EXTERNAL_REVIEWER,
        @Deprecated("Use EXTERNAL_REVIEWER")
        META,
        PARALLEL,
        REVIEW,
        CONSENSUS,
        REPAIR,
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
        val repairRounds: Int = 0,
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
            Mode.EXTERNAL_REVIEWER, Mode.META -> askExternalReviewer(context, text)
            Mode.PARALLEL -> parallel(context, text)
            Mode.REVIEW -> review(context, text)
            Mode.CONSENSUS -> consensus(context, text)
            Mode.REPAIR -> repair(context, text)
        }
    }

    private fun askExternalReviewer(context: Context, text: String): Result {
        val reviewer = callExternalReviewer(context, text)
        return Result(
            ok = reviewer.ok,
            mode = Mode.EXTERNAL_REVIEWER,
            metaProviderId = reviewer.providerId,
            metaReply = reviewer.reply,
            error = reviewer.error,
        )
    }

    private fun parallel(context: Context, text: String): Result {
        if (remoteProviderIsPrimary(context)) {
            val reviewer = callExternalReviewer(context, text)
            return Result(
                ok = reviewer.ok,
                mode = Mode.PARALLEL,
                primaryProviderId = DialogueRouter.providerId(),
                metaProviderId = reviewer.providerId,
                metaReply = reviewer.reply,
                error = reviewer.error,
            )
        }

        val primaryFuture = executor.submit<DialogueRouter.Outcome> { DialogueRouter.generate(text) }
        val reviewerFuture = executor.submit<RemoteOutcome> { callExternalReviewer(context, text) }

        val primary = awaitPrimary(primaryFuture)
        val reviewer = awaitExternalReviewer(reviewerFuture)
        val ok = primary.ok || reviewer.ok
        return Result(
            ok = ok,
            mode = Mode.PARALLEL,
            primaryProviderId = primary.providerId,
            metaProviderId = reviewer.providerId,
            primaryReply = primary.reply,
            metaReply = reviewer.reply,
            error = if (ok) null else mergeErrors(primary.error, reviewer.error),
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
            append("Du bist der zweite technische Reviewer fuer Ariana. Pruefe die folgende Antwort kritisch. ")
            append("Nenne konkrete Fehler, Risiken und eine bessere Loesung, falls noetig. Aufgabe: ")
            append(text.take(420))
            append("\nAriana-Antwort: ")
            append(primary.reply.take(620))
        }
        val reviewer = callExternalReviewer(context, reviewPrompt)
        return Result(
            ok = reviewer.ok,
            mode = Mode.REVIEW,
            primaryProviderId = primary.providerId,
            metaProviderId = reviewer.providerId,
            primaryReply = primary.reply,
            review = reviewer.reply,
            error = reviewer.error,
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

    private fun repair(context: Context, text: String): Result {
        if (remoteProviderIsPrimary(context)) {
            return Result(
                ok = false,
                mode = Mode.REPAIR,
                primaryProviderId = DialogueRouter.providerId(),
                error = "DISTINCT_PRIMARY_UNAVAILABLE",
            )
        }

        val initial = DialogueRouter.generate(text)
        val initialReply = initial.reply?.takeIf { it.isNotBlank() }
        if (!initial.ok || initialReply == null) {
            return Result(
                ok = false,
                mode = Mode.REPAIR,
                primaryProviderId = initial.providerId,
                error = initial.error ?: "PRIMARY_FAILED",
            )
        }

        var currentReply: String = initialReply
        var latestReview: String? = null
        var latestMetaProviderId: String? = null

        for (round in 1..MultiAiRepairLoop.MAX_ROUNDS) {
            val reviewPrompt = MultiAiRepairLoop.buildReviewPrompt(
                task = text,
                draft = currentReply,
                round = round,
            )
            val reviewer = callExternalReviewer(context, reviewPrompt)
            val reviewerReply = reviewer.reply?.takeIf { it.isNotBlank() }
            latestMetaProviderId = reviewer.providerId
            latestReview = reviewerReply

            if (!reviewer.ok || reviewerReply == null) {
                return Result(
                    ok = true,
                    mode = Mode.REPAIR,
                    primaryProviderId = initial.providerId,
                    metaProviderId = latestMetaProviderId,
                    primaryReply = currentReply,
                    review = "REVIEW_UNAVAILABLE:${reviewer.error ?: "EXTERNAL_REVIEWER_FAILED"}",
                    consensus = currentReply,
                    repairRounds = round,
                )
            }

            val parsed = MultiAiRepairLoop.parseReview(reviewerReply)
                ?: return Result(
                    ok = true,
                    mode = Mode.REPAIR,
                    primaryProviderId = initial.providerId,
                    metaProviderId = latestMetaProviderId,
                    primaryReply = currentReply,
                    metaReply = reviewerReply,
                    review = "REVIEW_FORMAT_INVALID",
                    consensus = currentReply,
                    repairRounds = round,
                )

            if (parsed.status == MultiAiRepairLoop.Status.PASS) {
                return Result(
                    ok = true,
                    mode = Mode.REPAIR,
                    primaryProviderId = initial.providerId,
                    metaProviderId = latestMetaProviderId,
                    primaryReply = currentReply,
                    metaReply = reviewerReply,
                    review = reviewerReply,
                    consensus = currentReply,
                    repairRounds = round,
                )
            }

            val revisionPrompt = MultiAiRepairLoop.buildRevisionPrompt(
                task = text,
                draft = currentReply,
                review = parsed,
                round = round,
            )
            val revised = DialogueRouter.generate(revisionPrompt)
            val revisedReply = revised.reply?.takeIf { it.isNotBlank() }
            if (!revised.ok || revisedReply == null) {
                val fallback = parsed.candidate ?: currentReply
                return Result(
                    ok = fallback.isNotBlank(),
                    mode = Mode.REPAIR,
                    primaryProviderId = initial.providerId,
                    metaProviderId = latestMetaProviderId,
                    primaryReply = currentReply,
                    metaReply = parsed.candidate,
                    review = reviewerReply,
                    consensus = fallback,
                    repairRounds = round,
                    error = if (fallback.isBlank()) revised.error ?: "REPAIR_FAILED" else null,
                )
            }

            currentReply = revisedReply

            if (round == MultiAiRepairLoop.MAX_ROUNDS) {
                val reviewerCandidate = parsed.candidate
                if (!reviewerCandidate.isNullOrBlank()) {
                    val judge = MultiAiJudge.decide(
                        task = text,
                        primaryReply = currentReply,
                        metaReply = reviewerCandidate,
                    )
                    val finalReply = judge.finalReply ?: currentReply
                    return Result(
                        ok = finalReply.isNotBlank(),
                        mode = Mode.REPAIR,
                        primaryProviderId = revised.providerId ?: initial.providerId,
                        metaProviderId = latestMetaProviderId,
                        primaryReply = currentReply,
                        metaReply = reviewerCandidate,
                        review = latestReview,
                        consensus = finalReply,
                        judgeProviderId = judge.judgeProviderId,
                        judgeVerdict = judge.verdict,
                        judgeRationale = judge.rationale,
                        judgeError = if (judge.ok) null else judge.error,
                        repairRounds = round,
                        error = if (finalReply.isBlank()) judge.error ?: "REPAIR_FAILED" else null,
                    )
                }
            }
        }

        return Result(
            ok = currentReply.isNotBlank(),
            mode = Mode.REPAIR,
            primaryProviderId = initial.providerId,
            metaProviderId = latestMetaProviderId,
            primaryReply = currentReply,
            review = latestReview,
            consensus = currentReply,
            repairRounds = MultiAiRepairLoop.MAX_ROUNDS,
            error = if (currentReply.isBlank()) "REPAIR_FAILED" else null,
        )
    }

    private fun callExternalReviewer(context: Context, text: String): RemoteOutcome {
        val config = SecureAiProviderStore(context.applicationContext).load()
            ?: return RemoteOutcome(false, error = "EXTERNAL_REVIEWER_NOT_CONFIGURED")
        val provider = externalReviewerAdapter(config)
        val providerId = provider.id
        if (!AiProviderHealth.acquireAttempt(providerId)) {
            return RemoteOutcome(false, providerId, error = "EXTERNAL_REVIEWER_CIRCUIT_OPEN")
        }

        return try {
            val reply = provider.generate(text)
            AiProviderHealth.recordSuccess(providerId)
            RemoteOutcome(true, providerId, reply)
        } catch (error: DialogueRouter.ProviderException) {
            AiProviderHealth.recordFailure(providerId, error.code)
            RemoteOutcome(false, providerId, error = error.code)
        } catch (_: Exception) {
            AiProviderHealth.recordFailure(providerId, "EXTERNAL_REVIEWER_PROVIDER_FAILED")
            RemoteOutcome(false, providerId, error = "EXTERNAL_REVIEWER_PROVIDER_FAILED")
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

    private fun awaitExternalReviewer(future: java.util.concurrent.Future<RemoteOutcome>): RemoteOutcome =
        try {
            future.get(EXTERNAL_REVIEWER_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            future.cancel(true)
            RemoteOutcome(false, error = "EXTERNAL_REVIEWER_TIMEOUT")
        } catch (error: ExecutionException) {
            future.cancel(true)
            val typed = error.cause as? DialogueRouter.ProviderException
            RemoteOutcome(false, error = typed?.code ?: "EXTERNAL_REVIEWER_PROVIDER_FAILED")
        } catch (_: Exception) {
            future.cancel(true)
            RemoteOutcome(false, error = "META_PROVIDER_FAILED")
        }

    private fun sanitize(rawText: String): String = rawText
        .replace(Regex("[\\u0000-\\u001f\\u007f]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(DialogueRouter.MAX_INPUT_CHARS)

    private fun mergeErrors(primary: String?, reviewer: String?): String =
        listOfNotNull(primary, reviewer).distinct().joinToString("+").ifBlank { "MULTI_AI_FAILED" }

    fun externalReviewerProviderId(config: SecureAiProviderStore.Config): String {
        val host = runCatching { URI(config.endpoint).host }.getOrNull().orEmpty()
            .lowercase().replace(Regex("[^a-z0-9.-]"), "-").take(36)
        val model = config.model.lowercase().replace(Regex("[^a-z0-9._-]"), "-").take(28)
        return "https-ai:$host:$model".take(80)
    }
}

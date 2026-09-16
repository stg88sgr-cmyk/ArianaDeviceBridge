package de.snowworks.ariana.bridge

import android.content.Context
import java.net.URI
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

object MultiAiRouter {
    const val META_TIMEOUT_MS = 30_000L
    const val PARALLEL_TIMEOUT_MS = 130_000L

    enum class Mode { META, PARALLEL, REVIEW, CONSENSUS, REPAIR }

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

    private data class RemoteOutcome(val ok: Boolean, val providerId: String? = null, val reply: String? = null, val error: String? = null)
    private val executor = Executors.newFixedThreadPool(2) { runnable -> Thread(runnable, "ArianaMultiAi").apply { isDaemon = true } }

    fun run(context: Context, mode: Mode, rawText: String): Result {
        val text = sanitize(rawText)
        if (text.isEmpty()) return Result(false, mode, error = "INVALID_INPUT")
        return when (mode) {
            Mode.META -> askMeta(context, text)
            Mode.PARALLEL -> parallel(context, text)
            Mode.REVIEW -> review(context, text)
            Mode.CONSENSUS -> consensus(context, text)
            Mode.REPAIR -> repair(context, text)
        }
    }

    private fun askMeta(context: Context, text: String): Result {
        val meta = callMeta(context, text)
        return Result(meta.ok, Mode.META, metaProviderId = meta.providerId, metaReply = meta.reply, error = meta.error)
    }

    private fun parallel(context: Context, text: String): Result {
        if (remoteProviderIsPrimary(context)) {
            val meta = callMeta(context, text)
            return Result(meta.ok, Mode.PARALLEL, primaryProviderId = DialogueRouter.providerId(), metaProviderId = meta.providerId, metaReply = meta.reply, error = meta.error)
        }
        val primaryFuture = executor.submit<DialogueRouter.Outcome> { DialogueRouter.generate(text) }
        val metaFuture = executor.submit<RemoteOutcome> { callMeta(context, text) }
        val primary = awaitPrimary(primaryFuture)
        val meta = awaitMeta(metaFuture)
        val ok = primary.ok || meta.ok
        return Result(ok, Mode.PARALLEL, primary.providerId, meta.providerId, primary.reply, meta.reply, error = if (ok) null else mergeErrors(primary.error, meta.error))
    }

    private fun review(context: Context, text: String): Result {
        if (remoteProviderIsPrimary(context)) return Result(false, Mode.REVIEW, primaryProviderId = DialogueRouter.providerId(), error = "DISTINCT_PRIMARY_UNAVAILABLE")
        val primary = DialogueRouter.generate(text)
        if (!primary.ok || primary.reply.isNullOrBlank()) return Result(false, Mode.REVIEW, primaryProviderId = primary.providerId, error = primary.error ?: "PRIMARY_FAILED")
        val reviewPrompt = "Du bist der zweite technische Reviewer fuer Ariana. Pruefe die folgende Antwort kritisch. Nenne konkrete Fehler, Risiken und eine bessere Loesung, falls noetig. Aufgabe: ${text.take(420)} Ariana-Antwort: ${primary.reply.take(620)}"
        val meta = callMeta(context, reviewPrompt)
        return Result(meta.ok, Mode.REVIEW, primary.providerId, meta.providerId, primary.reply, review = meta.reply, error = meta.error)
    }

    private fun consensus(context: Context, text: String): Result {
        val parallel = parallel(context, text)
        if (!parallel.ok) return parallel.copy(mode = Mode.CONSENSUS)
        if (parallel.primaryReply.isNullOrBlank()) return Result(true, Mode.CONSENSUS, parallel.primaryProviderId, parallel.metaProviderId, metaReply = parallel.metaReply, consensus = parallel.metaReply)
        if (parallel.metaReply.isNullOrBlank()) return Result(true, Mode.CONSENSUS, parallel.primaryProviderId, primaryReply = parallel.primaryReply, consensus = parallel.primaryReply)
        val judge = MultiAiJudge.decide(text, parallel.primaryReply, parallel.metaReply)
        val finalReply = judge.finalReply ?: parallel.primaryReply
        return Result(finalReply.isNotBlank(), Mode.CONSENSUS, parallel.primaryProviderId, parallel.metaProviderId, parallel.primaryReply, parallel.metaReply, consensus = finalReply, judgeProviderId = judge.judgeProviderId, judgeVerdict = judge.verdict, judgeRationale = judge.rationale, judgeError = if (judge.ok) null else judge.error, error = if (finalReply.isBlank()) judge.error ?: "CONSENSUS_FAILED" else null)
    }

    private fun repair(context: Context, text: String): Result {
        if (remoteProviderIsPrimary(context)) return Result(false, Mode.REPAIR, primaryProviderId = DialogueRouter.providerId(), error = "DISTINCT_PRIMARY_UNAVAILABLE")
        val initial = DialogueRouter.generate(text)
        val initialReply = initial.reply?.takeIf { it.isNotBlank() }
        if (!initial.ok || initialReply == null) return Result(false, Mode.REPAIR, primaryProviderId = initial.providerId, error = initial.error ?: "PRIMARY_FAILED")
        var currentReply = initialReply
        var latestReview: String? = null
        var latestMetaProviderId: String? = null
        for (round in 1..MultiAiRepairLoop.MAX_ROUNDS) {
            val meta = callMeta(context, MultiAiRepairLoop.buildReviewPrompt(text, currentReply, round))
            val metaReply = meta.reply?.takeIf { it.isNotBlank() }
            latestMetaProviderId = meta.providerId
            latestReview = metaReply
            if (!meta.ok || metaReply == null) return Result(true, Mode.REPAIR, initial.providerId, latestMetaProviderId, currentReply, review = "REVIEW_UNAVAILABLE:${meta.error ?: "META_FAILED"}", consensus = currentReply, repairRounds = round)
            val parsed = MultiAiRepairLoop.parseReview(metaReply) ?: return Result(true, Mode.REPAIR, initial.providerId, latestMetaProviderId, currentReply, metaReply, "REVIEW_FORMAT_INVALID", currentReply, repairRounds = round)
            if (parsed.status == MultiAiRepairLoop.Status.PASS) return Result(true, Mode.REPAIR, initial.providerId, latestMetaProviderId, currentReply, metaReply, metaReply, currentReply, repairRounds = round)
            val revised = DialogueRouter.generate(MultiAiRepairLoop.buildRevisionPrompt(text, currentReply, parsed, round))
            val revisedReply = revised.reply?.takeIf { it.isNotBlank() }
            if (!revised.ok || revisedReply == null) {
                val fallback = parsed.candidate ?: currentReply
                return Result(fallback.isNotBlank(), Mode.REPAIR, initial.providerId, latestMetaProviderId, currentReply, parsed.candidate, metaReply, fallback, repairRounds = round, error = if (fallback.isBlank()) revised.error ?: "REPAIR_FAILED" else null)
            }
            currentReply = revisedReply
            if (round == MultiAiRepairLoop.MAX_ROUNDS && !parsed.candidate.isNullOrBlank()) {
                val judge = MultiAiJudge.decide(text, currentReply, parsed.candidate)
                val finalReply = judge.finalReply ?: currentReply
                return Result(finalReply.isNotBlank(), Mode.REPAIR, revised.providerId ?: initial.providerId, latestMetaProviderId, currentReply, parsed.candidate, latestReview, finalReply, judge.judgeProviderId, judge.verdict, judge.rationale, if (judge.ok) null else judge.error, round, if (finalReply.isBlank()) judge.error ?: "REPAIR_FAILED" else null)
            }
        }
        return Result(currentReply.isNotBlank(), Mode.REPAIR, initial.providerId, latestMetaProviderId, currentReply, review = latestReview, consensus = currentReply, repairRounds = MultiAiRepairLoop.MAX_ROUNDS, error = if (currentReply.isBlank()) "REPAIR_FAILED" else null)
    }

    private fun callMeta(context: Context, text: String): RemoteOutcome {
        val config = SecureAiProviderStore(context.applicationContext).load() ?: return RemoteOutcome(false, error = "META_NOT_CONFIGURED")
        val providerId = remoteProviderId(config)
        if (!AiProviderHealth.acquireAttempt(providerId)) return RemoteOutcome(false, providerId, error = "META_CIRCUIT_OPEN")
        return try {
            val reply = HttpsDialogueProvider(config).generate(text)
            AiProviderHealth.recordSuccess(providerId)
            RemoteOutcome(true, providerId, reply)
        } catch (error: DialogueRouter.ProviderException) {
            AiProviderHealth.recordFailure(providerId, error.code)
            RemoteOutcome(false, providerId, error = error.code)
        } catch (_: Exception) {
            AiProviderHealth.recordFailure(providerId, "META_PROVIDER_FAILED")
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

    private fun awaitPrimary(future: java.util.concurrent.Future<DialogueRouter.Outcome>) = try { future.get(PARALLEL_TIMEOUT_MS, TimeUnit.MILLISECONDS) } catch (_: TimeoutException) { future.cancel(true); DialogueRouter.Outcome(false, error = "PRIMARY_TIMEOUT") } catch (_: ExecutionException) { future.cancel(true); DialogueRouter.Outcome(false, error = "PRIMARY_FAILED") } catch (_: Exception) { future.cancel(true); DialogueRouter.Outcome(false, error = "PRIMARY_FAILED") }
    private fun awaitMeta(future: java.util.concurrent.Future<RemoteOutcome>) = try { future.get(META_TIMEOUT_MS, TimeUnit.MILLISECONDS) } catch (_: TimeoutException) { future.cancel(true); RemoteOutcome(false, error = "META_TIMEOUT") } catch (error: ExecutionException) { future.cancel(true); RemoteOutcome(false, error = (error.cause as? DialogueRouter.ProviderException)?.code ?: "META_PROVIDER_FAILED") } catch (_: Exception) { future.cancel(true); RemoteOutcome(false, error = "META_PROVIDER_FAILED") }
    private fun sanitize(rawText: String) = rawText.replace(Regex("[\\u0000-\\u001f\\u007f]+"), " ").replace(Regex("\\s+"), " ").trim().take(DialogueRouter.MAX_INPUT_CHARS)
    private fun mergeErrors(primary: String?, meta: String?) = listOfNotNull(primary, meta).distinct().joinToString("+").ifBlank { "MULTI_AI_FAILED" }
    private fun remoteProviderId(config: SecureAiProviderStore.Config): String {
        val host = runCatching { URI(config.endpoint).host }.getOrNull().orEmpty().lowercase().replace(Regex("[^a-z0-9.-]"), "-").take(36)
        val model = config.model.lowercase().replace(Regex("[^a-z0-9._-]"), "-").take(28)
        return "meta-ai:$host:$model".take(80)
    }
}

package de.snowworks.ariana.gi

import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.exp

enum class GiMode { FAST, BALANCED, DEEP, CREATIVE, CRITICAL, RECOVERY }
enum class CognitiveRoute { KNOWLEDGE, REASONING, PLANNING, CREATIVITY, MEMORY, REFLECTION, EXECUTION, SAFETY }
enum class GiGateResult { SAFE, CONFIRM, BLOCKED }
enum class GiOutcomeStatus { SUCCESS, PARTIAL_SUCCESS, FAILURE, CANCELLED, UNKNOWN }

data class GiState(
    val active: Boolean = true,
    val mode: GiMode = GiMode.BALANCED,
    val confidence: Float = 0f,
    val uncertainty: Float = 1f,
    val contradictionScore: Float = 0f,
    val selectedRoute: String? = null,
    val activeGoal: String? = null,
    val cycle: Long = 0L,
)

data class ThoughtCandidate(
    val id: String = UUID.randomUUID().toString(),
    val route: String,
    val proposal: String,
    val confidence: Float,
    val risk: Float,
    val goalAlignment: Float,
    val evidenceScore: Float,
    val contextContinuity: Float = 0.5f,
)

data class GiInput(
    val goal: String,
    val context: String = "",
    val candidates: List<ThoughtCandidate>,
)

data class GiDecision(
    val candidate: ThoughtCandidate,
    val finalScore: Float,
    val contradictionScore: Float,
    val cycle: Long,
)

data class GiActionProposal(
    val actionType: String,
    val reason: String,
    val confidence: Float,
    val risk: Float,
    val parameters: Map<String, String> = emptyMap(),
    val cycle: Long,
)

data class GiOutcome(
    val cycle: Long,
    val actionType: String?,
    val route: String,
    val status: GiOutcomeStatus,
    val score: Float,
    val latencyMs: Long? = null,
    val errorCode: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
)

data class GiAuditEvent(
    val timestamp: Long = System.currentTimeMillis(),
    val cycle: Long,
    val goal: String,
    val mode: GiMode,
    val route: CognitiveRoute,
    val selectedCandidate: String?,
    val confidence: Float,
    val contradiction: Float,
    val actionType: String? = null,
    val gateResult: GiGateResult? = null,
    val result: String? = null,
)

object DecisionFusion {
    fun score(candidate: ThoughtCandidate): Float {
        val goal = candidate.goalAlignment.coerceIn(0f, 1f)
        val evidence = candidate.evidenceScore.coerceIn(0f, 1f)
        val confidence = candidate.confidence.coerceIn(0f, 1f)
        val continuity = candidate.contextContinuity.coerceIn(0f, 1f)
        val risk = candidate.risk.coerceIn(0f, 1f)
        return (
            goal * 0.35f +
                evidence * 0.25f +
                confidence * 0.20f +
                continuity * 0.10f +
                (1f - risk) * 0.10f
            ).coerceIn(0f, 1f)
    }

    fun select(candidates: List<ThoughtCandidate>): Pair<ThoughtCandidate, Float>? =
        candidates.maxByOrNull(::score)?.let { it to score(it) }
}

object ContradictionDetector {
    fun evaluate(candidates: List<ThoughtCandidate>): Float {
        if (candidates.size < 2) return 0f
        val confidence = candidates.map { it.confidence.coerceIn(0f, 1f) }
        val risk = candidates.map { it.risk.coerceIn(0f, 1f) }
        val confidenceSpread = (confidence.maxOrNull() ?: 0f) - (confidence.minOrNull() ?: 0f)
        val riskSpread = (risk.maxOrNull() ?: 0f) - (risk.minOrNull() ?: 0f)
        return (confidenceSpread * 0.6f + riskSpread * 0.4f).coerceIn(0f, 1f)
    }
}

object GoalAlignmentEngine {
    fun acceptable(candidate: ThoughtCandidate, minimum: Float = 0.45f): Boolean =
        candidate.goalAlignment.coerceIn(0f, 1f) >= minimum.coerceIn(0f, 1f)
}

object CognitiveRouter {
    fun selectMode(input: GiInput, currentState: GiState): GiMode {
        if (input.candidates.isEmpty()) return GiMode.RECOVERY
        val contradiction = ContradictionDetector.evaluate(input.candidates)
        val averageRisk = input.candidates.map { it.risk.coerceIn(0f, 1f) }.average().toFloat()
        val averageConfidence = input.candidates.map { it.confidence.coerceIn(0f, 1f) }.average().toFloat()
        return when {
            contradiction > 0.70f -> GiMode.CRITICAL
            averageRisk > 0.65f -> GiMode.CRITICAL
            averageConfidence < 0.30f -> GiMode.RECOVERY
            input.candidates.size >= 5 -> GiMode.DEEP
            currentState.uncertainty > 0.60f -> GiMode.DEEP
            else -> GiMode.BALANCED
        }
    }
}

object ReflectionEngine {
    data class Result(
        val accepted: Boolean,
        val shouldRetry: Boolean,
        val reason: String,
    )

    fun reflect(decision: GiDecision): Result {
        val score = decision.finalScore.coerceIn(0f, 1f)
        val contradiction = decision.contradictionScore.coerceIn(0f, 1f)
        return when {
            contradiction > 0.75f -> Result(false, true, "HIGH_CONTRADICTION")
            score < 0.40f -> Result(false, true, "LOW_CONFIDENCE")
            else -> Result(true, false, "ACCEPTED")
        }
    }
}

class MetaOrchestrator {
    private var state = GiState()

    @Synchronized
    fun getState(): GiState = state

    @Synchronized
    fun setMode(mode: GiMode) {
        state = state.copy(mode = mode)
    }

    @Synchronized
    fun process(input: GiInput): GiDecision? {
        if (!state.active) return null
        val selected = DecisionFusion.select(input.candidates) ?: return null
        val contradiction = ContradictionDetector.evaluate(input.candidates)
        val nextCycle = state.cycle + 1
        state = state.copy(
            confidence = selected.second,
            uncertainty = 1f - selected.second,
            contradictionScore = contradiction,
            selectedRoute = selected.first.route,
            activeGoal = input.goal,
            cycle = nextCycle,
        )
        return GiDecision(selected.first, selected.second, contradiction, nextCycle)
    }
}

class GiCycleEngine {
    data class Result(
        val route: CognitiveRoute,
        val decision: GiDecision?,
        val reflection: ReflectionEngine.Result?,
        val state: GiState,
    )

    private val orchestrator = MetaOrchestrator()

    fun run(input: GiInput): Result {
        val mode = CognitiveRouter.selectMode(input, orchestrator.getState())
        orchestrator.setMode(mode)

        val valid = input.candidates.filter { GoalAlignmentEngine.acceptable(it) }
        if (valid.isEmpty()) {
            orchestrator.setMode(GiMode.RECOVERY)
            return Result(CognitiveRoute.REFLECTION, null, null, orchestrator.getState())
        }

        val decision = orchestrator.process(input.copy(candidates = valid))
            ?: return Result(CognitiveRoute.REFLECTION, null, null, orchestrator.getState())
        val reflection = ReflectionEngine.reflect(decision)
        val route = when {
            reflection.shouldRetry -> CognitiveRoute.REFLECTION
            decision.candidate.risk.coerceIn(0f, 1f) > 0.65f -> CognitiveRoute.SAFETY
            mode == GiMode.CRITICAL -> CognitiveRoute.REASONING
            mode == GiMode.DEEP -> CognitiveRoute.PLANNING
            mode == GiMode.CREATIVE -> CognitiveRoute.CREATIVITY
            mode == GiMode.RECOVERY -> CognitiveRoute.REFLECTION
            else -> CognitiveRoute.EXECUTION
        }
        return Result(route, decision, reflection, orchestrator.getState())
    }
}

class DivineIntelligenceEngine {
    private val cycle = GiCycleEngine()

    fun think(
        goal: String,
        context: String = "",
        candidates: List<ThoughtCandidate>,
    ): GiCycleEngine.Result = cycle.run(GiInput(goal, context, candidates))
}

fun interface GiExecutionGate {
    fun evaluate(proposal: GiActionProposal): GiGateResult
}

object GiBridgePolicyGate : GiExecutionGate {
    private val safe = setOf(
        "get_device_status",
        "get_permission_status",
        "get_active_sessions",
        "notification_list",
        "notification_clear",
        "camera_snapshot",
        "camera_stop",
        "microphone_stop",
        "screen_stop",
        "stop_all",
    )
    private val confirm = setOf("camera_start", "microphone_start", "screen_start")

    override fun evaluate(proposal: GiActionProposal): GiGateResult = when (proposal.actionType) {
        in safe -> GiGateResult.SAFE
        in confirm -> GiGateResult.CONFIRM
        else -> GiGateResult.BLOCKED
    }
}

object GiActionBuilder {
    fun fromDecision(decision: GiDecision): GiActionProposal? {
        val action = when (decision.candidate.route.uppercase()) {
            "CAMERA_START" -> "camera_start"
            "CAMERA_STOP" -> "camera_stop"
            "CAMERA_SNAPSHOT" -> "camera_snapshot"
            "MICROPHONE_START" -> "microphone_start"
            "MICROPHONE_STOP" -> "microphone_stop"
            "SCREEN_START", "SCREEN_SHARE_START" -> "screen_start"
            "SCREEN_STOP", "SCREEN_SHARE_STOP" -> "screen_stop"
            "STOP_ALL" -> "stop_all"
            else -> return null
        }
        return GiActionProposal(
            actionType = action,
            reason = decision.candidate.proposal,
            confidence = decision.finalScore,
            risk = decision.candidate.risk.coerceIn(0f, 1f),
            cycle = decision.cycle,
        )
    }
}

object GiSafetyInvariant {
    fun canChange(original: GiGateResult, requested: GiGateResult): Boolean = when (original) {
        GiGateResult.BLOCKED -> requested == GiGateResult.BLOCKED
        GiGateResult.CONFIRM -> requested != GiGateResult.SAFE
        GiGateResult.SAFE -> true
    }
}

sealed interface GiRuntimeResult {
    data object NoDecision : GiRuntimeResult
    data class CognitiveDecision(val decision: GiDecision) : GiRuntimeResult
    data class ActionProposal(val proposal: GiActionProposal, val gateResult: GiGateResult) : GiRuntimeResult
}

class GiAuditLogger(private val maxEntries: Int = 1_000) {
    private val events = ArrayDeque<GiAuditEvent>()

    @Synchronized
    fun log(event: GiAuditEvent) {
        while (events.size >= maxEntries) events.removeFirst()
        events.add(event)
    }

    @Synchronized
    fun latest(count: Int = 20): List<GiAuditEvent> = events.toList().takeLast(count.coerceAtLeast(0))
}

class GiRuntime(
    private val gate: GiExecutionGate = GiBridgePolicyGate,
    private val audit: GiAuditLogger = GiAuditLogger(),
) {
    private val engine = DivineIntelligenceEngine()

    fun process(
        goal: String,
        context: String = "",
        candidates: List<ThoughtCandidate>,
    ): GiRuntimeResult {
        val cycle = engine.think(goal, context, candidates)
        val decision = cycle.decision
        if (decision == null) {
            audit.log(
                GiAuditEvent(
                    cycle = cycle.state.cycle,
                    goal = goal,
                    mode = cycle.state.mode,
                    route = cycle.route,
                    selectedCandidate = null,
                    confidence = 0f,
                    contradiction = cycle.state.contradictionScore,
                    result = "NO_DECISION",
                ),
            )
            return GiRuntimeResult.NoDecision
        }

        val proposal = GiActionBuilder.fromDecision(decision)
        if (proposal == null) {
            audit.log(
                GiAuditEvent(
                    cycle = decision.cycle,
                    goal = goal,
                    mode = cycle.state.mode,
                    route = cycle.route,
                    selectedCandidate = decision.candidate.proposal,
                    confidence = decision.finalScore,
                    contradiction = decision.contradictionScore,
                    result = "COGNITIVE_ONLY",
                ),
            )
            return GiRuntimeResult.CognitiveDecision(decision)
        }

        val gateResult = gate.evaluate(proposal)
        audit.log(
            GiAuditEvent(
                cycle = decision.cycle,
                goal = goal,
                mode = cycle.state.mode,
                route = cycle.route,
                selectedCandidate = decision.candidate.proposal,
                confidence = decision.finalScore,
                contradiction = decision.contradictionScore,
                actionType = proposal.actionType,
                gateResult = gateResult,
                result = "PROPOSAL_READY",
            ),
        )
        return GiRuntimeResult.ActionProposal(proposal, gateResult)
    }

    fun auditLog(count: Int = 20): List<GiAuditEvent> = audit.latest(count)
}

data class GiExperience(
    val key: String,
    val route: String,
    val actionType: String?,
    val attempts: Int,
    val successes: Int,
    val failures: Int,
    val averageOutcome: Float,
    val lastOutcome: Float,
    val lastUsedAt: Long,
)

interface GiExperienceStore {
    fun get(key: String, route: String): GiExperience?
    fun save(experience: GiExperience)
    fun allFor(key: String): List<GiExperience>
    fun clear()
}

class SynchronizedGiExperienceStore(private val delegate: GiExperienceStore) : GiExperienceStore {
    private val lock = ReentrantLock()
    override fun get(key: String, route: String): GiExperience? = lock.withLock { delegate.get(key, route) }
    override fun save(experience: GiExperience) = lock.withLock { delegate.save(experience) }
    override fun allFor(key: String): List<GiExperience> = lock.withLock { delegate.allFor(key) }
    override fun clear() = lock.withLock { delegate.clear() }
}

class GiExperienceEngine(private val store: GiExperienceStore) {
    fun record(key: String, decision: GiDecision, actionType: String?, outcome: GiOutcome) {
        val route = decision.candidate.route
        val old = store.get(key, route)
        val attempts = (old?.attempts ?: 0) + 1
        val score = outcome.score.coerceIn(0f, 1f)
        val average = if (old == null) score else ((old.averageOutcome * old.attempts) + score) / attempts
        store.save(
            GiExperience(
                key = key,
                route = route,
                actionType = actionType,
                attempts = attempts,
                successes = (old?.successes ?: 0) + if (outcome.status == GiOutcomeStatus.SUCCESS) 1 else 0,
                failures = (old?.failures ?: 0) + if (outcome.status == GiOutcomeStatus.FAILURE) 1 else 0,
                averageOutcome = average.coerceIn(0f, 1f),
                lastOutcome = score,
                lastUsedAt = System.currentTimeMillis(),
            ),
        )
    }
}

object ExperienceWeightedScoring {
    private const val DAY_MS = 86_400_000.0

    fun adjustment(experience: GiExperience?, now: Long = System.currentTimeMillis()): Float {
        if (experience == null) return 0f
        val sampleConfidence = (experience.attempts / 10f).coerceIn(0f, 1f)
        val quality = (experience.averageOutcome.coerceIn(0f, 1f) - 0.5f) * 0.20f
        val ageDays = (now - experience.lastUsedAt).coerceAtLeast(0L).toDouble() / DAY_MS
        val decay = exp(-ageDays / 60.0).toFloat().coerceIn(0f, 1f)
        return (quality * sampleConfidence * decay).coerceIn(-0.10f, 0.10f)
    }
}

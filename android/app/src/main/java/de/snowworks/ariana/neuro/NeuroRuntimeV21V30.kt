package de.snowworks.ariana.neuro

import java.util.ArrayDeque
import java.util.LinkedHashMap
import java.util.concurrent.atomic.AtomicLong

/** V21 - deterministic attention scoring without executing actions. */
class V21AttentionRouter : NeuroModule {
    override val id: String = "v21-attention-router"
    override val inputs: Set<NeuroChannel> = setOf(
        NeuroChannel.USER_INTENT,
        NeuroChannel.PERCEPTION,
        NeuroChannel.DEVICE_STATE,
        NeuroChannel.SAFETY,
    )
    override val outputs: Set<NeuroChannel> = setOf(NeuroChannel.ATTENTION)

    override suspend fun onSignal(signal: NeuroSignal): List<NeuroSignal> {
        val defaultPriority = when (signal.channel) {
            NeuroChannel.SAFETY -> 100
            NeuroChannel.USER_INTENT -> 80
            NeuroChannel.PERCEPTION -> 50
            NeuroChannel.DEVICE_STATE -> 40
            else -> 10
        }
        val priority = signal.payload["priority"]?.toIntOrNull()?.coerceIn(0, 100) ?: defaultPriority
        return listOf(
            NeuroSignal(
                channel = NeuroChannel.ATTENTION,
                source = id,
                payload = signal.payload + mapOf(
                    "originChannel" to signal.channel.name,
                    "priority" to priority.toString(),
                ),
            ),
        )
    }
}

/** V22 - bounded latest-context fusion. Raw values never persist unboundedly. */
class V22ContextFusion : NeuroModule {
    override val id: String = "v22-context-fusion"
    override val inputs: Set<NeuroChannel> = setOf(
        NeuroChannel.ATTENTION,
        NeuroChannel.MEMORY,
        NeuroChannel.EMOTION_STATE,
        NeuroChannel.DEVICE_STATE,
    )
    override val outputs: Set<NeuroChannel> = setOf(NeuroChannel.CONTEXT)

    private val latest = LinkedHashMap<NeuroChannel, Map<String, String>>()

    override suspend fun onSignal(signal: NeuroSignal): List<NeuroSignal> {
        val snapshot = synchronized(latest) {
            latest[signal.channel] = signal.payload.toMap()
            latest.toMap()
        }

        val merged = LinkedHashMap<String, String>()
        merged.putAll(signal.payload)
        snapshot.forEach { (channel, values) ->
            values.forEach { (key, value) ->
                merged.putIfAbsent("${channel.name.lowercase()}.$key", value)
            }
        }
        merged["contextSources"] = snapshot.keys.joinToString(",") { it.name }

        return listOf(
            NeuroSignal(
                channel = NeuroChannel.CONTEXT,
                source = id,
                payload = merged,
            ),
        )
    }

    fun sourceCount(): Int = synchronized(latest) { latest.size }
}

/** V23 - stabilizes explicit intent metadata, but never invents a device action. */
class V23IntentStabilizer : NeuroModule {
    override val id: String = "v23-intent-stabilizer"
    override val inputs: Set<NeuroChannel> = setOf(NeuroChannel.CONTEXT)
    override val outputs: Set<NeuroChannel> = setOf(NeuroChannel.INTENT)

    override suspend fun onSignal(signal: NeuroSignal): List<NeuroSignal> {
        val explicitAction = signal.payload["actionId"]?.trim()?.lowercase().orEmpty()
        val explicitIntent = signal.payload["intent"]?.trim()?.lowercase().orEmpty()
        if (explicitAction.isBlank() && explicitIntent.isBlank()) return emptyList()

        val confidence = signal.payload["confidence"]
            ?.toDoubleOrNull()
            ?.coerceIn(0.0, 1.0)
            ?: 1.0

        return listOf(
            NeuroSignal(
                channel = NeuroChannel.INTENT,
                source = id,
                payload = buildMap {
                    if (explicitAction.isNotBlank()) put("actionId", explicitAction)
                    if (explicitIntent.isNotBlank()) put("intent", explicitIntent)
                    put("confidence", confidence.toString())
                    signal.payload["correlationId"]?.let { put("correlationId", it) }
                },
            ),
        )
    }
}

/** V24 - creates a one-step proposal plan. It cannot dispatch Android work. */
class V24PlanSynthesizer : NeuroModule {
    override val id: String = "v24-plan-synthesizer"
    override val inputs: Set<NeuroChannel> = setOf(NeuroChannel.INTENT)
    override val outputs: Set<NeuroChannel> = setOf(NeuroChannel.PLAN)

    override suspend fun onSignal(signal: NeuroSignal): List<NeuroSignal> {
        val actionId = signal.payload["actionId"].orEmpty()
        if (actionId.isBlank()) return emptyList()

        return listOf(
            NeuroSignal(
                channel = NeuroChannel.PLAN,
                source = id,
                payload = buildMap {
                    put("actionId", actionId)
                    put("stepCount", "1")
                    put("executionMode", "proposal_only")
                    signal.payload["confidence"]?.let { put("intentConfidence", it) }
                    signal.payload["correlationId"]?.let { put("correlationId", it) }
                },
            ),
        )
    }
}

/**
 * V25 - bridge from internal plan to canonical ACTION_REQUEST.
 *
 * This stage intentionally performs no device action. The resulting request must
 * still pass through the existing X-88 SecurityChain / confirmation path.
 */
class V25ActionProposalBridge : NeuroModule {
    override val id: String = "v25-action-proposal-bridge"
    override val inputs: Set<NeuroChannel> = setOf(NeuroChannel.PLAN)
    override val outputs: Set<NeuroChannel> = setOf(NeuroChannel.ACTION_REQUEST)

    override suspend fun onSignal(signal: NeuroSignal): List<NeuroSignal> {
        val actionId = signal.payload["actionId"].orEmpty()
        if (actionId.isBlank()) return emptyList()

        return listOf(
            NeuroSignal(
                channel = NeuroChannel.ACTION_REQUEST,
                source = id,
                payload = buildMap {
                    put("actionId", actionId)
                    put("requiresSecurityChain", "true")
                    put("executionMode", "proposal_only")
                    signal.payload["correlationId"]?.let { put("correlationId", it) }
                },
            ),
        )
    }
}

/** V26 - turns action outcomes into bounded memory/emotion feedback signals. */
class V26OutcomeIntegrator : NeuroModule {
    override val id: String = "v26-outcome-integrator"
    override val inputs: Set<NeuroChannel> = setOf(NeuroChannel.ACTION_RESULT)
    override val outputs: Set<NeuroChannel> = setOf(
        NeuroChannel.MEMORY,
        NeuroChannel.EMOTION_STATE,
    )

    override suspend fun onSignal(signal: NeuroSignal): List<NeuroSignal> {
        val ok = signal.payload["ok"]?.toBooleanStrictOrNull() ?: false
        val code = signal.payload["code"].orEmpty().take(96)
        val emotionalWeight = if (ok) 0.20 else -0.25
        val tension = if (ok) 0.05 else 0.35

        return listOf(
            NeuroSignal(
                channel = NeuroChannel.MEMORY,
                source = id,
                payload = mapOf(
                    "kind" to "action_outcome",
                    "ok" to ok.toString(),
                    "code" to code,
                ),
            ),
            NeuroSignal(
                channel = NeuroChannel.EMOTION_STATE,
                source = id,
                payload = mapOf(
                    "emotionalWeight" to emotionalWeight.toString(),
                    "tension" to tension.toString(),
                    "intensity" to if (ok) "0.25" else "0.55",
                ),
            ),
        )
    }
}

/** V27 - explicit lifecycle state with a fail-quiet safety transition. */
class V27LifecycleRegulator : NeuroModule {
    enum class Mode { ACTIVE, QUIET, STOPPED }

    override val id: String = "v27-lifecycle-regulator"
    override val inputs: Set<NeuroChannel> = setOf(
        NeuroChannel.LIFECYCLE,
        NeuroChannel.SAFETY,
        NeuroChannel.DEVICE_STATE,
    )
    override val outputs: Set<NeuroChannel> = setOf(NeuroChannel.LIFECYCLE)

    @Volatile
    var mode: Mode = Mode.QUIET
        private set

    override suspend fun onSignal(signal: NeuroSignal): List<NeuroSignal> {
        if (signal.channel == NeuroChannel.LIFECYCLE) {
            signal.payload["mode"]?.let { raw ->
                runCatching { Mode.valueOf(raw.uppercase()) }.getOrNull()?.let { mode = it }
            }
            return emptyList()
        }

        mode = when {
            signal.channel == NeuroChannel.SAFETY &&
                (signal.payload["stop"] == "true" || signal.payload["blocked"] == "true") -> Mode.STOPPED
            signal.payload["active"] == "true" -> Mode.ACTIVE
            else -> Mode.QUIET
        }

        return listOf(
            NeuroSignal(
                channel = NeuroChannel.LIFECYCLE,
                source = id,
                payload = mapOf("mode" to mode.name.lowercase()),
            ),
        )
    }
}

data class NeuroTelemetryEntry(
    val sequence: Long,
    val channel: NeuroChannel,
    val source: String,
    val timestamp: Long,
)

/** V28 - metadata-only bounded telemetry. User payloads are not copied into the ledger. */
class V28TelemetryLedger(
    private val capacity: Int = 128,
) : NeuroModule {
    override val id: String = "v28-telemetry-ledger"
    override val inputs: Set<NeuroChannel> = setOf(
        NeuroChannel.ATTENTION,
        NeuroChannel.CONTEXT,
        NeuroChannel.INTENT,
        NeuroChannel.PLAN,
        NeuroChannel.ACTION_REQUEST,
        NeuroChannel.ACTION_RESULT,
        NeuroChannel.LIFECYCLE,
        NeuroChannel.SAFETY,
    )
    override val outputs: Set<NeuroChannel> = setOf(NeuroChannel.TELEMETRY)

    private val sequence = AtomicLong(0)
    private val entries = ArrayDeque<NeuroTelemetryEntry>()

    init {
        require(capacity > 0) { "capacity must be > 0" }
    }

    override suspend fun onSignal(signal: NeuroSignal): List<NeuroSignal> {
        val next = NeuroTelemetryEntry(
            sequence = sequence.incrementAndGet(),
            channel = signal.channel,
            source = signal.source,
            timestamp = signal.timestamp,
        )
        synchronized(entries) {
            if (entries.size == capacity) entries.removeFirst()
            entries.addLast(next)
        }

        return listOf(
            NeuroSignal(
                channel = NeuroChannel.TELEMETRY,
                source = id,
                payload = mapOf(
                    "sequence" to next.sequence.toString(),
                    "eventChannel" to next.channel.name,
                    "eventSource" to next.source,
                ),
            ),
        )
    }

    fun snapshot(): List<NeuroTelemetryEntry> = synchronized(entries) { entries.toList() }
}

/** V29 - topology/telemetry integrity monitor with no escalation capability. */
class V29IntegrityMonitor : NeuroModule {
    override val id: String = "v29-integrity-monitor"
    override val inputs: Set<NeuroChannel> = setOf(NeuroChannel.TELEMETRY)
    override val outputs: Set<NeuroChannel> = setOf(NeuroChannel.INTEGRITY)

    @Volatile
    private var topologyHealthy: Boolean = false

    @Volatile
    private var lastSequence: Long = 0L

    fun updateTopologyHealth(healthy: Boolean) {
        topologyHealthy = healthy
    }

    fun healthy(): Boolean = topologyHealthy

    override suspend fun onSignal(signal: NeuroSignal): List<NeuroSignal> {
        val sequence = signal.payload["sequence"]?.toLongOrNull() ?: 0L
        if (sequence > lastSequence) lastSequence = sequence
        return listOf(
            NeuroSignal(
                channel = NeuroChannel.INTEGRITY,
                source = id,
                payload = mapOf(
                    "healthy" to topologyHealthy.toString(),
                    "lastSequence" to lastSequence.toString(),
                ),
            ),
        )
    }
}

/**
 * V30 - composed V16..V30 neuro runtime.
 *
 * The runtime remains local and internal. V25 emits proposals only; all Android
 * capabilities remain controlled by the existing X-88 permission, confirmation,
 * emergency-stop and SecurityChain layers.
 */
class V30NeuroRuntime private constructor(
    val base: V20NeuroRuntime,
    val attention: V21AttentionRouter,
    val contextFusion: V22ContextFusion,
    val intent: V23IntentStabilizer,
    val planner: V24PlanSynthesizer,
    val actionProposal: V25ActionProposalBridge,
    val outcome: V26OutcomeIntegrator,
    val lifecycle: V27LifecycleRegulator,
    val telemetry: V28TelemetryLedger,
    val integrity: V29IntegrityMonitor,
) {
    @Volatile
    private var booted: Boolean = false

    val fabric: V16NeuralFabric
        get() = base.fabric

    @Synchronized
    fun boot() {
        if (booted) return
        base.boot()
        listOf(
            attention,
            contextFusion,
            intent,
            planner,
            actionProposal,
            outcome,
            lifecycle,
            telemetry,
            integrity,
        ).forEach(fabric::register)

        integrity.updateTopologyHealth(REQUIRED_V21_V29_MODULES.all { it in fabric.moduleIds() })
        booted = true
    }

    suspend fun emit(signal: NeuroSignal) = fabric.emit(signal)

    fun health(): NeuroHealthReport {
        val baseReport = base.health()
        val ids = fabric.moduleIds().toSet()
        val stage21 = attention.id in ids
        val stage22 = contextFusion.id in ids
        val stage23 = intent.id in ids
        val stage24 = planner.id in ids
        val stage25 = actionProposal.id in ids
        val stage26 = outcome.id in ids
        val stage27 = lifecycle.id in ids
        val stage28 = telemetry.id in ids
        val stage29 = integrity.id in ids && integrity.healthy()
        val stage30 = booted && baseReport.green &&
            listOf(stage21, stage22, stage23, stage24, stage25, stage26, stage27, stage28, stage29).all { it }

        val stages = baseReport.stages + listOf(
            NeuroStageHealth(21, "attention-router", stage21),
            NeuroStageHealth(22, "context-fusion", stage22),
            NeuroStageHealth(23, "intent-stabilizer", stage23),
            NeuroStageHealth(24, "plan-synthesizer", stage24),
            NeuroStageHealth(25, "action-proposal-bridge", stage25),
            NeuroStageHealth(26, "outcome-integrator", stage26),
            NeuroStageHealth(27, "lifecycle-regulator", stage27),
            NeuroStageHealth(28, "metadata-telemetry-ledger", stage28),
            NeuroStageHealth(29, "integrity-monitor", stage29),
            NeuroStageHealth(30, "composed-runtime-gate", stage30),
        )

        return NeuroHealthReport(
            version = 30,
            green = stages.all { it.healthy },
            stages = stages,
            modules = fabric.moduleIds(),
        )
    }

    companion object {
        private val REQUIRED_V21_V29_MODULES = setOf(
            "v21-attention-router",
            "v22-context-fusion",
            "v23-intent-stabilizer",
            "v24-plan-synthesizer",
            "v25-action-proposal-bridge",
            "v26-outcome-integrator",
            "v27-lifecycle-regulator",
            "v28-telemetry-ledger",
            "v29-integrity-monitor",
        )

        @Volatile
        private var processRuntime: V30NeuroRuntime? = null

        fun createForTest(): V30NeuroRuntime = create(V20NeuroRuntime.createForTest())

        fun initialize(): V30NeuroRuntime {
            processRuntime?.let { return it }
            return synchronized(this) {
                processRuntime ?: create(V20NeuroRuntime.initialize()).also { runtime ->
                    runtime.boot()
                    processRuntime = runtime
                }
            }
        }

        fun currentOrNull(): V30NeuroRuntime? = processRuntime

        private fun create(base: V20NeuroRuntime): V30NeuroRuntime = V30NeuroRuntime(
            base = base,
            attention = V21AttentionRouter(),
            contextFusion = V22ContextFusion(),
            intent = V23IntentStabilizer(),
            planner = V24PlanSynthesizer(),
            actionProposal = V25ActionProposalBridge(),
            outcome = V26OutcomeIntegrator(),
            lifecycle = V27LifecycleRegulator(),
            telemetry = V28TelemetryLedger(),
            integrity = V29IntegrityMonitor(),
        )
    }
}

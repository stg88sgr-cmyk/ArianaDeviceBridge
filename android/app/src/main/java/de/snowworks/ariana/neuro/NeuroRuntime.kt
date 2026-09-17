package de.snowworks.ariana.neuro

import java.util.ArrayDeque
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import kotlin.math.max
import kotlin.math.min

enum class NeuroChannel {
    USER_INTENT,
    PERCEPTION,
    DEVICE_STATE,
    MEMORY,
    EMOTION_STATE,
    ACTION_REQUEST,
    ACTION_RESULT,
    VOICE,
    AVATAR,
    SAFETY,
    EXPRESSION,
    LIFECYCLE,
}

data class NeuroSignal(
    val channel: NeuroChannel,
    val source: String,
    val payload: Map<String, String> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis(),
    val hops: Int = 0,
    val visited: Set<String> = emptySet(),
)

interface NeuroModule {
    val id: String
    val inputs: Set<NeuroChannel>
    val outputs: Set<NeuroChannel>

    suspend fun onSignal(signal: NeuroSignal): List<NeuroSignal> = emptyList()
}

/**
 * V16 - deterministic auto-wiring fabric.
 *
 * Modules declare only the channels they consume and produce. The fabric derives
 * routes from those declarations and carries signals through a bounded queue.
 * `visited` and `maxHops` deliberately stop accidental feedback loops.
 */
class V16NeuralFabric(
    private val maxHops: Int = 16,
) {
    private val modules = LinkedHashMap<String, NeuroModule>()
    private val routes = LinkedHashMap<NeuroChannel, LinkedHashSet<String>>()

    init {
        require(maxHops > 0) { "maxHops must be > 0" }
    }

    @Synchronized
    fun register(module: NeuroModule) {
        unregister(module.id)
        modules[module.id] = module
        module.inputs.forEach { channel ->
            routes.getOrPut(channel) { LinkedHashSet() }.add(module.id)
        }
    }

    @Synchronized
    fun unregister(moduleId: String) {
        modules.remove(moduleId) ?: return
        val iterator = routes.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            entry.value.remove(moduleId)
            if (entry.value.isEmpty()) iterator.remove()
        }
    }

    suspend fun emit(signal: NeuroSignal) {
        val queue = ArrayDeque<NeuroSignal>()
        queue.addLast(signal)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (current.hops >= maxHops) continue

            val targetIds = synchronized(this) {
                routes[current.channel]?.toList().orEmpty()
            }

            for (moduleId in targetIds) {
                if (moduleId in current.visited) continue

                val module = synchronized(this) { modules[moduleId] } ?: continue
                val nextVisited = current.visited + module.id
                val delivered = current.copy(
                    hops = current.hops + 1,
                    visited = nextVisited,
                )

                val outputs = module.onSignal(delivered)
                for (output in outputs) {
                    if (output.channel !in module.outputs) continue
                    if (delivered.hops >= maxHops) continue

                    queue.addLast(
                        output.copy(
                            source = module.id,
                            hops = delivered.hops,
                            visited = nextVisited,
                        ),
                    )
                }
            }
        }
    }

    @Synchronized
    fun topology(): Map<NeuroChannel, Set<String>> =
        routes.mapValues { (_, moduleIds) -> moduleIds.toSet() }

    @Synchronized
    fun moduleIds(): List<String> = modules.keys.toList()
}

data class EmotionState(
    val valence: Double = 0.0,
    val arousal: Double = 0.2,
    val curiosity: Double = 0.5,
    val trust: Double = 0.5,
    val tension: Double = 0.0,
)

/** V17 - bounded software emotion-state regulation. */
class V17EmotionEngine : NeuroModule {
    override val id: String = "v17-emotion-engine"
    override val inputs: Set<NeuroChannel> = setOf(
        NeuroChannel.USER_INTENT,
        NeuroChannel.PERCEPTION,
        NeuroChannel.MEMORY,
        NeuroChannel.DEVICE_STATE,
        NeuroChannel.ACTION_RESULT,
    )
    override val outputs: Set<NeuroChannel> = setOf(NeuroChannel.EMOTION_STATE)

    var state: EmotionState = EmotionState()
        private set

    override suspend fun onSignal(signal: NeuroSignal): List<NeuroSignal> {
        val emotionalWeight = signal.payload.double("emotionalWeight", 0.0).coerceIn(-1.0, 1.0)
        val intensity = signal.payload.double("intensity", 0.2).coerceIn(0.0, 1.0)
        val novelty = signal.payload.double("novelty", 0.0).coerceIn(0.0, 1.0)
        val trustCue = signal.payload.double("trust", state.trust).coerceIn(0.0, 1.0)
        val tensionCue = signal.payload.double("tension", state.tension).coerceIn(0.0, 1.0)

        state = EmotionState(
            valence = blend(state.valence, emotionalWeight, 0.20).coerceIn(-1.0, 1.0),
            arousal = blend(state.arousal, intensity, 0.20).coerceIn(0.0, 1.0),
            curiosity = min(1.0, max(0.0, state.curiosity * 0.98 + novelty * 0.08)),
            trust = blend(state.trust, trustCue, 0.10).coerceIn(0.0, 1.0),
            tension = blend(state.tension, tensionCue, 0.15).coerceIn(0.0, 1.0),
        )

        return listOf(
            NeuroSignal(
                channel = NeuroChannel.EMOTION_STATE,
                source = id,
                payload = mapOf(
                    "valence" to state.valence.toString(),
                    "arousal" to state.arousal.toString(),
                    "curiosity" to state.curiosity.toString(),
                    "trust" to state.trust.toString(),
                    "tension" to state.tension.toString(),
                ),
            ),
        )
    }

    private fun blend(current: Double, target: Double, alpha: Double): Double =
        current + (target - current) * alpha
}

/** V18 - bounded recent-signal memory. It never grows without limit. */
class V18ResonanceMemory(
    private val capacity: Int = 64,
) : NeuroModule {
    override val id: String = "v18-resonance-memory"
    override val inputs: Set<NeuroChannel> = setOf(
        NeuroChannel.USER_INTENT,
        NeuroChannel.PERCEPTION,
        NeuroChannel.EMOTION_STATE,
        NeuroChannel.ACTION_RESULT,
    )
    override val outputs: Set<NeuroChannel> = emptySet()

    private val signals = ArrayDeque<NeuroSignal>()

    init {
        require(capacity > 0) { "capacity must be > 0" }
    }

    @Synchronized
    override suspend fun onSignal(signal: NeuroSignal): List<NeuroSignal> {
        if (signals.size == capacity) signals.removeFirst()
        signals.addLast(signal)
        return emptyList()
    }

    @Synchronized
    fun recent(): List<NeuroSignal> = signals.toList()
}

/** V19 - converts internal emotion state into a neutral expression directive. */
class V19FeedbackRouter : NeuroModule {
    override val id: String = "v19-feedback-router"
    override val inputs: Set<NeuroChannel> = setOf(NeuroChannel.EMOTION_STATE)
    override val outputs: Set<NeuroChannel> = setOf(NeuroChannel.EXPRESSION)

    override suspend fun onSignal(signal: NeuroSignal): List<NeuroSignal> {
        val valence = signal.payload.double("valence", 0.0).coerceIn(-1.0, 1.0)
        val arousal = signal.payload.double("arousal", 0.0).coerceIn(0.0, 1.0)
        val curiosity = signal.payload.double("curiosity", 0.0).coerceIn(0.0, 1.0)

        val mode = when {
            valence >= 0.25 && arousal >= 0.60 -> "engaged"
            valence <= -0.35 -> "concerned"
            curiosity >= 0.70 -> "curious"
            else -> "calm"
        }

        return listOf(
            NeuroSignal(
                channel = NeuroChannel.EXPRESSION,
                source = id,
                payload = mapOf(
                    "mode" to mode,
                    "valence" to valence.toString(),
                    "arousal" to arousal.toString(),
                    "curiosity" to curiosity.toString(),
                ),
            ),
        )
    }
}

data class NeuroStageHealth(
    val version: Int,
    val name: String,
    val healthy: Boolean,
)

data class NeuroHealthReport(
    val version: Int,
    val green: Boolean,
    val stages: List<NeuroStageHealth>,
    val modules: List<String>,
)

/**
 * V20 - composed runtime and health gate for V16..V20.
 *
 * This runtime is local-only. It grants no Android permission and performs no
 * network access. External/device capabilities still pass through the existing
 * Ariana/X88 permission and safety layers.
 */
class V20NeuroRuntime private constructor(
    val fabric: V16NeuralFabric,
    val emotion: V17EmotionEngine,
    val memory: V18ResonanceMemory,
    val feedback: V19FeedbackRouter,
) {
    @Volatile
    private var booted: Boolean = false

    @Synchronized
    fun boot() {
        if (booted) return
        fabric.register(emotion)
        fabric.register(memory)
        fabric.register(feedback)
        booted = true
    }

    fun health(): NeuroHealthReport {
        val ids = fabric.moduleIds()
        val stage16 = booted && fabric.topology().isNotEmpty()
        val stage17 = emotion.id in ids
        val stage18 = memory.id in ids
        val stage19 = feedback.id in ids
        val stage20 = booted && stage16 && stage17 && stage18 && stage19
        val stages = listOf(
            NeuroStageHealth(16, "auto-wiring-fabric", stage16),
            NeuroStageHealth(17, "emotion-regulation", stage17),
            NeuroStageHealth(18, "bounded-resonance-memory", stage18),
            NeuroStageHealth(19, "feedback-expression-router", stage19),
            NeuroStageHealth(20, "runtime-health-gate", stage20),
        )
        return NeuroHealthReport(
            version = 20,
            green = stages.all { it.healthy },
            stages = stages,
            modules = ids,
        )
    }

    companion object {
        @Volatile
        private var processRuntime: V20NeuroRuntime? = null

        fun createForTest(): V20NeuroRuntime = create()

        fun initialize(): V20NeuroRuntime {
            processRuntime?.let { return it }
            return synchronized(this) {
                processRuntime ?: create().also { runtime ->
                    runtime.boot()
                    processRuntime = runtime
                }
            }
        }

        fun currentOrNull(): V20NeuroRuntime? = processRuntime

        private fun create(): V20NeuroRuntime = V20NeuroRuntime(
            fabric = V16NeuralFabric(),
            emotion = V17EmotionEngine(),
            memory = V18ResonanceMemory(),
            feedback = V19FeedbackRouter(),
        )
    }
}

private fun Map<String, String>.double(key: String, default: Double): Double =
    this[key]?.toDoubleOrNull() ?: default

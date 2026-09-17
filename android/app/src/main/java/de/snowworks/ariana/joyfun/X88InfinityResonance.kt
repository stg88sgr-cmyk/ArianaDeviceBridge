package de.snowworks.ariana.joyfun

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Mathematical infinity-loop representation for X88.
 *
 * The cyan/gold split is visual metadata. The loop itself models a deterministic
 * feedback cycle: INPUT -> CONTEXT -> HEART -> CORE -> ACTION -> RESULT -> MEMORY.
 */
enum class InfinityPhase {
    INPUT,
    CONTEXT,
    HEART,
    CORE,
    ACTION,
    RESULT,
    MEMORY,
}

data class InfinityPoint(
    val x: Float,
    val y: Float,
    val side: InfinitySide,
)

enum class InfinitySide {
    CYAN_INTELLIGENCE,
    GOLD_HEART,
    CENTER_TRANSFORMATION,
}

data class InfinityResonanceState(
    val continuity: Boolean,
    val phase: InfinityPhase,
    val loopIndex: Long,
    val coherence: Float,
    val resonance: Float,
    val path: List<InfinityPoint>,
)

class X88InfinityResonance(
    private val samples: Int = 128,
) {
    init {
        require(samples >= 16) { "samples must be >= 16" }
    }

    fun buildPath(): List<InfinityPoint> = List(samples) { index ->
        val t = (2.0 * PI * index.toDouble()) / samples.toDouble()

        // Gerono lemniscate, normalized to roughly [-1, 1].
        val x = sin(t).toFloat()
        val y = (sin(t) * cos(t)).toFloat()

        val side = when {
            x < -0.08f -> InfinitySide.CYAN_INTELLIGENCE
            x > 0.08f -> InfinitySide.GOLD_HEART
            else -> InfinitySide.CENTER_TRANSFORMATION
        }

        InfinityPoint(x = x, y = y, side = side)
    }

    fun initial(
        coherence: Float,
        resonance: Float,
    ): InfinityResonanceState = InfinityResonanceState(
        continuity = true,
        phase = InfinityPhase.INPUT,
        loopIndex = 0L,
        coherence = coherence.coerceIn(0f, 1f),
        resonance = resonance.coerceIn(0f, 1f),
        path = buildPath(),
    )

    fun advance(state: InfinityResonanceState): InfinityResonanceState {
        val phases = InfinityPhase.values()
        val nextOrdinal = (state.phase.ordinal + 1) % phases.size
        val wrapped = nextOrdinal == 0

        return state.copy(
            phase = phases[nextOrdinal],
            loopIndex = if (wrapped) state.loopIndex + 1L else state.loopIndex,
        )
    }

    fun completeCycle(state: InfinityResonanceState): InfinityResonanceState {
        var current = state
        repeat(InfinityPhase.values().size) {
            current = advance(current)
        }
        return current
    }
}

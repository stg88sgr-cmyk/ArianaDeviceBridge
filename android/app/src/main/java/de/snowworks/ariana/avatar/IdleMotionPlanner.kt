package de.snowworks.ariana.avatar

import kotlin.math.PI
import kotlin.math.sin

/**
 * Deterministic low-amplitude motion for Ariana while the renderer is visible.
 *
 * This planner has no Android or renderer dependency. It generates subtle head,
 * eye, blink, breathing and core-light motion around a caller-owned base pose.
 * A renderer may sample it at any cadence.
 */
object IdleMotionPlanner {

    data class Frame(
        val headYawOffset: Float,
        val headPitchOffset: Float,
        val headRollOffset: Float,
        val eyeXOffset: Float,
        val eyeYOffset: Float,
        val eyeOpen: Float,
        val breath: Float,
        val coreGlow: Float,
    )

    fun sample(elapsedMs: Long, speaking: Boolean): Frame {
        val t = elapsedMs.coerceAtLeast(0L) / 1000.0
        val motionScale = if (speaking) 0.72f else 1f

        return Frame(
            headYawOffset = (sin(t * 0.54) * 0.055 * motionScale).toFloat(),
            headPitchOffset = (sin(t * 0.41 + 1.2) * 0.035 * motionScale).toFloat(),
            headRollOffset = (sin(t * 0.29 + 0.35) * 0.018 * motionScale).toFloat(),
            eyeXOffset = (sin(t * 0.73 + 0.6) * 0.085 * motionScale).toFloat(),
            eyeYOffset = (sin(t * 0.51 + 1.8) * 0.045 * motionScale).toFloat(),
            eyeOpen = blinkOpen(elapsedMs),
            breath = (0.5 + sin(t * (2.0 * PI / BREATH_PERIOD_SECONDS)) * 0.14)
                .toFloat()
                .coerceIn(0f, 1f),
            coreGlow = (0.64 + sin(t * (2.0 * PI / CORE_PERIOD_SECONDS) + 0.5) * 0.055)
                .toFloat()
                .coerceIn(0f, 1f),
        )
    }

    private fun blinkOpen(elapsedMs: Long): Float {
        val phase = ((elapsedMs.coerceAtLeast(0L) + BLINK_PHASE_OFFSET_MS) % BLINK_PERIOD_MS)

        return when {
            phase < BLINK_CLOSING_MS -> 1f - (phase.toFloat() / BLINK_CLOSING_MS)
            phase < BLINK_CLOSING_MS + BLINK_HOLD_MS -> 0f
            phase < BLINK_CLOSING_MS + BLINK_HOLD_MS + BLINK_OPENING_MS -> {
                val opening = phase - BLINK_CLOSING_MS - BLINK_HOLD_MS
                opening.toFloat() / BLINK_OPENING_MS
            }
            else -> 1f
        }.coerceIn(0f, 1f)
    }

    internal const val BLINK_PERIOD_MS = 4200L
    internal const val BLINK_PHASE_OFFSET_MS = 1300L
    private const val BLINK_CLOSING_MS = 70L
    private const val BLINK_HOLD_MS = 45L
    private const val BLINK_OPENING_MS = 90L
    private const val BREATH_PERIOD_SECONDS = 4.8
    private const val CORE_PERIOD_SECONDS = 6.4
}

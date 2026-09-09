package de.snowworks.ariana.avatar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IdleMotionPlannerTest {

    @Test
    fun sampledValuesStayInsideAvatarContract() {
        listOf(0L, 250L, 1200L, 2980L, 4200L, 12_345L, 60_000L).forEach { time ->
            val frame = IdleMotionPlanner.sample(time, speaking = false)

            assertTrue(frame.headYawOffset in -1f..1f)
            assertTrue(frame.headPitchOffset in -1f..1f)
            assertTrue(frame.headRollOffset in -1f..1f)
            assertTrue(frame.eyeXOffset in -1f..1f)
            assertTrue(frame.eyeYOffset in -1f..1f)
            assertTrue(frame.eyeOpen in 0f..1f)
            assertTrue(frame.breath in 0f..1f)
            assertTrue(frame.coreGlow in 0f..1f)
        }
    }

    @Test
    fun blinkActuallyClosesAndReopens() {
        val blinkStart = IdleMotionPlanner.BLINK_PERIOD_MS - IdleMotionPlanner.BLINK_PHASE_OFFSET_MS
        val before = IdleMotionPlanner.sample(blinkStart - 1L, speaking = false)
        val closed = IdleMotionPlanner.sample(blinkStart + 80L, speaking = false)
        val after = IdleMotionPlanner.sample(blinkStart + 250L, speaking = false)

        assertEquals(1f, before.eyeOpen, 0.001f)
        assertEquals(0f, closed.eyeOpen, 0.001f)
        assertEquals(1f, after.eyeOpen, 0.001f)
    }

    @Test
    fun speakingReducesIdleHeadMovement() {
        val time = 1650L
        val idle = IdleMotionPlanner.sample(time, speaking = false)
        val speaking = IdleMotionPlanner.sample(time, speaking = true)

        assertTrue(kotlin.math.abs(speaking.headYawOffset) <= kotlin.math.abs(idle.headYawOffset))
        assertTrue(kotlin.math.abs(speaking.headPitchOffset) <= kotlin.math.abs(idle.headPitchOffset))
        assertTrue(kotlin.math.abs(speaking.eyeXOffset) <= kotlin.math.abs(idle.eyeXOffset))
    }
}

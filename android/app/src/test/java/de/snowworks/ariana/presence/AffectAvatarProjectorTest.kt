package de.snowworks.ariana.presence

import de.snowworks.ariana.avatar.AvatarState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AffectAvatarProjectorTest {

    @Test
    fun warmPlayfulStateBecomesHappy() {
        val result = AffectAvatarProjector.apply(
            PresenceAffectState(warmth = 0.92f, playfulness = 0.72f),
            AvatarState(),
        )

        assertEquals(AvatarState.Expression.HAPPY, result.expression)
        assertTrue(result.coreGlow > 0.65f)
    }

    @Test
    fun focusedTenseStateBecomesStern() {
        val result = AffectAvatarProjector.apply(
            PresenceAffectState(focus = 0.88f, tension = 0.82f),
            AvatarState(),
        )

        assertEquals(AvatarState.Expression.STERN, result.expression)
    }

    @Test
    fun stateIsClampedBeforeProjection() {
        val result = AffectAvatarProjector.apply(
            PresenceAffectState(warmth = 3f, confidence = 3f, tension = -2f),
            AvatarState(),
        )

        assertTrue(result.coreGlow in 0f..1f)
    }
}

package de.snowworks.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class X88AvatarAnimationPolicyTest {
    @Test
    fun speakingPrefersTalkClip() {
        val names = listOf("Idle_Breath", "Talk", "Attention")
        assertEquals(
            1,
            X88AvatarAnimationPolicy.pickAnimationIndex(X88AvatarView.Mode.SPEAKING, names),
        )
    }

    @Test
    fun missingModeClipFallsBackToIdleBreath() {
        val names = listOf("Idle_Breath")
        assertEquals(
            0,
            X88AvatarAnimationPolicy.pickAnimationIndex(X88AvatarView.Mode.THINKING, names),
        )
    }

    @Test
    fun staticModelIsValidAndReturnsNoAnimation() {
        assertNull(
            X88AvatarAnimationPolicy.pickAnimationIndex(X88AvatarView.Mode.IDLE, emptyList()),
        )
    }

    @Test
    fun stoppedNeverAutoplaysAnimation() {
        val names = listOf("Idle_Breath", "Talk")
        assertNull(
            X88AvatarAnimationPolicy.pickAnimationIndex(X88AvatarView.Mode.STOPPED, names),
        )
    }
}

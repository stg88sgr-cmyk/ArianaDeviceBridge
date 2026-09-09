package de.snowworks.ariana.avatar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarDriverRouterTest {

    @Test
    fun replaysStateAndVisibilityWhenDriverIsInstalled() {
        val first = RecordingDriver()
        val router = AvatarDriverRouter(first)
        val state = AvatarState(
            headYaw = 0.4f,
            mouthOpen = 0.7f,
            coreGlow = 0.9f,
            speaking = true,
        )

        router.apply(state)
        router.show()

        val second = RecordingDriver()
        router.install(second)

        assertTrue(first.closed)
        assertEquals(state.normalized(), second.lastState)
        assertTrue(second.visible)
        assertSame(second, router.currentDriver())
    }

    @Test
    fun installsHiddenDriverWithoutShowingIt() {
        val router = AvatarDriverRouter()
        router.apply(AvatarState(headPitch = 0.25f))

        val second = RecordingDriver()
        router.install(second)

        assertFalse(second.visible)
        assertEquals(AvatarState(headPitch = 0.25f).normalized(), second.lastState)
    }

    @Test
    fun closingRouterClosesActiveDriverAndRejectsReplacement() {
        val first = RecordingDriver()
        val router = AvatarDriverRouter(first)

        router.close()
        val second = RecordingDriver()
        router.install(second)

        assertTrue(first.closed)
        assertTrue(second.closed)
    }

    private class RecordingDriver : AvatarDriver {
        var lastState: AvatarState? = null
        var visible = false
        var closed = false

        override fun apply(state: AvatarState) {
            lastState = state
        }

        override fun show() {
            visible = true
        }

        override fun hide() {
            visible = false
        }

        override fun close() {
            closed = true
        }
    }
}

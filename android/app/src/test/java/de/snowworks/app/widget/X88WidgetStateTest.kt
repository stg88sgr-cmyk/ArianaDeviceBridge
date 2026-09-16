package de.snowworks.app.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class X88WidgetStateTest {

    @Test
    fun stopAllAlwaysWins() {
        val state = state(master = true, blocked = true, camera = true)

        assertEquals(X88WidgetState.Mode.BLOCKED, state.mode)
        assertEquals("STOP ALL", state.headline)
    }

    @Test
    fun masterOffIsClear() {
        val state = state(master = false)

        assertEquals(X88WidgetState.Mode.OFF, state.mode)
        assertEquals("Lokaler Gerätezugriff ist ausgeschaltet", state.detail)
    }

    @Test
    fun activeMediaSessionsAreListed() {
        val state = state(master = true, camera = true, microphone = true)

        assertEquals(X88WidgetState.Mode.ACTIVE, state.mode)
        assertEquals("Aktiv · CAM · MIC", state.detail)
    }

    @Test
    fun activeMasterWithoutSessionIsReady() {
        val state = state(master = true)

        assertEquals("Bereit · keine Medien-Sitzung aktiv", state.detail)
    }

    private fun state(
        master: Boolean,
        blocked: Boolean = false,
        camera: Boolean = false,
        microphone: Boolean = false,
        screen: Boolean = false,
    ) = X88WidgetState(
        masterEnabled = master,
        blocked = blocked,
        cameraActive = camera,
        microphoneActive = microphone,
        screenActive = screen,
    )
}

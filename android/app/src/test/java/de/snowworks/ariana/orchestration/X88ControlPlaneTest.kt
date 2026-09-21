package de.snowworks.ariana.orchestration

import de.snowworks.ariana.neuro.ResonanceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class X88ControlPlaneTest {
    private val resonance = ResonanceState(
        coherence = 0.8,
        growth = 0.6,
        correction = 0.1,
        stability = 0.9,
        marker = "growth",
    )

    @Test
    fun routesDialogueToLocalDialogue() {
        val plane = X88ControlPlane()
        val route = plane.route(X88Task(X88TaskKind.DIALOGUE, "Sprich mit mir"), resonance)

        assertEquals(X88Capability.LOCAL_DIALOGUE, route.capability)
        assertTrue(route.reason.contains("preferred local capability"))
        assertEquals(resonance, route.resonance)
    }

    @Test
    fun routesCreationToLocalCreation() {
        val plane = X88ControlPlane()
        val route = plane.route(X88Task(X88TaskKind.CREATION, "Erzeuge eine Szene"), resonance)

        assertEquals(X88Capability.LOCAL_CREATION, route.capability)
    }

    @Test
    fun fallsBackWhenPreferredCapabilityIsUnavailable() {
        val plane = X88ControlPlane(setOf(X88Capability.LOCAL_MEMORY))
        val route = plane.route(X88Task(X88TaskKind.CREATION, "Erzeuge eine Szene"), resonance)

        assertEquals(X88Capability.LOCAL_MEMORY, route.capability)
        assertTrue(route.reason.contains("bounded fallback"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsBlankTasks() {
        X88ControlPlane().route(X88Task(X88TaskKind.DIALOGUE, "   "), resonance)
    }
}

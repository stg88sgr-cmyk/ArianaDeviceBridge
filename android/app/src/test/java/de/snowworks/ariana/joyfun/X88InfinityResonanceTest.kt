package de.snowworks.ariana.joyfun

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class X88InfinityResonanceTest {
    @Test
    fun pathContainsCyanGoldAndCenterZones() {
        val path = X88InfinityResonance(samples = 128).buildPath()

        assertEquals(128, path.size)
        assertTrue(path.any { it.side == InfinitySide.CYAN_INTELLIGENCE })
        assertTrue(path.any { it.side == InfinitySide.GOLD_HEART })
        assertTrue(path.any { it.side == InfinitySide.CENTER_TRANSFORMATION })
    }

    @Test
    fun cycleReturnsToInputAndIncrementsLoopCounter() {
        val engine = X88InfinityResonance(samples = 64)
        val initial = engine.initial(coherence = 0.8f, resonance = 0.9f)
        val completed = engine.completeCycle(initial)

        assertEquals(InfinityPhase.INPUT, completed.phase)
        assertEquals(1L, completed.loopIndex)
        assertTrue(completed.continuity)
    }

    @Test
    fun unionCoreCarriesInfinityField() {
        val evolution = JoyFunEvolution(JoyFunClock { 1_000L }).evolveTo(JoyFunState())
        val union = X88UnionCore().unite(
            evolution = evolution,
            memoryConnected = true,
            creationEnabled = true,
        )

        assertEquals(InfinityPhase.INPUT, union.infinity.phase)
        assertTrue(union.infinity.path.isNotEmpty())
        assertTrue(union.infinity.coherence in 0f..1f)
        assertTrue(union.infinity.resonance in 0f..1f)
    }
}

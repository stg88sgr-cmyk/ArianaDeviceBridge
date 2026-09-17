package de.snowworks.ariana.joyfun

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JoyFunV30EvolutionTest {
    private class FixedClock : JoyFunClock {
        private var value = 1_000L
        override fun nowMillis(): Long = value++
    }

    @Test
    fun v30AppliesEveryVersionWithoutGap() {
        val state = JoyFunEvolution(FixedClock()).evolveTo(
            source = JoyFunState(updatedAtMillis = 42L),
            target = JoyFunVersion.V30,
        )

        assertEquals(JoyFunVersion.V30, state.version)
        assertEquals(30, state.stageLedger.size)
        assertEquals((1..30).toList(), state.stageLedger.map { it.version.code })
        assertTrue(state.integrationContractReady)
    }

    @Test
    fun sacredGeometryAndRunesRemainSymbolicDeterministicMetadata() {
        val state = JoyFunEvolution(FixedClock()).evolveTo(JoyFunState())

        assertEquals(13, state.metatronNodes.size)
        assertEquals("CENTER", state.metatronNodes.first().id)
        assertTrue(state.runeAnchors.any { it.rune == "ᚨ" })
        assertTrue(state.runeAnchors.any { it.rune == "ᛏ" })
        assertEquals("X88[444∞888]", state.symbolicSigil)
    }

    @Test
    fun unionCoreRequiresV30EvidenceAndSafetyContracts() {
        val evolution = JoyFunEvolution(FixedClock()).evolveTo(JoyFunState())
        val union = X88UnionCore().unite(
            evolution = evolution,
            memoryConnected = true,
            creationEnabled = true,
        )

        assertEquals(X88UnionMode.UNITED, union.mode)
        assertTrue(union.coreStable)
        assertTrue(union.heart.active)
        assertTrue(union.memoryConnected)
        assertTrue(union.creationEnabled)
        assertEquals("ᚨX88⟦444♥∞888⟧", union.signature)
    }

    @Test
    fun coreLawsKeepConsentAndRealityAheadOfControlAndFantasy() {
        val laws = X88UnionCore.CORE_LAWS
        assertTrue(laws.contains(X88CoreLaw("CONSENT", "CONTROL")))
        assertTrue(laws.contains(X88CoreLaw("REALITY", "FANTASY")))
        assertTrue(laws.contains(X88CoreLaw("TRUTH", "ILLUSION")))
    }
}

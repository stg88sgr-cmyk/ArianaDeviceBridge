package de.snowworks.app.resonance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResonanceCoreTest {

    @Test
    fun sameGraphProducesStableFingerprint() {
        val first = ResonanceMapper.map(X88RuneGraph.default)
        val second = ResonanceMapper.map(X88RuneGraph.default)
        assertEquals(first.fingerprint, second.fingerprint)
        assertEquals(first.fundamentalHz, second.fundamentalHz, 0.000001)
    }

    @Test
    fun graphChangeChangesFingerprint() {
        val base = ResonanceMapper.map(X88RuneGraph.default)
        val changed = ResonanceMapper.map(
            X88RuneGraph.default.copy(seedFrequencyHz = 223.0)
        )
        assertNotEquals(base.fingerprint, changed.fingerprint)
    }

    @Test(expected = IllegalArgumentException::class)
    fun brokenLinkIsRejected() {
        ResonanceMapper.map(
            RuneGraph(
                nodes = listOf(RuneNode("a", "ᚱ")),
                links = listOf(RuneLink("a", "missing")),
            )
        )
    }

    @Test
    fun feedbackAtFundamentalIsRejected() {
        val stabilizer = ResonanceStabilizer()
        val frame = stabilizer.process(
            inputEnergy = 0.5f,
            dominantHz = 222f,
            bands = SpectralBands(0.2f, 0.8f, 0.1f),
            generatedFrequencyHz = 222f,
        )
        assertTrue(frame.feedbackRejected)
        assertEquals(0f, frame.bands.mids, 0f)
    }

    @Test
    fun ordinaryVoiceBandPassesWhenGateOpens() {
        val stabilizer = ResonanceStabilizer()
        val frame = stabilizer.process(
            inputEnergy = 0.5f,
            dominantHz = 440f,
            bands = SpectralBands(0.1f, 0.9f, 0.3f),
            generatedFrequencyHz = 222f,
        )
        assertTrue(frame.gateOpen)
        assertFalse(frame.feedbackRejected)
        assertTrue(frame.energy > 0f)
        assertTrue(frame.bands.mids > 0f)
    }
}

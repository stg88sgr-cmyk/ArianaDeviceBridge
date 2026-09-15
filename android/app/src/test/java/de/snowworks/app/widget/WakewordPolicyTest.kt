package de.snowworks.app.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakewordPolicyTest {
    @Test fun exactPhraseMatches() {
        assertTrue(WakewordPolicy.matches("Ariana, rede mit mir"))
    }

    @Test fun colloquialPhraseMatches() {
        assertTrue(WakewordPolicy.matches("Ariana red mit mir"))
    }

    @Test fun nameAloneDoesNotMatch() {
        assertFalse(WakewordPolicy.matches("Ariana"))
    }

    @Test fun phraseWithoutNameDoesNotMatch() {
        assertFalse(WakewordPolicy.matches("rede mit mir"))
    }
}

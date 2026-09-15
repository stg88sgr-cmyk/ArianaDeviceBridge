package de.snowworks.app.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakewordPolicyTest {
    @Test fun exactPhraseMatches() {
        assertTrue(WakewordPolicy.matches("Ariana, rede mit mir"))
    }

    @Test fun colloquialPhraseMatches() {
        assertTrue(WakewordPolicy.matches("Hey Ariana, red mit mir bitte"))
    }

    @Test fun spacingAndPunctuationNormalize() {
        assertTrue(WakewordPolicy.matches("ARIANA ... REDE   MIT   MIR!"))
    }

    @Test fun nameAloneDoesNotMatch() {
        assertFalse(WakewordPolicy.matches("Ariana"))
    }

    @Test fun phraseWithoutNameDoesNotMatch() {
        assertFalse(WakewordPolicy.matches("rede mit mir"))
    }

    @Test fun miriamDoesNotFalseTrigger() {
        assertFalse(WakewordPolicy.matches("Ariana, rede mit Miriam"))
    }

    @Test fun similarNameDoesNotTrigger() {
        assertFalse(WakewordPolicy.matches("Mariana, rede mit mir"))
    }
}

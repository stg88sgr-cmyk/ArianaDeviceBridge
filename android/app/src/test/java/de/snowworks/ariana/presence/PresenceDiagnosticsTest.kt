package de.snowworks.ariana.presence

import org.junit.Assert.assertEquals
import org.junit.Test

class PresenceDiagnosticsTest {

    @Test
    fun idleWhenNotSpeaking() {
        val diagnostics = PresenceDiagnostics(
            voiceReady = true,
            voiceEnginePackage = "example.tts",
            utterancesStarted = 1,
            rangeCallbacksObserved = 12,
            speaking = false,
        )

        assertEquals(PresenceDiagnostics.LipSyncMode.IDLE, diagnostics.lipSyncMode)
    }

    @Test
    fun fallbackWhenSpeakingWithoutRangeCallbacks() {
        val diagnostics = PresenceDiagnostics(
            voiceReady = true,
            voiceEnginePackage = "example.tts",
            utterancesStarted = 1,
            rangeCallbacksObserved = 0,
            speaking = true,
        )

        assertEquals(PresenceDiagnostics.LipSyncMode.FALLBACK_PULSE, diagnostics.lipSyncMode)
    }

    @Test
    fun textTimingWhenRangeCallbacksAreObserved() {
        val diagnostics = PresenceDiagnostics(
            voiceReady = true,
            voiceEnginePackage = "example.tts",
            utterancesStarted = 1,
            rangeCallbacksObserved = 3,
            speaking = true,
        )

        assertEquals(PresenceDiagnostics.LipSyncMode.TEXT_TIMING, diagnostics.lipSyncMode)
    }
}

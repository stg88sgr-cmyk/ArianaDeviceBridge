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
            currentUtteranceRangeCallbacks = 12,
        )

        assertEquals(PresenceDiagnostics.LipSyncMode.IDLE, diagnostics.lipSyncMode)
    }

    @Test
    fun fallbackWhenSpeakingWithoutCurrentRangeCallbacks() {
        val diagnostics = PresenceDiagnostics(
            voiceReady = true,
            voiceEnginePackage = "example.tts",
            utterancesStarted = 2,
            rangeCallbacksObserved = 19,
            speaking = true,
            currentUtteranceRangeCallbacks = 0,
        )

        assertEquals(PresenceDiagnostics.LipSyncMode.FALLBACK_PULSE, diagnostics.lipSyncMode)
    }

    @Test
    fun textTimingWhenCurrentUtteranceHasRangeCallbacks() {
        val diagnostics = PresenceDiagnostics(
            voiceReady = true,
            voiceEnginePackage = "example.tts",
            utterancesStarted = 1,
            rangeCallbacksObserved = 3,
            speaking = true,
            currentUtteranceRangeCallbacks = 3,
        )

        assertEquals(PresenceDiagnostics.LipSyncMode.TEXT_TIMING, diagnostics.lipSyncMode)
    }

    @Test
    fun previousTimedUtteranceDoesNotPoisonNewFallbackUtterance() {
        val diagnostics = PresenceDiagnostics(
            voiceReady = true,
            voiceEnginePackage = "example.tts",
            utterancesStarted = 4,
            rangeCallbacksObserved = 41,
            speaking = true,
            currentUtteranceRangeCallbacks = 0,
        )

        assertEquals(PresenceDiagnostics.LipSyncMode.FALLBACK_PULSE, diagnostics.lipSyncMode)
    }
}

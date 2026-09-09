package de.snowworks.ariana.presence

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PresenceTelemetryStoreTest {

    @After
    fun tearDown() {
        PresenceTelemetryStore.clear()
    }

    @Test
    fun publishAndReadLatestSnapshot() {
        val expected = PresenceDiagnostics(
            voiceReady = true,
            voiceEnginePackage = "example.tts",
            utterancesStarted = 2,
            rangeCallbacksObserved = 8,
            speaking = true,
            currentUtteranceRangeCallbacks = 3,
        )

        PresenceTelemetryStore.publish(expected)

        assertEquals(expected, PresenceTelemetryStore.snapshot())
    }

    @Test
    fun clearRemovesSnapshot() {
        PresenceTelemetryStore.publish(
            PresenceDiagnostics(
                voiceReady = false,
                voiceEnginePackage = null,
                utterancesStarted = 0,
                rangeCallbacksObserved = 0,
                speaking = false,
            ),
        )

        PresenceTelemetryStore.clear()

        assertNull(PresenceTelemetryStore.snapshot())
    }
}

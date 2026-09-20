package de.snowworks.ariana.neuro

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class V32InneresWerdenRuntimeTest {
    @Test
    fun v32AddsTrainableModelWithoutBreakingEarlierStages() {
        val runtime = V32InneresWerdenRuntime.createForTest()
        runtime.boot()
        val report = runtime.health()

        assertEquals(32, report.version)
        assertEquals((16..32).toList(), report.stages.map { it.version })
        assertTrue(report.green)
        assertTrue(report.modules.contains("inneres-werden-trainer"))
    }

    @Test
    fun trainingRunsThroughTheExistingLocalSignalFabric() = runTest {
        val runtime = V32InneresWerdenRuntime.createForTest()
        runtime.boot()

        runtime.base.base.emit(
            NeuroSignal(
                channel = NeuroChannel.ACTION_RESULT,
                source = "test",
                payload = mapOf(
                    "train" to "true",
                    "exampleId" to "integration-1",
                    "experience" to "0.5",
                    "valueAlignment" to "0.8",
                    "principleConsistency" to "0.9",
                    "evidenceQuality" to "0.9",
                    "correctionSignal" to "0.2",
                    "targetCoherence" to "0.8",
                ),
            ),
        )

        assertEquals(1L, runtime.trainer.snapshot().steps)
        assertTrue(runtime.snapshot().green)
    }
}

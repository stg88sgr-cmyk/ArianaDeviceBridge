package de.snowworks.ariana.neuro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InneresWerdenPersistenceTest {
    private fun trainedSnapshot(): InneresWerdenModelSnapshot {
        val model = InneresWerdenModel()
        val example = InneresWerdenExample(
            id = "persist-1",
            features = InneresWerdenFeatures(
                experience = 0.6,
                valueAlignment = 0.9,
                principleConsistency = 0.8,
                evidenceQuality = 0.9,
                correctionSignal = 0.2,
            ),
            targetCoherence = 0.85,
        )
        repeat(10) { model.train(example) }
        return model.snapshot()
    }

    @Test
    fun snapshotSurvivesRuntimeRecreation() {
        val store = InMemoryInneresWerdenSnapshotStore()
        val first = V32InneresWerdenRuntime.createForTest(store)
        first.boot()

        val snapshot = trainedSnapshot()
        assertTrue(store.save(snapshot))

        val second = V32InneresWerdenRuntime.createForTest(store)
        second.boot()

        assertEquals(snapshot, second.trainer.snapshot())
        assertTrue(second.snapshot().green)
        assertTrue(second.snapshot().persistenceHealthy)
    }

    @Test
    fun trainingPersistsLatestSnapshotAndReportsPersistence() = kotlinx.coroutines.test.runTest {
        val store = InMemoryInneresWerdenSnapshotStore()
        val runtime = V32InneresWerdenRuntime.createForTest(store)
        runtime.boot()

        runtime.base.base.emit(
            NeuroSignal(
                channel = NeuroChannel.ACTION_RESULT,
                source = "test",
                payload = mapOf(
                    "train" to "true",
                    "exampleId" to "persist-2",
                    "experience" to "0.5",
                    "valueAlignment" to "0.8",
                    "principleConsistency" to "0.9",
                    "evidenceQuality" to "0.9",
                    "correctionSignal" to "0.2",
                    "targetCoherence" to "0.8",
                ),
            ),
        )

        assertEquals(runtime.trainer.snapshot(), store.load())
        assertEquals("true", runtime.trainer.persistenceHealthy.toString())
        assertEquals("true", runtime.snapshot().persistenceHealthy.toString())
    }
}

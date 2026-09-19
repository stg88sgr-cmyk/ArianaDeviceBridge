package de.snowworks.ariana.neuro

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InneresWerdenModelTest {
    private fun example() = InneresWerdenExample(
        id = "stable",
        features = InneresWerdenFeatures(
            experience = 0.7,
            valueAlignment = 0.9,
            principleConsistency = 0.9,
            evidenceQuality = 0.8,
            correctionSignal = 0.2,
        ),
        targetCoherence = 0.9,
    )

    @Test
    fun modelActuallyChangesParametersWhenTraining() {
        val model = InneresWerdenModel()
        val before = model.snapshot()
        repeat(20) { model.train(example()) }
        val after = model.snapshot()
        assertEquals(20L, after.steps)
        assertTrue(before.weights != after.weights || before.bias != after.bias)
    }

    @Test
    fun repeatedTrainingReducesLoss() {
        val model = InneresWerdenModel()
        val initial = model.evaluate(listOf(example()))
        repeat(100) { model.train(example()) }
        val trained = model.evaluate(listOf(example()))
        assertTrue("expected loss to decrease: $initial -> $trained", trained < initial)
    }

    @Test
    fun trainerConsumesExplicitTargetsAndNeverEmitsActionRequests() = runTest {
        val trainer = InneresWerdenTrainer()
        val outputs = trainer.onSignal(
            NeuroSignal(
                channel = NeuroChannel.ACTION_RESULT,
                source = "test",
                payload = mapOf(
                    "train" to "true",
                    "exampleId" to "mita-01",
                    "experience" to "0.6",
                    "valueAlignment" to "0.9",
                    "principleConsistency" to "0.8",
                    "evidenceQuality" to "0.9",
                    "correctionSignal" to "0.3",
                    "targetCoherence" to "0.85",
                ),
            ),
        )
        assertEquals(1, outputs.size)
        assertEquals(NeuroChannel.MEMORY, outputs.single().channel)
        assertEquals("inneres-werden-v1", outputs.single().payload["model"])
        assertEquals("1", outputs.single().payload["trainingStep"])
        assertTrue(outputs.none { it.channel == NeuroChannel.ACTION_REQUEST })
    }

    @Test
    fun malformedTrainingSignalDoesNotChangeModel() = runTest {
        val trainer = InneresWerdenTrainer()
        val before = trainer.snapshot()
        val outputs = trainer.onSignal(
            NeuroSignal(
                channel = NeuroChannel.USER_INTENT,
                source = "test",
                payload = mapOf("train" to "true"),
            ),
        )
        assertTrue(outputs.isEmpty())
        assertEquals(before, trainer.snapshot())
    }

    @Test
    fun featuresAreBoundedBeforeTraining() {
        val vector = InneresWerdenFeatures(
            experience = 9.0,
            valueAlignment = -2.0,
            principleConsistency = 3.0,
            evidenceQuality = 4.0,
            correctionSignal = -9.0,
        ).asVector()
        assertEquals(1.0, vector[0], 0.0)
        assertEquals(0.0, vector[1], 0.0)
        assertEquals(1.0, vector[2], 0.0)
        assertEquals(1.0, vector[3], 0.0)
        assertEquals(-1.0, vector[4], 0.0)
    }
}

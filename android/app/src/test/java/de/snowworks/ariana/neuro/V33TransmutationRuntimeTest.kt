package de.snowworks.ariana.neuro

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class V33TransmutationRuntimeTest {

    @Test
    fun readsFromV32SnapshotWithoutMutatingIt() {
        val v32 = V32InneresWerdenRuntime.createForTest()
        v32.boot()
        val before = v32.snapshot()

        val state = V33TransmutationRuntime.createForTest(v32).evaluate(before)
        val after = v32.snapshot()

        assertNotNull(state)
        assertTrue(state.stability in 0.0..1.0)
        assertTrue(state.version == "V33")
        assertTrue(before == after)
    }

    @Test
    fun healthGateAllowsOnlyConsistentGreenV32Snapshot() {
        val v32 = V32InneresWerdenRuntime.createForTest()
        v32.boot()
        val snapshot = v32.snapshot()

        val gate = V33TransmutationHealthGate.check(snapshot)

        assertTrue(gate.allowed)
        assertTrue(gate.reasons.isEmpty())
    }

    @Test
    fun healthGateBlocksNonGreenSnapshot() {
        val v32 = V32InneresWerdenRuntime.createForTest()
        v32.boot()
        val unhealthy = v32.snapshot().copy(green = false)

        val state = V33TransmutationRuntime.createForTest(v32).evaluateIfHealthy(unhealthy)

        assertTrue(state == null)
        assertTrue(V33TransmutationHealthGate.check(unhealthy).reasons.contains("V32_NOT_GREEN"))
    }

    @Test
    fun healthGateBlocksStageOrRuntimeVersionMismatch() {
        val v32 = V32InneresWerdenRuntime.createForTest()
        v32.boot()
        val snapshot = v32.snapshot()

        val wrongStage = snapshot.copy(stage = 31)
        val wrongRuntime = snapshot.copy(runtimeVersion = 31)

        assertTrue(!V33TransmutationHealthGate.check(wrongStage).allowed)
        assertTrue(!V33TransmutationHealthGate.check(wrongRuntime).allowed)
    }
}

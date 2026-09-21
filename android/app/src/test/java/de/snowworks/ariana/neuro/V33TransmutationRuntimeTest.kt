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
}

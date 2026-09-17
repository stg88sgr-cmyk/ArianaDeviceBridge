package de.snowworks.ariana.neuro

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V31NeuroRuntimeTest {

    @Test
    fun v31BootContainsEveryStageFrom16Through31WithoutGap() {
        val runtime = V31NeuroRuntime.createForTest()
        runtime.boot()

        val report = runtime.health()
        assertEquals(31, report.version)
        assertEquals((16..31).toList(), report.stages.map { it.version })
        assertTrue(report.stages.all { it.healthy })
        assertTrue(report.green)
    }

    @Test
    fun v31SnapshotContainsOnlyBoundedRuntimeMetadata() = runTest {
        val runtime = V31NeuroRuntime.createForTest()
        runtime.boot()
        runtime.base.emit(
            NeuroSignal(
                channel = NeuroChannel.ACTION_RESULT,
                source = "test-dispatcher",
                payload = mapOf(
                    "ok" to "true",
                    "code" to "OK",
                    "privateText" to "must-never-appear-in-v31-snapshot",
                ),
            ),
        )

        val snapshot = runtime.snapshot()
        assertEquals(31, snapshot.stage)
        assertEquals(31, snapshot.runtimeVersion)
        assertTrue(snapshot.green)
        assertEquals(16, snapshot.healthyStages)
        assertEquals(16, snapshot.totalStages)
        assertTrue(snapshot.moduleCount >= 12)
        assertTrue(snapshot.telemetryEntries >= 1)
        assertEquals("ᚨX88⟦444♥∞888⟧", snapshot.coherenceSignature)
        assertFalse(snapshot.toString().contains("must-never-appear-in-v31-snapshot"))
    }

    @Test
    fun v31IsReadOnlyAndLeavesActionBoundaryAtV25() = runTest {
        val runtime = V31NeuroRuntime.createForTest()
        val captured = mutableListOf<NeuroSignal>()
        val probe = object : NeuroModule {
            override val id = "v31-action-boundary-probe"
            override val inputs = setOf(NeuroChannel.ACTION_REQUEST)
            override val outputs = emptySet<NeuroChannel>()

            override suspend fun onSignal(signal: NeuroSignal): List<NeuroSignal> {
                captured += signal
                return emptyList()
            }
        }

        runtime.boot()
        runtime.base.fabric.register(probe)
        runtime.base.emit(
            NeuroSignal(
                channel = NeuroChannel.USER_INTENT,
                source = "test-user",
                payload = mapOf("actionId" to "open_settings"),
            ),
        )

        assertEquals(1, captured.size)
        assertEquals("true", captured.single().payload["requiresSecurityChain"])
        assertEquals("proposal_only", captured.single().payload["executionMode"])
        assertFalse(runtime.snapshot().toString().contains("open_settings"))
    }
}

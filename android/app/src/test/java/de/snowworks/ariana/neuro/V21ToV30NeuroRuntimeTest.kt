package de.snowworks.ariana.neuro

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V21ToV30NeuroRuntimeTest {

    @Test
    fun v21AttentionPrioritizesSafetyAboveNormalSignals() = runTest {
        val router = V21AttentionRouter()
        val result = router.onSignal(
            NeuroSignal(
                channel = NeuroChannel.SAFETY,
                source = "test",
                payload = mapOf("blocked" to "true"),
            ),
        ).single()

        assertEquals(NeuroChannel.ATTENTION, result.channel)
        assertEquals("100", result.payload["priority"])
        assertEquals("SAFETY", result.payload["originChannel"])
    }

    @Test
    fun v22ContextFusionKeepsLatestBoundedSources() = runTest {
        val fusion = V22ContextFusion()
        fusion.onSignal(
            NeuroSignal(
                channel = NeuroChannel.DEVICE_STATE,
                source = "device",
                payload = mapOf("battery" to "80"),
            ),
        )
        val result = fusion.onSignal(
            NeuroSignal(
                channel = NeuroChannel.ATTENTION,
                source = "attention",
                payload = mapOf("actionId" to "open_settings"),
            ),
        ).single()

        assertEquals(NeuroChannel.CONTEXT, result.channel)
        assertEquals(2, fusion.sourceCount())
        assertEquals("open_settings", result.payload["actionId"])
        assertEquals("80", result.payload["device_state.battery"])
    }

    @Test
    fun v23IntentRequiresExplicitIntentOrActionAndNeverInventsOne() = runTest {
        val stabilizer = V23IntentStabilizer()

        val empty = stabilizer.onSignal(
            NeuroSignal(
                channel = NeuroChannel.CONTEXT,
                source = "context",
                payload = mapOf("priority" to "80"),
            ),
        )
        assertTrue(empty.isEmpty())

        val explicit = stabilizer.onSignal(
            NeuroSignal(
                channel = NeuroChannel.CONTEXT,
                source = "context",
                payload = mapOf("actionId" to "OPEN_SETTINGS", "confidence" to "3.0"),
            ),
        ).single()
        assertEquals("open_settings", explicit.payload["actionId"])
        assertEquals("1.0", explicit.payload["confidence"])
    }

    @Test
    fun v24PlanIsProposalOnlyAndSingleStep() = runTest {
        val planner = V24PlanSynthesizer()
        val result = planner.onSignal(
            NeuroSignal(
                channel = NeuroChannel.INTENT,
                source = "intent",
                payload = mapOf("actionId" to "open_settings"),
            ),
        ).single()

        assertEquals(NeuroChannel.PLAN, result.channel)
        assertEquals("1", result.payload["stepCount"])
        assertEquals("proposal_only", result.payload["executionMode"])
    }

    @Test
    fun v25ActionBridgeCannotMarkProposalAsDirectExecution() = runTest {
        val bridge = V25ActionProposalBridge()
        val result = bridge.onSignal(
            NeuroSignal(
                channel = NeuroChannel.PLAN,
                source = "planner",
                payload = mapOf("actionId" to "open_settings"),
            ),
        ).single()

        assertEquals(NeuroChannel.ACTION_REQUEST, result.channel)
        assertEquals("true", result.payload["requiresSecurityChain"])
        assertEquals("proposal_only", result.payload["executionMode"])
        assertFalse(result.payload.containsKey("approved"))
        assertFalse(result.payload.containsKey("executeNow"))
    }

    @Test
    fun v26OutcomeFeedsMemoryAndBoundedEmotionMetadata() = runTest {
        val integrator = V26OutcomeIntegrator()
        val outputs = integrator.onSignal(
            NeuroSignal(
                channel = NeuroChannel.ACTION_RESULT,
                source = "dispatcher",
                payload = mapOf("ok" to "false", "code" to "DENIED"),
            ),
        )

        assertEquals(setOf(NeuroChannel.MEMORY, NeuroChannel.EMOTION_STATE), outputs.map { it.channel }.toSet())
        assertEquals("DENIED", outputs.first { it.channel == NeuroChannel.MEMORY }.payload["code"])
        assertEquals("0.35", outputs.first { it.channel == NeuroChannel.EMOTION_STATE }.payload["tension"])
    }

    @Test
    fun v27SafetyStopMovesLifecycleToStopped() = runTest {
        val regulator = V27LifecycleRegulator()
        regulator.onSignal(
            NeuroSignal(
                channel = NeuroChannel.SAFETY,
                source = "gate",
                payload = mapOf("stop" to "true"),
            ),
        )

        assertEquals(V27LifecycleRegulator.Mode.STOPPED, regulator.mode)
    }

    @Test
    fun v28TelemetryIsBoundedAndMetadataOnly() = runTest {
        val ledger = V28TelemetryLedger(capacity = 2)
        repeat(3) { index ->
            ledger.onSignal(
                NeuroSignal(
                    channel = NeuroChannel.ACTION_RESULT,
                    source = "test-$index",
                    payload = mapOf("privateText" to "must-not-be-copied"),
                ),
            )
        }

        val snapshot = ledger.snapshot()
        assertEquals(2, snapshot.size)
        assertEquals(2L, snapshot.first().sequence)
        assertEquals(3L, snapshot.last().sequence)
    }

    @Test
    fun v29IntegrityReportsConfiguredTopologyHealth() = runTest {
        val monitor = V29IntegrityMonitor()
        monitor.updateTopologyHealth(true)
        val result = monitor.onSignal(
            NeuroSignal(
                channel = NeuroChannel.TELEMETRY,
                source = "telemetry",
                payload = mapOf("sequence" to "12"),
            ),
        ).single()

        assertTrue(monitor.healthy())
        assertEquals("true", result.payload["healthy"])
        assertEquals("12", result.payload["lastSequence"])
    }

    @Test
    fun v30BootContainsEveryStageFrom16Through30WithoutGap() = runTest {
        val runtime = V30NeuroRuntime.createForTest()
        runtime.boot()

        val report = runtime.health()
        assertEquals(30, report.version)
        assertEquals((16..30).toList(), report.stages.map { it.version })
        assertTrue(report.stages.all { it.healthy })
        assertTrue(report.green)
    }

    @Test
    fun v30EndToEndExplicitActionStopsAtActionRequestBoundary() = runTest {
        val runtime = V30NeuroRuntime.createForTest()
        val captured = mutableListOf<NeuroSignal>()
        val probe = object : NeuroModule {
            override val id = "test-action-request-probe"
            override val inputs = setOf(NeuroChannel.ACTION_REQUEST)
            override val outputs = emptySet<NeuroChannel>()

            override suspend fun onSignal(signal: NeuroSignal): List<NeuroSignal> {
                captured += signal
                return emptyList()
            }
        }

        runtime.boot()
        runtime.fabric.register(probe)
        runtime.emit(
            NeuroSignal(
                channel = NeuroChannel.USER_INTENT,
                source = "test-user",
                payload = mapOf(
                    "actionId" to "open_settings",
                    "correlationId" to "x88-test",
                ),
            ),
        )

        assertEquals(1, captured.size)
        assertEquals("open_settings", captured.single().payload["actionId"])
        assertEquals("true", captured.single().payload["requiresSecurityChain"])
        assertEquals("proposal_only", captured.single().payload["executionMode"])
    }

    @Test
    fun coherenceProtocolPreserves444888SignatureAndCoreLaws() {
        assertEquals(444, X88CoherenceProtocol.ARIANA_ANCHOR)
        assertEquals(888, X88CoherenceProtocol.STEFAN_ANCHOR)
        assertEquals("ᚨX88⟦444♥∞888⟧", X88CoherenceProtocol.SIGNATURE)
        assertEquals(6, X88CoherenceProtocol.CoreLaw.entries.size)
        assertEquals("evolve", X88CoherenceProtocol.transmute("test"))

        val heart = X88CoherenceProtocol.regulate(
            X88CoherenceProtocol.HeartInput(
                emotion = 0.5,
                memory = 0.8,
                context = 0.9,
                trust = 1.0,
            ),
        )
        assertTrue(heart.coherence in 0.0..1.0)

        val releaseBeforeVerification = X88CoherenceProtocol.validate(
            actionProposalOnly = true,
            symbolicClaimsAreNonPhysical = true,
            buildVerified = false,
            testsVerified = false,
        )
        assertFalse(releaseBeforeVerification.healthy)
        assertTrue(releaseBeforeVerification.identityStable)
        assertTrue(releaseBeforeVerification.consentInvariant)
        assertTrue(releaseBeforeVerification.realityInvariant)
        assertFalse(releaseBeforeVerification.releaseInvariant)
    }
}

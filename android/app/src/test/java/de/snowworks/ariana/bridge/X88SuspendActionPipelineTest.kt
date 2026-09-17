package de.snowworks.ariana.bridge

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class X88SuspendActionPipelineTest {

    @Test
    fun successfulDispatchBecomesCanonicalArianaResult() = runTest {
        val chain = chain(
            policy = ActionPolicy.Decision.SAFE,
            confirmation = ConfirmationDecision.Approved,
            dispatcher = SuspendActionDispatcher { _, _ ->
                DispatchResult(ok = true, code = "DEVICE_OK", message = "done")
            },
        )

        val result = X88ResultTransmuter(chain).execute(
            SecurityRequest(
                actionId = "get_device_status",
                token = "ok",
            ),
        )

        assertTrue(result.ok)
        assertEquals(ArianaResult.Status.OK, result.status)
        assertEquals("DEVICE_OK", result.code)
        assertEquals("x88-suspend-final-v1", result.metadata["pipeline"])
        assertEquals(
            "TOKEN_AUTH>INPUT_VALIDATION>ACTION_POLICY>PERMISSION_RESOLVER>CONFIRMATION_GATE>ACTION_DISPATCHER",
            result.metadata["trace"],
        )
    }

    @Test
    fun pendingConfirmationIsAcknowledgedWithoutDispatch() = runTest {
        var dispatched = false
        val chain = chain(
            policy = ActionPolicy.Decision.CONFIRM,
            confirmation = ConfirmationDecision.Pending("proposal-88"),
            dispatcher = SuspendActionDispatcher { _, _ ->
                dispatched = true
                DispatchResult(ok = true)
            },
        )

        val result = X88ResultTransmuter(chain).execute(
            SecurityRequest(
                actionId = "open_settings",
                token = "ok",
            ),
        )

        assertFalse(dispatched)
        assertTrue(result.ok)
        assertEquals(ArianaResult.Status.ACKNOWLEDGED, result.status)
        assertEquals("CONFIRMATION_REQUIRED", result.code)
        assertEquals("proposal-88", result.metadata["proposalId"])
        assertEquals("true", result.metadata["requiresUserConfirmation"])
    }

    @Test
    fun authenticationFailureBecomesDeniedArianaResult() = runTest {
        val chain = SuspendSecurityChain(
            tokenAuthenticator = TokenAuthenticator { false },
            inputValidator = ActionInputValidator(),
            policyEvaluator = PolicyEvaluator {
                ActionPolicy.Evaluation(it, ActionPolicy.Decision.SAFE, "test")
            },
            permissionResolver = PermissionResolver { PermissionResolution.Ready },
            confirmationGate = ConfirmationGate { _, _, _ -> ConfirmationDecision.Approved },
            actionDispatcher = SuspendActionDispatcher { _, _ -> DispatchResult(ok = true) },
        )

        val result = X88ResultTransmuter(chain).execute(
            SecurityRequest(actionId = "get_device_status", token = "wrong"),
        )

        assertFalse(result.ok)
        assertEquals(ArianaResult.Status.DENIED, result.status)
        assertEquals("UNAUTHORIZED", result.code)
        assertEquals("TOKEN_AUTH", result.metadata["trace"])
    }

    @Test
    fun timeoutBecomesNormalArianaResult() = runTest {
        val chain = chain(
            policy = ActionPolicy.Decision.SAFE,
            confirmation = ConfirmationDecision.Approved,
            dispatcher = SuspendActionDispatcher { _, _ ->
                delay(1_000)
                DispatchResult(ok = true)
            },
        )

        val result = X88ResultTransmuter(chain, timeoutMs = 10).execute(
            SecurityRequest(actionId = "get_device_status", token = "ok"),
        )

        assertFalse(result.ok)
        assertEquals(ArianaResult.Status.ERROR, result.status)
        assertEquals("EXECUTION_TIMEOUT", result.code)
    }

    @Test
    fun externalCoroutineCancellationIsNeverSwallowed() = runTest {
        try {
            ArianaResultExecutor.execute("get_device_status") {
                throw CancellationException("caller cancelled")
            }
            fail("CancellationException must be rethrown")
        } catch (expected: CancellationException) {
            assertEquals("caller cancelled", expected.message)
        }
    }

    private fun chain(
        policy: ActionPolicy.Decision,
        confirmation: ConfirmationDecision,
        dispatcher: SuspendActionDispatcher,
    ) = SuspendSecurityChain(
        tokenAuthenticator = TokenAuthenticator { it == "ok" },
        inputValidator = ActionInputValidator(),
        policyEvaluator = PolicyEvaluator { actionId ->
            ActionPolicy.Evaluation(
                action = actionId,
                decision = policy,
                reason = "test",
            )
        },
        permissionResolver = PermissionResolver { PermissionResolution.Ready },
        confirmationGate = ConfirmationGate { _, _, _ -> confirmation },
        actionDispatcher = dispatcher,
    )
}

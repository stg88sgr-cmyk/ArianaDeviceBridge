package de.snowworks.ariana.bridge

import de.snowworks.ariana.Feature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityChainTest {

    @Test
    fun safeActionTraversesCanonicalOrder() {
        var dispatched = false
        val chain = SecurityChain(
            tokenAuthenticator = TokenAuthenticator { it == "token" },
            inputValidator = ActionInputValidator(),
            policyEvaluator = PolicyEvaluator { action ->
                ActionPolicy.Evaluation(action, ActionPolicy.Decision.SAFE, "SAFE")
            },
            permissionResolver = PermissionResolver { PermissionResolution.Ready },
            confirmationGate = ConfirmationGate { _, _, _ -> ConfirmationDecision.Approved },
            actionDispatcher = ActionDispatcher { _, _ ->
                dispatched = true
                DispatchResult(ok = true)
            },
        )

        val result = chain.execute(
            SecurityRequest(
                actionId = "get_device_status",
                token = "token",
            ),
        )

        assertTrue(result is SecurityDecision.Dispatched)
        assertTrue(dispatched)
        assertEquals(
            listOf(
                SecurityStage.TOKEN_AUTH,
                SecurityStage.INPUT_VALIDATION,
                SecurityStage.ACTION_POLICY,
                SecurityStage.PERMISSION_RESOLVER,
                SecurityStage.CONFIRMATION_GATE,
                SecurityStage.ACTION_DISPATCHER,
            ),
            result.trace,
        )
    }

    @Test
    fun reservedCallerAuthorityIsRejectedBeforePolicy() {
        var policyCalled = false
        var dispatched = false
        val chain = SecurityChain(
            tokenAuthenticator = TokenAuthenticator { true },
            inputValidator = ActionInputValidator(),
            policyEvaluator = PolicyEvaluator { action ->
                policyCalled = true
                ActionPolicy.Evaluation(action, ActionPolicy.Decision.SAFE, "SAFE")
            },
            permissionResolver = PermissionResolver { PermissionResolution.Ready },
            confirmationGate = ConfirmationGate { _, _, _ -> ConfirmationDecision.Approved },
            actionDispatcher = ActionDispatcher { _, _ ->
                dispatched = true
                DispatchResult(ok = true)
            },
        )

        val result = chain.execute(
            SecurityRequest(
                actionId = "get_device_status",
                token = "token",
                payload = mapOf("confirmationLevel" to "SAFE"),
            ),
        )

        assertTrue(result is SecurityDecision.Denied)
        assertEquals("CALLER_AUTHORITY_FORBIDDEN", (result as SecurityDecision.Denied).code)
        assertFalse(policyCalled)
        assertFalse(dispatched)
        assertEquals(
            listOf(SecurityStage.TOKEN_AUTH, SecurityStage.INPUT_VALIDATION),
            result.trace,
        )
    }

    @Test
    fun missingPermissionStopsBeforeConfirmation() {
        var confirmationCalled = false
        var dispatched = false
        val chain = SecurityChain(
            tokenAuthenticator = TokenAuthenticator { true },
            inputValidator = ActionInputValidator(),
            policyEvaluator = PolicyEvaluator { action ->
                ActionPolicy.Evaluation(action, ActionPolicy.Decision.CONFIRM, "CONFIRM")
            },
            permissionResolver = PermissionResolver {
                PermissionResolution.Missing(setOf(Feature.CAMERA))
            },
            confirmationGate = ConfirmationGate { _, _, _ ->
                confirmationCalled = true
                ConfirmationDecision.Approved
            },
            actionDispatcher = ActionDispatcher { _, _ ->
                dispatched = true
                DispatchResult(ok = true)
            },
        )

        val result = chain.execute(
            SecurityRequest(
                actionId = "camera_start",
                token = "token",
            ),
        )

        assertTrue(result is SecurityDecision.Denied)
        assertEquals("PERMISSION_REQUIRED", (result as SecurityDecision.Denied).code)
        assertFalse(confirmationCalled)
        assertFalse(dispatched)
        assertEquals(SecurityStage.PERMISSION_RESOLVER, result.trace.last())
    }

    @Test
    fun confirmActionCanReturnPendingWithoutDispatch() {
        var dispatched = false
        val chain = SecurityChain(
            tokenAuthenticator = TokenAuthenticator { true },
            inputValidator = ActionInputValidator(),
            policyEvaluator = PolicyEvaluator { action ->
                ActionPolicy.Evaluation(action, ActionPolicy.Decision.CONFIRM, "CONFIRM")
            },
            permissionResolver = PermissionResolver { PermissionResolution.Ready },
            confirmationGate = ConfirmationGate { _, _, _ -> ConfirmationDecision.Pending("proposal-1") },
            actionDispatcher = ActionDispatcher { _, _ ->
                dispatched = true
                DispatchResult(ok = true)
            },
        )

        val result = chain.execute(
            SecurityRequest(
                actionId = "camera_start",
                token = "token",
            ),
        )

        assertTrue(result is SecurityDecision.PendingConfirmation)
        assertEquals("proposal-1", (result as SecurityDecision.PendingConfirmation).proposalId)
        assertFalse(dispatched)
        assertEquals(SecurityStage.CONFIRMATION_GATE, result.trace.last())
    }

    @Test
    fun invalidTokenStopsAtFirstGate() {
        var policyCalled = false
        val chain = SecurityChain(
            tokenAuthenticator = TokenAuthenticator { false },
            inputValidator = ActionInputValidator(),
            policyEvaluator = PolicyEvaluator { action ->
                policyCalled = true
                ActionPolicy.Evaluation(action, ActionPolicy.Decision.SAFE, "SAFE")
            },
            permissionResolver = PermissionResolver { PermissionResolution.Ready },
            confirmationGate = ConfirmationGate { _, _, _ -> ConfirmationDecision.Approved },
            actionDispatcher = ActionDispatcher { _, _ -> DispatchResult(ok = true) },
        )

        val result = chain.execute(
            SecurityRequest(
                actionId = "get_device_status",
                token = "wrong",
            ),
        )

        assertTrue(result is SecurityDecision.Denied)
        assertEquals("UNAUTHORIZED", (result as SecurityDecision.Denied).code)
        assertFalse(policyCalled)
        assertEquals(listOf(SecurityStage.TOKEN_AUTH), result.trace)
    }
}

package de.snowworks.ariana.bridge

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/** Suspend-capable dispatcher used by the final X-88 action path. */
fun interface SuspendActionDispatcher {
    suspend fun dispatch(
        descriptor: ActionDescriptor,
        payload: Map<String, String>,
    ): DispatchResult
}

/**
 * Suspend version of the canonical SecurityChain.
 *
 * The gate order intentionally mirrors SecurityChain exactly:
 * TokenAuth -> InputValidator -> ActionPolicy -> PermissionResolver ->
 * ConfirmationGate -> ActionDispatcher.
 *
 * No Android permission dialog or external side effect is created by the gates.
 * Only the final dispatcher may execute a previously authorized capability.
 */
class SuspendSecurityChain(
    private val tokenAuthenticator: TokenAuthenticator,
    private val inputValidator: ActionInputValidator,
    private val policyEvaluator: PolicyEvaluator,
    private val permissionResolver: PermissionResolver,
    private val confirmationGate: ConfirmationGate,
    private val actionDispatcher: SuspendActionDispatcher,
) {
    suspend fun execute(request: SecurityRequest): SecurityDecision {
        val trace = mutableListOf<SecurityStage>()

        trace += SecurityStage.TOKEN_AUTH
        if (!tokenAuthenticator.authenticate(request.token)) {
            return denied("UNAUTHORIZED", "Token authentication failed.", trace)
        }

        trace += SecurityStage.INPUT_VALIDATION
        inputValidator.validate(request)?.let { code ->
            return denied(code, "Input validation failed.", trace)
        }

        trace += SecurityStage.ACTION_POLICY
        val descriptor = ActionDescriptorRegistry.find(request.actionId)
            ?: return denied("ACTION_NOT_ALLOWLISTED", "Action is not allowlisted.", trace)

        ActionDescriptorRegistry.validatePayload(descriptor, request.payload)
            .exceptionOrNull()
            ?.let { error ->
                return denied(
                    error.message ?: "INVALID_PAYLOAD",
                    "Payload does not match the canonical descriptor.",
                    trace,
                )
            }

        val policy = policyEvaluator.evaluate(descriptor.actionId)
        if (policy.decision == ActionPolicy.Decision.BLOCKED) {
            return denied(policy.reason, "Action policy denied execution.", trace)
        }

        trace += SecurityStage.PERMISSION_RESOLVER
        when (val permission = permissionResolver.resolve(descriptor)) {
            PermissionResolution.Ready -> Unit
            is PermissionResolution.Missing -> {
                val ids = permission.features.map { it.id }.sorted().joinToString(",")
                return denied(
                    permission.reason,
                    "Required capability permission is not ready: $ids",
                    trace,
                )
            }
        }

        trace += SecurityStage.CONFIRMATION_GATE
        when (val confirmation = confirmationGate.evaluate(descriptor, policy, request.approvalGrantId)) {
            ConfirmationDecision.Approved -> Unit
            is ConfirmationDecision.Pending -> {
                return SecurityDecision.PendingConfirmation(
                    descriptor = descriptor,
                    proposalId = confirmation.proposalId,
                    trace = trace.toList(),
                )
            }
            is ConfirmationDecision.Denied -> {
                return denied(confirmation.code, confirmation.message, trace)
            }
        }

        trace += SecurityStage.ACTION_DISPATCHER
        val dispatch = actionDispatcher.dispatch(descriptor, request.payload)
        return SecurityDecision.Dispatched(
            descriptor = descriptor,
            result = dispatch,
            trace = trace.toList(),
        )
    }

    private fun denied(
        code: String,
        message: String,
        trace: List<SecurityStage>,
    ): SecurityDecision.Denied = SecurityDecision.Denied(
        code = code,
        message = message,
        trace = trace.toList(),
    )
}

/**
 * Stable coroutine boundary for X-88 execution.
 *
 * Timeout becomes a normal ArianaResult. External coroutine cancellation is
 * deliberately rethrown so callers retain structured-concurrency control.
 */
object ArianaResultExecutor {
    const val DEFAULT_TIMEOUT_MS = 15_000L

    suspend fun execute(
        actionId: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        block: suspend () -> ArianaResult,
    ): ArianaResult {
        require(timeoutMs > 0L) { "timeoutMs must be > 0" }

        return try {
            withTimeout(timeoutMs) { block() }
        } catch (_: TimeoutCancellationException) {
            ArianaResult.error(
                actionId = actionId,
                code = "EXECUTION_TIMEOUT",
                message = "X-88 action exceeded its execution timeout.",
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (security: SecurityException) {
            ArianaResult.blocked(
                actionId = actionId,
                code = "SECURITY_EXCEPTION",
                message = security.message ?: "Android security policy blocked the action.",
            )
        } catch (error: Throwable) {
            ArianaResult.error(
                actionId = actionId,
                code = "EXECUTION_EXCEPTION",
                message = error.javaClass.simpleName.ifBlank { "Throwable" },
            )
        }
    }
}

/** Converts every suspend SecurityDecision into the canonical ArianaResult. */
class X88ResultTransmuter(
    private val chain: SuspendSecurityChain,
    private val timeoutMs: Long = ArianaResultExecutor.DEFAULT_TIMEOUT_MS,
) {
    suspend fun execute(request: SecurityRequest): ArianaResult =
        ArianaResultExecutor.execute(request.actionId, timeoutMs) {
            when (val decision = chain.execute(request)) {
                is SecurityDecision.Dispatched -> {
                    val metadata = traceMetadata(decision.trace) + mapOf(
                        "descriptor" to decision.descriptor.actionId,
                    )
                    if (decision.result.ok) {
                        ArianaResult.ok(
                            actionId = request.actionId,
                            code = decision.result.code ?: "DISPATCH_OK",
                            message = decision.result.message ?: "Action dispatched.",
                            metadata = metadata,
                        )
                    } else {
                        ArianaResult.error(
                            actionId = request.actionId,
                            code = decision.result.code ?: "DISPATCH_FAILED",
                            message = decision.result.message ?: "Action dispatcher reported failure.",
                            metadata = metadata,
                        )
                    }
                }

                is SecurityDecision.PendingConfirmation -> ArianaResult.acknowledged(
                    actionId = request.actionId,
                    code = "CONFIRMATION_REQUIRED",
                    message = "Action accepted and is waiting for visible user confirmation.",
                    metadata = traceMetadata(decision.trace) + mapOf(
                        "descriptor" to decision.descriptor.actionId,
                        "proposalId" to decision.proposalId,
                        "requiresUserConfirmation" to "true",
                    ),
                )

                is SecurityDecision.Denied -> ArianaResult.denied(
                    actionId = request.actionId,
                    code = decision.code,
                    message = decision.message,
                    metadata = traceMetadata(decision.trace),
                )
            }
        }

    private fun traceMetadata(trace: List<SecurityStage>): Map<String, String> = mapOf(
        "trace" to trace.joinToString(">") { it.name },
        "pipeline" to "x88-suspend-final-v1",
    )
}

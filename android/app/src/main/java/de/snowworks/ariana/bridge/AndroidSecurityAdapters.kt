package de.snowworks.ariana.bridge

import android.content.Context
import de.snowworks.ariana.Feature
import de.snowworks.ariana.PermissionStore
import java.security.MessageDigest

/** Constant-time token comparison for bridge-bound execution. */
class ConstantTimeTokenAuthenticator(
    private val expectedToken: String,
) : TokenAuthenticator {
    override fun authenticate(token: String?): Boolean {
        if (token == null) return false
        return MessageDigest.isEqual(
            expectedToken.toByteArray(Charsets.UTF_8),
            token.toByteArray(Charsets.UTF_8),
        )
    }
}

/** Side-effect-free policy adapter for SecurityChain execution. */
class AndroidPolicyEvaluator(
    context: Context,
) : PolicyEvaluator {
    private val appContext = context.applicationContext

    override fun evaluate(actionId: String): ActionPolicy.Evaluation =
        ActionPolicy.evaluateForExecution(appContext, actionId)
}

/**
 * Read-only Android capability resolver.
 *
 * It never launches a permission dialog. SCREEN is deliberately deferred because
 * MediaProjection consent must be collected by the visible execution flow for
 * every new screen-share session.
 */
class AndroidPermissionResolver(
    context: Context,
) : PermissionResolver {
    private val store = PermissionStore(context.applicationContext)

    override fun resolve(descriptor: ActionDescriptor): PermissionResolution {
        val missing = descriptor.requiredFeatures
            .filterNot { feature ->
                when (feature) {
                    Feature.SCREEN -> true
                    else -> store.isGranted(feature)
                }
            }
            .toSet()

        return if (missing.isEmpty()) {
            PermissionResolution.Ready
        } else {
            PermissionResolution.Missing(
                features = missing,
                reason = if (Feature.NOTIFY_READ in missing) {
                    "SETTINGS_OR_PERMISSION_REQUIRED"
                } else {
                    "PERMISSION_REQUIRED"
                },
            )
        }
    }
}

/**
 * Human-in-the-loop gate. SAFE actions pass immediately. CONFIRM actions require
 * an exact one-time approval grant; without one, a pending proposal is created.
 */
object LocalApprovalConfirmationGate : ConfirmationGate {
    override fun evaluate(
        descriptor: ActionDescriptor,
        policy: ActionPolicy.Evaluation,
        approvalGrantId: String?,
    ): ConfirmationDecision {
        return when (policy.decision) {
            ActionPolicy.Decision.BLOCKED -> ConfirmationDecision.Denied(
                code = policy.reason,
                message = "Action policy blocked execution.",
            )

            ActionPolicy.Decision.SAFE -> ConfirmationDecision.Approved

            ActionPolicy.Decision.CONFIRM -> {
                if (approvalGrantId.isNullOrBlank()) {
                    val pending = ActionApprovalStore.createPending(
                        action = descriptor.actionId,
                        reason = policy.reason,
                    )
                    ConfirmationDecision.Pending(pending.id)
                } else {
                    val grant = ActionApprovalStore.consumeGrant(
                        grantId = approvalGrantId,
                        action = descriptor.actionId,
                    )
                    if (grant != null) {
                        ConfirmationDecision.Approved
                    } else {
                        ConfirmationDecision.Denied(
                            code = "APPROVAL_INVALID_OR_EXPIRED",
                            message = "Exact one-time approval grant is missing or expired.",
                        )
                    }
                }
            }
        }
    }
}

package de.snowworks.ariana.bridge

import android.content.Context
import de.snowworks.ariana.ArianaGate

/**
 * Classifies proposed device actions without executing them.
 *
 * Authority is derived from ActionDescriptorRegistry. Callers cannot choose
 * confirmation level or capabilities. CONFIRM decisions are registered in the
 * volatile local approval store so the user can review them on-device.
 */
object ActionPolicy {
    enum class Decision { SAFE, CONFIRM, BLOCKED }

    data class Evaluation(
        val action: String,
        val decision: Decision,
        val reason: String,
        val executable: Boolean = false,
        val pendingProposalId: String? = null,
    )

    private val actionPattern = Regex("^[a-z][a-z0-9_]{0,79}$")

    fun evaluate(context: Context, rawAction: String): Evaluation {
        val action = rawAction.trim().lowercase()
        if (!actionPattern.matches(action)) {
            return Evaluation(
                action = action.take(80),
                decision = Decision.BLOCKED,
                reason = "INVALID_ACTION_NAME",
            )
        }

        val descriptor = ActionDescriptorRegistry.find(action)
            ?: return Evaluation(
                action = action,
                decision = Decision.BLOCKED,
                reason = "ACTION_NOT_ALLOWLISTED",
            )

        val gate = ArianaGate(context.applicationContext)
        if ((!gate.isMasterEnabled || gate.isBlocked) &&
            descriptor.confirmation != ConfirmationRequirement.SAFE
        ) {
            return Evaluation(
                action = action,
                decision = Decision.BLOCKED,
                reason = "MASTER_DISABLED",
            )
        }

        return when (descriptor.confirmation) {
            ConfirmationRequirement.SAFE -> Evaluation(
                action = action,
                decision = Decision.SAFE,
                reason = "NON_ESCALATING_OR_STATUS_ACTION",
            )

            ConfirmationRequirement.CONFIRM -> {
                val reason = "VISIBLE_USER_CONFIRMATION_REQUIRED"
                val pending = ActionApprovalStore.createPending(action, reason)
                Evaluation(
                    action = action,
                    decision = Decision.CONFIRM,
                    reason = reason,
                    pendingProposalId = pending.id,
                )
            }
        }
    }
}

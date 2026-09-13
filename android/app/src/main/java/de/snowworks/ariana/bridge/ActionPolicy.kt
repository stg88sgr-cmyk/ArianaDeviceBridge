package de.snowworks.ariana.bridge

import android.content.Context
import de.snowworks.ariana.ArianaGate

/**
 * Classifies proposed device actions without executing them.
 *
 * CONFIRM decisions are additionally registered in the volatile local approval
 * store so the user can review them on-device. No device action is started here.
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

    private val safe = setOf(
        "get_device_status",
        "get_permission_status",
        "get_active_sessions",
        "camera_stop",
        "microphone_stop",
        "screen_stop",
        "stop_all",
        "presence_clear",
    )

    private val confirm = setOf(
        "camera_start",
        "microphone_start",
        "screen_start",
        "camera_snapshot",
        "notification_list",
        "notification_clear",
        "presence_thinking",
        "presence_done",
        "presence_attention",
        "presence_quiet",
        "presence_test",
    )

    fun evaluate(context: Context, rawAction: String): Evaluation {
        val action = rawAction.trim().lowercase()
        if (!actionPattern.matches(action)) {
            return Evaluation(
                action = action.take(80),
                decision = Decision.BLOCKED,
                reason = "INVALID_ACTION_NAME",
            )
        }

        val gate = ArianaGate(context.applicationContext)
        if ((!gate.isMasterEnabled || gate.isBlocked) && action !in safe) {
            return Evaluation(
                action = action,
                decision = Decision.BLOCKED,
                reason = "MASTER_DISABLED",
            )
        }

        return when (action) {
            in safe -> Evaluation(
                action = action,
                decision = Decision.SAFE,
                reason = "NON_ESCALATING_OR_STATUS_ACTION",
            )
            in confirm -> {
                val reason = "VISIBLE_USER_CONFIRMATION_REQUIRED"
                val pending = ActionApprovalStore.createPending(action, reason)
                Evaluation(
                    action = action,
                    decision = Decision.CONFIRM,
                    reason = reason,
                    pendingProposalId = pending.id,
                )
            }
            else -> Evaluation(
                action = action,
                decision = Decision.BLOCKED,
                reason = "ACTION_NOT_ALLOWLISTED",
            )
        }
    }
}

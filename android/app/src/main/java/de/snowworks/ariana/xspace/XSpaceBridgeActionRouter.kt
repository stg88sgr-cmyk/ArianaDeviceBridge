package de.snowworks.ariana.xspace

import android.content.Context
import de.snowworks.ariana.ArianaGate
import de.snowworks.ariana.bridge.ActionApprovalStore
import de.snowworks.ariana.bridge.ActionPolicy
import org.json.JSONObject

/**
 * Single policy boundary for X Space actions coming from Ariana's local bridge.
 *
 * SAFE actions execute immediately. CONFIRM actions create a pending proposal
 * and bind that proposal to the exact reviewed payload. Confirmed execution
 * requires the one-time grant produced by the visible Android approval flow.
 */
object XSpaceBridgeActionRouter {

    private val supported = setOf(
        "xspace_status",
        "xspace_disconnect",
        "xspace_connect",
        "xspace_join",
        "xspace_leave",
        "xspace_speak",
        "xspace_mute",
        "xspace_unmute",
    )

    fun supports(action: String): Boolean = action.trim().lowercase() in supported

    fun dispatch(
        context: Context,
        requestId: String,
        action: String,
        payload: JSONObject = JSONObject(),
    ): JSONObject {
        val normalized = action.trim().lowercase()
        if (normalized !in supported) {
            return error(requestId, "XSPACE_ACTION_NOT_SUPPORTED", "Unbekannte X-Space-Aktion.")
        }

        val evaluation = ActionPolicy.evaluate(context, normalized)
        return when (evaluation.decision) {
            ActionPolicy.Decision.SAFE -> executeSafe(requestId, normalized)

            ActionPolicy.Decision.CONFIRM -> {
                val proposalId = evaluation.pendingProposalId
                    ?: return error(
                        requestId,
                        "XSPACE_CONFIRMATION_STATE_INVALID",
                        "Bestätigungsvorschlag konnte nicht erstellt werden.",
                    )

                XSpaceApprovalBindingStore.register(
                    proposalId = proposalId,
                    action = normalized,
                    payload = payload,
                )
                val review = XSpaceApprovalRequestStore.register(
                    proposalId = proposalId,
                    action = normalized,
                    payload = payload,
                )

                JSONObject()
                    .put("ok", false)
                    .put("requestId", requestId)
                    .put("action", normalized)
                    .put("error", "USER_CONFIRMATION_REQUIRED")
                    .put("message", "Diese X-Space-Aktion benötigt eine sichtbare Bestätigung in der App.")
                    .put("requiresUserConfirmation", true)
                    .put("proposalId", proposalId)
                    .put("reviewSummary", review.summary)
                    .put("expiresAtMs", review.expiresAtMs)
            }

            ActionPolicy.Decision.BLOCKED -> error(
                requestId,
                "XSPACE_ACTION_BLOCKED",
                evaluation.reason.ifBlank { "X-Space-Aktion ist durch die Policy blockiert." },
            )
        }
    }

    /**
     * Call only from the Android approval flow. The exact one-time approval grant
     * and exact payload must both match the original proposal.
     */
    fun dispatchConfirmed(
        context: Context,
        requestId: String,
        action: String,
        grantId: String,
        payload: JSONObject = JSONObject(),
    ): JSONObject {
        val normalized = action.trim().lowercase()
        if (normalized !in supported) {
            return error(requestId, "XSPACE_ACTION_NOT_SUPPORTED", "Unbekannte X-Space-Aktion.")
        }

        val gate = ArianaGate(context.applicationContext)
        if (!gate.isMasterEnabled || gate.isBlocked) {
            return error(requestId, "MASTER_DISABLED", "Master-Zugriff ist deaktiviert.")
        }

        if (normalized == "xspace_status" || normalized == "xspace_disconnect") {
            return executeSafe(requestId, normalized)
        }

        val grant = ActionApprovalStore.consumeGrant(
            grantId = grantId.trim(),
            action = normalized,
        ) ?: return error(
            requestId,
            "XSPACE_APPROVAL_REQUIRED",
            "Passender oder gültiger Einmal-Freigabecode fehlt.",
        )

        if (!XSpaceApprovalBindingStore.consume(
                proposalId = grant.proposalId,
                action = normalized,
                payload = payload,
            )
        ) {
            return error(
                requestId,
                "XSPACE_APPROVAL_PAYLOAD_MISMATCH",
                "Freigabe gehört nicht zu diesem X-Space-Payload.",
            )
        }

        return XSpaceBridgeController.executeConfirmed(
            requestId = requestId,
            action = normalized,
            payload = payload,
        )
    }

    private fun executeSafe(requestId: String, action: String): JSONObject = when (action) {
        "xspace_status" -> XSpaceBridgeController.status(requestId)
        "xspace_disconnect" -> XSpaceBridgeController.disconnect(requestId)
        else -> error(
            requestId,
            "XSPACE_SAFE_ROUTE_MISMATCH",
            "Die Aktion ist nicht als sichere X-Space-Operation implementiert.",
        )
    }

    private fun error(requestId: String, code: String, message: String): JSONObject =
        JSONObject()
            .put("ok", false)
            .put("requestId", requestId)
            .put("error", code)
            .put("message", message)
}

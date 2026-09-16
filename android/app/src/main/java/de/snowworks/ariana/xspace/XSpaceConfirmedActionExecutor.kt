package de.snowworks.ariana.xspace

import android.content.Context
import de.snowworks.ariana.bridge.ActionApprovalStore
import org.json.JSONObject
import java.util.UUID

/**
 * Entry point for the visible Android approval UI.
 *
 * The caller supplies only the proposalId. The exact payload is loaded from the
 * volatile review queue, approved once, then executed through the policy router.
 */
object XSpaceConfirmedActionExecutor {

    fun pending(): List<XSpaceApprovalRequestStore.ReviewRequest> =
        XSpaceApprovalRequestStore.listPending()

    fun approveAndExecute(
        context: Context,
        proposalId: String,
    ): JSONObject {
        val cleanId = proposalId.trim()
        val review = XSpaceApprovalRequestStore.get(cleanId)
            ?: return error("XSPACE_PROPOSAL_NOT_FOUND", "X-Space-Freigabe ist abgelaufen oder unbekannt.")

        val payload = XSpaceApprovalRequestStore.payloadFor(cleanId, review.action)
            ?: return error("XSPACE_PROPOSAL_PAYLOAD_MISSING", "Gespeicherter X-Space-Payload fehlt oder ist ungültig.")

        val grant = ActionApprovalStore.approve(cleanId)
            ?: return error("XSPACE_PROPOSAL_NOT_APPROVABLE", "X-Space-Freigabe konnte nicht bestätigt werden.")

        XSpaceApprovalRequestStore.remove(cleanId)

        return XSpaceBridgeActionRouter.dispatchConfirmed(
            context = context.applicationContext,
            requestId = UUID.randomUUID().toString(),
            action = review.action,
            grantId = grant.id,
            payload = payload,
        ).put("proposalId", cleanId)
    }

    fun deny(proposalId: String): JSONObject {
        val cleanId = proposalId.trim()
        val denied = ActionApprovalStore.deny(cleanId)
        XSpaceApprovalBindingStore.revoke(cleanId)
        XSpaceApprovalRequestStore.remove(cleanId)
        return JSONObject()
            .put("ok", denied)
            .put("proposalId", cleanId)
            .put("action", "xspace_deny")
            .put("error", if (denied) JSONObject.NULL else "XSPACE_PROPOSAL_NOT_FOUND")
    }

    fun revokeAll() {
        XSpaceApprovalRequestStore.revokeAll()
        XSpaceApprovalBindingStore.revokeAll()
    }

    private fun error(code: String, message: String): JSONObject =
        JSONObject()
            .put("ok", false)
            .put("requestId", UUID.randomUUID().toString())
            .put("error", code)
            .put("message", message)
}

package de.snowworks.ariana.xspace

import android.content.Context
import de.snowworks.ariana.bridge.ActionPolicy
import org.json.JSONObject

/**
 * Single policy boundary for X Space actions coming from Ariana's local bridge.
 *
 * SAFE actions execute immediately. CONFIRM actions return a confirmation
 * requirement unless the caller enters through dispatchConfirmed(), which is
 * intended only for a visible Android approval flow. BLOCKED remains blocked.
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
            ActionPolicy.Decision.CONFIRM -> JSONObject()
                .put("ok", false)
                .put("requestId", requestId)
                .put("action", normalized)
                .put("error", "USER_CONFIRMATION_REQUIRED")
                .put("message", "Diese X-Space-Aktion benötigt eine sichtbare Bestätigung in der App.")
                .put("requiresUserConfirmation", true)
                .put("payloadAccepted", payload.length() <= 16)
            ActionPolicy.Decision.BLOCKED -> error(
                requestId,
                "XSPACE_ACTION_BLOCKED",
                evaluation.reason.ifBlank { "X-Space-Aktion ist durch die Policy blockiert." },
            )
        }
    }

    /**
     * Call only from the Android approval flow after the user has explicitly
     * confirmed the exact proposed action and payload.
     */
    fun dispatchConfirmed(
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
            ActionPolicy.Decision.CONFIRM -> XSpaceBridgeController.executeConfirmed(
                requestId = requestId,
                action = normalized,
                payload = payload,
            )
            ActionPolicy.Decision.BLOCKED -> error(
                requestId,
                "XSPACE_ACTION_BLOCKED",
                evaluation.reason.ifBlank { "X-Space-Aktion ist durch die Policy blockiert." },
            )
        }
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

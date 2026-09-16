package de.snowworks.ariana.xspace

import org.json.JSONObject

/**
 * Thin policy-aware execution facade for X Space commands.
 *
 * SAFE operations may be called by the local bridge directly. Operations that
 * affect an external X Space are exposed only through executeConfirmed(), so a
 * visible approval flow can remain the single place that authorizes them.
 */
object XSpaceBridgeController {

    private const val MAX_SPEAK_LENGTH = 2000

    fun status(requestId: String): JSONObject {
        val snapshot = XSpaceGatewayClient.snapshot()
        XSpaceGatewayClient.requestStatus()
        return JSONObject()
            .put("ok", true)
            .put("requestId", requestId)
            .put("model", "x88-xspace-status-v2")
            .put("connected", snapshot.connected)
            .put("connecting", snapshot.connecting)
            .put("joined", snapshot.joined)
            .put("muted", snapshot.muted)
            .put("spaceUrl", snapshot.spaceUrl ?: JSONObject.NULL)
            .put("lastStatus", snapshot.lastStatus ?: JSONObject.NULL)
            .put("lastError", snapshot.lastError ?: JSONObject.NULL)
            .put("lastTranscript", snapshot.lastTranscript ?: JSONObject.NULL)
            .put("lastSpeaker", snapshot.lastSpeaker ?: JSONObject.NULL)
    }

    fun disconnect(requestId: String): JSONObject {
        XSpaceGatewayClient.disconnect()
        return ok(requestId, "xspace_disconnect")
    }

    /**
     * Execute only after the app has visibly approved the corresponding
     * ActionPolicy.CONFIRM proposal. This method does not create approvals.
     */
    fun executeConfirmed(
        requestId: String,
        action: String,
        payload: JSONObject = JSONObject(),
    ): JSONObject = when (action.trim().lowercase()) {
        "xspace_connect" -> {
            val token = payload.optString("gatewayToken").trim().takeIf { it.isNotEmpty() }
            if (!XSpaceGatewayClient.connect(token)) {
                error(requestId, "XSPACE_CONNECT_FAILED", "Gateway-Verbindung konnte nicht gestartet werden.")
            } else {
                ok(requestId, "xspace_connect")
            }
        }

        "xspace_join" -> {
            val spaceUrl = payload.optString("spaceUrl").trim()
            if (spaceUrl.isEmpty()) {
                error(requestId, "INVALID_X_SPACE_URL", "X-Space-URL fehlt.")
            } else if (!XSpaceGatewayClient.join(spaceUrl)) {
                error(requestId, "XSPACE_JOIN_REJECTED", "X-Space-URL ist ungültig oder Gateway ist nicht verbunden.")
            } else {
                ok(requestId, "xspace_join").put("spaceUrl", spaceUrl)
            }
        }

        "xspace_leave" -> commandResult(requestId, "xspace_leave", XSpaceGatewayClient.leave())

        "xspace_speak" -> {
            val text = payload.optString("text").trim().take(MAX_SPEAK_LENGTH)
            if (text.isEmpty()) {
                error(requestId, "EMPTY_XSPACE_TEXT", "Sprechtext fehlt.")
            } else {
                commandResult(requestId, "xspace_speak", XSpaceGatewayClient.speak(text))
                    .put("characters", text.length)
            }
        }

        "xspace_mute" -> commandResult(requestId, "xspace_mute", XSpaceGatewayClient.mute())
        "xspace_unmute" -> commandResult(requestId, "xspace_unmute", XSpaceGatewayClient.unmute())

        else -> error(requestId, "XSPACE_ACTION_NOT_SUPPORTED", "Unbekannte X-Space-Aktion.")
    }

    private fun commandResult(requestId: String, action: String, sent: Boolean): JSONObject =
        if (sent) ok(requestId, action)
        else error(requestId, "XSPACE_GATEWAY_NOT_CONNECTED", "X-Space-Gateway ist nicht verbunden.")

    private fun ok(requestId: String, action: String): JSONObject =
        JSONObject()
            .put("ok", true)
            .put("requestId", requestId)
            .put("action", action)

    private fun error(requestId: String, code: String, message: String): JSONObject =
        JSONObject()
            .put("ok", false)
            .put("requestId", requestId)
            .put("error", code)
            .put("message", message)
}

package de.snowworks.ariana.xspace

import android.content.Context
import de.snowworks.ariana.bridge.BridgeTokenStore
import de.snowworks.ariana.bridge.LocalBridgeServer
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket

/**
 * End-to-end verification for the X Space LocalBridge path without external X mutations.
 *
 * It verifies the real loopback HTTP route. The only CONFIRM action proposed is
 * xspace_speak; the proposal is inspected and then denied. It never approves a
 * mutation and therefore never sends speech or joins/leaves an external Space.
 */
object XSpaceBridgeSelfTest {

    data class Check(
        val name: String,
        val ok: Boolean,
        val detail: String,
    )

    data class Result(
        val ok: Boolean,
        val checks: List<Check>,
        val message: String,
    )

    private data class HttpResponse(
        val status: Int,
        val json: JSONObject?,
    )

    fun run(context: Context): Result {
        if (!LocalBridgeServer.isRunning()) {
            return Result(
                ok = false,
                checks = listOf(Check("LocalBridge", false, "127.0.0.1:${LocalBridgeServer.PORT} läuft nicht.")),
                message = "XSpace v5 Selbsttest nicht gestartet: LocalBridge ist aus.",
            )
        }

        val app = context.applicationContext
        val token = BridgeTokenStore(app).getOrCreate()
        val checks = mutableListOf<Check>()

        val statusResponse = runCatching {
            request(
                token = token,
                body = JSONObject().put("action", "xspace_status").toString(),
            )
        }.getOrElse { error ->
            checks += Check("/action → xspace_status", false, error.javaClass.simpleName)
            return finish(checks)
        }

        val statusHealthy = statusResponse.status == 200 &&
            statusResponse.json?.optBoolean("ok", false) == true &&
            statusResponse.json.optString("model") == "x88-xspace-status-v2"
        checks += Check(
            name = "/action → xspace_status",
            ok = statusHealthy,
            detail = if (statusHealthy) {
                "SAFE-XSpace-Route erreicht den echten Router über 127.0.0.1:${LocalBridgeServer.PORT}."
            } else {
                "HTTP ${statusResponse.status}; SAFE-XSpace-Route liefert keinen gültigen Status."
            },
        )
        if (!statusHealthy) return finish(checks)

        val marker = "X88_SELFTEST_${System.currentTimeMillis()}"
        val speakResponse = runCatching {
            request(
                token = token,
                body = JSONObject()
                    .put("action", "xspace_speak")
                    .put("payload", JSONObject().put("text", marker))
                    .toString(),
            )
        }.getOrElse { error ->
            checks += Check("/action → xspace_speak → approval", false, error.javaClass.simpleName)
            return finish(checks)
        }

        val proposalId = speakResponse.json?.optString("proposalId").orEmpty()
        val confirmationHealthy = speakResponse.status == 400 &&
            speakResponse.json?.optString("error") == "USER_CONFIRMATION_REQUIRED" &&
            proposalId.isNotBlank()
        checks += Check(
            name = "/action → xspace_speak → approval",
            ok = confirmationHealthy,
            detail = if (confirmationHealthy) {
                "CONFIRM-Aktion wurde gestoppt und als sichtbarer Vorschlag angelegt; keine X-Space-Mutation ausgeführt."
            } else {
                "HTTP ${speakResponse.status}; CONFIRM-Grenze wurde nicht wie erwartet erreicht."
            },
        )
        if (!confirmationHealthy) return finish(checks)

        try {
            val review = XSpaceApprovalRequestStore.get(proposalId)
            val reviewHealthy = review != null &&
                review.action == "xspace_speak" &&
                review.summary.contains(marker)
            checks += Check(
                name = "Review-Payload-Bindung",
                ok = reviewHealthy,
                detail = if (reviewHealthy) {
                    "Die sichtbare Review-Zusammenfassung enthält exakt den vorgeschlagenen Testtext."
                } else {
                    "Review-Eintrag fehlt oder zeigt nicht den vorgeschlagenen Payload."
                },
            )

            val denied = XSpaceConfirmedActionExecutor.deny(proposalId)
            XSpaceApprovalNotifier.cancel(app, proposalId)
            val denyHealthy = denied.optBoolean("ok", false) &&
                XSpaceApprovalRequestStore.get(proposalId) == null
            checks += Check(
                name = "Proposal-Verwurf",
                ok = denyHealthy,
                detail = if (denyHealthy) {
                    "Testvorschlag und lokale Freigabe-Benachrichtigung wurden verworfen."
                } else {
                    "Testvorschlag konnte nicht sauber verworfen werden."
                },
            )
        } finally {
            XSpaceConfirmedActionExecutor.deny(proposalId)
            XSpaceApprovalNotifier.cancel(app, proposalId)
        }

        return finish(checks)
    }

    private fun finish(checks: List<Check>): Result {
        val failed = checks.count { !it.ok }
        val passed = checks.size - failed
        return Result(
            ok = failed == 0,
            checks = checks,
            message = if (failed == 0) {
                "XSpace v5: lokale /action → Policy → Review-Kette OK ($passed geprüft)."
            } else {
                "XSpace v5: $failed Fehler, $passed Prüfungen erfolgreich."
            },
        )
    }

    private fun request(token: String, body: String): HttpResponse {
        val bodyBytes = body.toByteArray(Charsets.UTF_8)
        Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", LocalBridgeServer.PORT), CONNECT_TIMEOUT_MS)
            socket.soTimeout = READ_TIMEOUT_MS
            val head = buildString {
                append("POST /action HTTP/1.1\r\n")
                append("Host: 127.0.0.1:${LocalBridgeServer.PORT}\r\n")
                append("X-Ariana-Token: $token\r\n")
                append("Content-Type: application/json; charset=utf-8\r\n")
                append("Accept: application/json\r\n")
                append("Content-Length: ${bodyBytes.size}\r\n")
                append("Connection: close\r\n\r\n")
            }
            socket.getOutputStream().apply {
                write(head.toByteArray(Charsets.US_ASCII))
                write(bodyBytes)
                flush()
            }

            val raw = socket.getInputStream().readBytes().toString(Charsets.UTF_8)
            val headerEnd = raw.indexOf("\r\n\r\n")
            require(headerEnd > 0) { "incomplete HTTP response" }
            val status = raw.substringBefore("\r\n")
                .split(' ')
                .getOrNull(1)
                ?.toIntOrNull()
                ?: error("invalid HTTP status")
            val responseBody = raw.substring(headerEnd + 4)
            val json = if (responseBody.isBlank()) null else JSONObject(responseBody)
            return HttpResponse(status, json)
        }
    }

    private const val CONNECT_TIMEOUT_MS = 1_500
    private const val READ_TIMEOUT_MS = 2_500
}

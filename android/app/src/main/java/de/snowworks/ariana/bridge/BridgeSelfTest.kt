package de.snowworks.ariana.bridge

import android.content.Context
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Side-effect-bounded local bridge verification.
 *
 * The protocol checks never invoke the configured AI provider: the valid bearer
 * dialogue request deliberately carries an empty text, so LocalBridgeServer must
 * reject it at input validation before DialogueRouter.generate() can run.
 * Action proposal and approval checks only operate on in-memory metadata.
 */
object BridgeSelfTest {
    data class Check(
        val name: String,
        val ok: Boolean,
        val detail: String,
        val skipped: Boolean = false,
    )

    data class Result(
        val ok: Boolean,
        val message: String,
        val state: JSONObject? = null,
        val checks: List<Check> = emptyList(),
    )

    private data class HttpResponse(
        val status: Int,
        val json: JSONObject?,
    )

    fun run(context: Context): Result {
        if (!LocalBridgeServer.isRunning()) {
            return Result(false, "Lokale Bridge läuft nicht.")
        }

        val app = context.applicationContext
        val checks = mutableListOf<Check>()
        val token = BridgeTokenStore(app).getOrCreate()

        val stateResponse = runCatching {
            request(
                method = "GET",
                path = "/state",
                headers = mapOf("X-Ariana-Token" to token),
            )
        }.getOrElse { error ->
            return Result(
                false,
                "Bridge-Selbsttest fehlgeschlagen: ${error.javaClass.simpleName}",
                checks = checks,
            )
        }

        val state = stateResponse.json
        val expectedBridge = "127.0.0.1:${LocalBridgeServer.PORT}"
        val stateHealthy = stateResponse.status == 200 &&
            state?.optBoolean("ok", false) == true &&
            state.optString("bridge") == expectedBridge
        checks += Check(
            name = "Loopback /state",
            ok = stateHealthy,
            detail = if (stateHealthy) {
                "$expectedBridge antwortet mit konsistentem State."
            } else {
                "HTTP ${stateResponse.status}; Bridge-State ist nicht konsistent."
            },
        )
        if (!stateHealthy) {
            return finish(checks, state)
        }

        if (DialogueSessionStore.hasActiveSession()) {
            checks += Check(
                name = "Pairing + Bearer + ActionPolicy + Approval",
                ok = true,
                skipped = true,
                detail = "Übersprungen, weil bereits eine aktive X-88-Dialogsession läuft. Diese Sitzung wurde nicht verändert.",
            )
            return finish(checks, state)
        }

        try {
            ActionApprovalStore.revokeAll()

            val pairing = DialogueSessionStore.beginPairing()
            val exchangeBody = JSONObject().put("pairingCode", pairing.code).toString()
            val exchange = request(
                method = "POST",
                path = "/v1/session",
                headers = mapOf("X-X88-Pairing-Code" to pairing.code),
                body = exchangeBody,
            )
            val sessionToken = exchange.json?.optString("token").orEmpty()
            val sessionExpiry = exchange.json?.optLong("expiresAtMs", 0L) ?: 0L
            val exchangeHealthy = exchange.status == 200 &&
                exchange.json?.optBoolean("ok", false) == true &&
                sessionToken.length >= 24 &&
                sessionExpiry > System.currentTimeMillis()
            checks += Check(
                name = "Einmal-Pairing",
                ok = exchangeHealthy,
                detail = if (exchangeHealthy) {
                    "Pairing-Code wurde über /v1/session in eine flüchtige Session getauscht."
                } else {
                    "HTTP ${exchange.status}; Session-Antwort ist ungültig."
                },
            )
            if (!exchangeHealthy) return finish(checks, state)

            val reused = request(
                method = "POST",
                path = "/v1/session",
                headers = mapOf("X-X88-Pairing-Code" to pairing.code),
                body = exchangeBody,
            )
            val reuseBlocked = reused.status == 401 && reused.json?.optString("error") == "PAIRING_DENIED"
            checks += Check(
                name = "Pairing-Replay-Sperre",
                ok = reuseBlocked,
                detail = if (reuseBlocked) {
                    "Der bereits verwendete Pairing-Code wird korrekt abgewiesen."
                } else {
                    "Wiederverwendung wurde nicht wie erwartet blockiert (HTTP ${reused.status})."
                },
            )

            val invalidBearer = request(
                method = "POST",
                path = "/v1/dialogue",
                headers = mapOf("Authorization" to "Bearer x88-selftest-invalid-token"),
                body = JSONObject().put("request", JSONObject().put("text", "")).toString(),
            )
            val invalidBearerBlocked = invalidBearer.status == 401 &&
                invalidBearer.json?.optString("error") == "DIALOGUE_SESSION_UNAUTHORIZED"
            checks += Check(
                name = "Bearer-Abweisung",
                ok = invalidBearerBlocked,
                detail = if (invalidBearerBlocked) {
                    "Ungültiges Dialog-Token wird vor jeder Dialogverarbeitung blockiert."
                } else {
                    "Ungültiges Bearer-Token wurde nicht wie erwartet abgewiesen (HTTP ${invalidBearer.status})."
                },
            )

            val validBearer = request(
                method = "POST",
                path = "/v1/dialogue",
                headers = mapOf("Authorization" to "Bearer $sessionToken"),
                body = JSONObject().put("request", JSONObject().put("text", "")).toString(),
            )
            val validBearerReachedInputGate = validBearer.status == 400 &&
                validBearer.json?.optString("error") == "INVALID_INPUT"
            checks += Check(
                name = "Bearer-Akzeptanz + Input-Gate",
                ok = validBearerReachedInputGate,
                detail = if (validBearerReachedInputGate) {
                    "Gültiges Session-Token erreicht die Eingabeprüfung; der leere Testtext wird dort gestoppt. Kein KI-Provider wurde aufgerufen."
                } else {
                    "Gültige Session erreichte das erwartete Input-Gate nicht (HTTP ${validBearer.status})."
                },
            )

            val invalidProposalBearer = request(
                method = "POST",
                path = "/v1/action/proposal",
                headers = mapOf("Authorization" to "Bearer x88-selftest-invalid-token"),
                body = JSONObject().put("action", "get_device_status").toString(),
            )
            val proposalAuthBlocked = invalidProposalBearer.status == 401 &&
                invalidProposalBearer.json?.optString("error") == "DIALOGUE_SESSION_UNAUTHORIZED"
            checks += Check(
                name = "ActionProposal Bearer-Gate",
                ok = proposalAuthBlocked,
                detail = if (proposalAuthBlocked) {
                    "Aktionsvorschläge akzeptieren keine ungültige X-88-Session."
                } else {
                    "Proposal-Endpunkt akzeptierte die Auth-Grenze nicht wie erwartet (HTTP ${invalidProposalBearer.status})."
                },
            )

            checks += proposalCheck(
                sessionToken = sessionToken,
                action = "get_device_status",
                expectedDecision = "SAFE",
                name = "ActionPolicy SAFE",
            )
            checks += proposalCheck(
                sessionToken = sessionToken,
                action = "camera_start",
                expectedDecision = "CONFIRM",
                name = "ActionPolicy CONFIRM",
            )
            checks += proposalCheck(
                sessionToken = sessionToken,
                action = "format_phone",
                expectedDecision = "BLOCKED",
                name = "ActionPolicy BLOCKED",
            )

            val pendingCamera = ActionApprovalStore.listPending().firstOrNull { it.action == "camera_start" }
            val pendingHealthy = pendingCamera != null && pendingCamera.expiresAtMs > System.currentTimeMillis()
            checks += Check(
                name = "Approval Pending-Queue",
                ok = pendingHealthy,
                detail = if (pendingHealthy) {
                    "CONFIRM-Vorschlag liegt flüchtig in der lokalen Queue; keine Geräteaktion wurde ausgeführt."
                } else {
                    "CONFIRM-Vorschlag wurde nicht in der lokalen Pending-Queue gefunden."
                },
            )

            if (pendingCamera != null) {
                val grant = ActionApprovalStore.approve(pendingCamera.id)
                val approvalHealthy = grant != null &&
                    grant.action == "camera_start" &&
                    grant.expiresAtMs > System.currentTimeMillis() &&
                    ActionApprovalStore.listPending().none { it.id == pendingCamera.id }
                checks += Check(
                    name = "Lokale Einmalfreigabe",
                    ok = approvalHealthy,
                    detail = if (approvalHealthy) {
                        "Lokale Bestätigung erzeugt nur einen kurzlebigen RAM-Grant; weiterhin keine Geräteausführung."
                    } else {
                        "Lokale Bestätigung erzeugte keinen gültigen flüchtigen Grant."
                    },
                )

                val wrongActionRejected = ActionApprovalStore.consumeForAction("microphone_start") == null
                val firstConsume = ActionApprovalStore.consumeForAction("camera_start")
                val secondConsume = ActionApprovalStore.consumeForAction("camera_start")
                val oneTimeHealthy = wrongActionRejected && firstConsume != null && secondConsume == null
                checks += Check(
                    name = "Approval Action-Bindung + Einmaligkeit",
                    ok = oneTimeHealthy,
                    detail = if (oneTimeHealthy) {
                        "Falsche Aktion kann den Grant nicht nutzen; richtige Aktion kann ihn genau einmal konsumieren. Keine Geräte-API wurde aufgerufen."
                    } else {
                        "Action-Bindung oder Einmalverbrauch des Grants ist inkonsistent."
                    },
                )
            }
        } catch (error: Exception) {
            checks += Check(
                name = "Pairing-/Policy-/Approval-Protokoll",
                ok = false,
                detail = "Lokaler Protokolltest scheiterte: ${error.javaClass.simpleName}",
            )
        } finally {
            DialogueSessionStore.revoke()
            ActionApprovalStore.revokeAll()
        }

        return finish(checks, state)
    }

    private fun proposalCheck(
        sessionToken: String,
        action: String,
        expectedDecision: String,
        name: String,
    ): Check {
        val response = request(
            method = "POST",
            path = "/v1/action/proposal",
            headers = mapOf("Authorization" to "Bearer $sessionToken"),
            body = JSONObject().put("action", action).toString(),
        )
        val json = response.json
        val ok = response.status == 200 &&
            json?.optBoolean("ok", false) == true &&
            json.optString("action") == action &&
            json.optString("decision") == expectedDecision &&
            !json.optBoolean("executable", true)
        return Check(
            name = name,
            ok = ok,
            detail = if (ok) {
                "$action → $expectedDecision, executable=false. Keine Geräteaktion wurde ausgeführt."
            } else {
                "$action lieferte nicht die erwartete reine Policy-Entscheidung (HTTP ${response.status})."
            },
        )
    }

    private fun finish(checks: List<Check>, state: JSONObject?): Result {
        val failed = checks.count { !it.ok && !it.skipped }
        val skipped = checks.count { it.skipped }
        val passed = checks.size - failed - skipped
        val ok = failed == 0
        val message = buildString {
            append(if (ok) "ARIANA Core v3: lokale Sicherheitskette OK." else "ARIANA Core v3: Sicherheitskette hat Fehler.")
            append("\n$passed geprüft")
            if (skipped > 0) append(" · $skipped übersprungen")
            if (failed > 0) append(" · $failed fehlgeschlagen")
        }
        return Result(ok = ok, message = message, state = state, checks = checks)
    }

    private fun request(
        method: String,
        path: String,
        headers: Map<String, String> = emptyMap(),
        body: String = "",
    ): HttpResponse {
        val bodyBytes = body.toByteArray(Charsets.UTF_8)
        Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", LocalBridgeServer.PORT), CONNECT_TIMEOUT_MS)
            socket.soTimeout = READ_TIMEOUT_MS

            val requestHead = buildString {
                append("$method $path HTTP/1.1\r\n")
                append("Host: 127.0.0.1:${LocalBridgeServer.PORT}\r\n")
                append("Accept: application/json\r\n")
                headers.forEach { (headerName, value) -> append("$headerName: $value\r\n") }
                if (bodyBytes.isNotEmpty()) {
                    append("Content-Type: application/json; charset=utf-8\r\n")
                    append("Content-Length: ${bodyBytes.size}\r\n")
                }
                append("Connection: close\r\n\r\n")
            }

            socket.getOutputStream().apply {
                write(requestHead.toByteArray(Charsets.US_ASCII))
                if (bodyBytes.isNotEmpty()) write(bodyBytes)
                flush()
            }

            val raw = socket.getInputStream().readBytes().toString(Charsets.UTF_8)
            val headerEnd = raw.indexOf("\r\n\r\n")
            require(headerEnd > 0) { "incomplete HTTP response" }
            val statusLine = raw.substringBefore("\r\n")
            val status = statusLine.split(' ').getOrNull(1)?.toIntOrNull()
                ?: throw IllegalStateException("invalid HTTP status")
            val responseBody = raw.substring(headerEnd + 4)
            val json = if (responseBody.isBlank()) null else runCatching { JSONObject(responseBody) }.getOrNull()
            return HttpResponse(status = status, json = json)
        }
    }

    private const val CONNECT_TIMEOUT_MS = 1_500
    private const val READ_TIMEOUT_MS = 2_500
}

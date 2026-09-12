package de.snowworks.ariana.bridge

import android.content.Context
import android.util.Base64
import de.snowworks.ariana.ArianaGate
import de.snowworks.ariana.Feature
import de.snowworks.ariana.camera.CameraFrameStore
import de.snowworks.ariana.notify.NotificationStore
import de.snowworks.ariana.presence.PresenceSignalController
import de.snowworks.ariana.session.ArianaCaptureService
import de.snowworks.ariana.session.SessionRegistry
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Minimal local-only HTTP bridge. It binds explicitly to loopback and never to
 * Wi-Fi/LAN interfaces. Sensitive start actions are intentionally not exposed
 * until a visible user-confirmation flow is attached.
 */
object LocalBridgeServer {
    const val PORT = 8765
    private const val MAX_BODY_BYTES = 8 * 1024
    private const val MAX_HTTP_LINE_BYTES = 8 * 1024
    private val running = AtomicBoolean(false)
    @Volatile private var serverSocket: ServerSocket? = null
    @Volatile private var worker: Thread? = null
    private val rateLock = Any()
    private var windowStartedAt = 0L
    private var requestsInWindow = 0

    private data class HttpResult(
        val status: Int,
        val body: JSONObject,
    )

    fun isRunning(): Boolean = running.get()

    @Synchronized
    fun start(context: Context) {
        if (running.get()) return
        val app = context.applicationContext
        val gate = ArianaGate(app)
        if (!gate.isMasterEnabled || gate.isBlocked) return
        val token = BridgeTokenStore(app).getOrCreate()
        running.set(true)
        worker = Thread({
            try {
                ServerSocket(PORT, 8, InetAddress.getByName("127.0.0.1")).use { server ->
                    server.soTimeout = 1000
                    serverSocket = server
                    while (running.get()) {
                        try {
                            server.accept().use { socket -> handle(app, socket, token) }
                        } catch (_: SocketTimeoutException) {
                            // Periodically observe the stop flag.
                        }
                    }
                }
            } catch (_: Exception) {
                running.set(false)
            } finally {
                serverSocket = null
                running.set(false)
            }
        }, "ArianaLoopbackBridge").also {
            it.isDaemon = true
            it.start()
        }
    }

    @Synchronized
    fun stop() {
        running.set(false)
        DialogueSessionStore.revoke()
        runCatching { serverSocket?.close() }
        serverSocket = null
        worker?.interrupt()
        worker = null
    }

    private fun handle(context: Context, socket: Socket, token: String) {
        socket.soTimeout = 2000
        if (!allowRequest()) {
            return respond(socket, 429, error("RATE_LIMITED", "Zu viele lokale Bridge-Anfragen."))
        }

        val input = socket.getInputStream()
        val requestLine = runCatching { readHttpLine(input) }.getOrNull()
            ?: return respond(socket, 400, error("INVALID_REQUEST", "Ungültige Anfrage."))
        val parts = requestLine.split(' ')
        if (parts.size < 2) return respond(socket, 400, error("INVALID_REQUEST", "Ungültige Anfrage."))

        val method = parts[0].uppercase()
        val path = parts[1]
        val headers = mutableMapOf<String, String>()
        while (true) {
            val line = runCatching { readHttpLine(input) }.getOrNull()
                ?: return respond(socket, 400, error("INVALID_HEADERS", "HTTP-Header konnten nicht gelesen werden."))
            if (line.isEmpty()) break
            val idx = line.indexOf(':')
            if (idx <= 0) return respond(socket, 400, error("INVALID_HEADERS", "Ungültiger HTTP-Header."))
            headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
        }

        val length = headers["content-length"]?.toIntOrNull() ?: 0
        if (length < 0 || length > MAX_BODY_BYTES) {
            return respond(socket, 413, error("REQUEST_TOO_LARGE", "Request ist zu groß."))
        }
        val bodyBytes = if (length > 0) readExactBytes(input, length) else ByteArray(0)
        if (bodyBytes == null) {
            return respond(socket, 400, error("INCOMPLETE_BODY", "Request-Body ist unvollständig."))
        }
        val body = bodyBytes.toString(Charsets.UTF_8)

        val result = when {
            path == "/v1/session" && method == "POST" -> exchangeDialogueSession(body, headers)
            path == "/v1/dialogue" && method == "POST" -> dialogue(context, body, headers)
            path.startsWith("/v1/") -> HttpResult(404, error("NOT_FOUND", "Unbekannter X-88-Endpunkt."))
            headers["x-ariana-token"] != token -> HttpResult(
                401,
                error("UNAUTHORIZED", "Lokales Bridge-Token fehlt oder ist ungültig."),
            )
            path == "/state" && method == "GET" -> HttpResult(200, state(context))
            path == "/action" && method == "POST" -> {
                val response = action(context, body)
                HttpResult(if (response.optBoolean("ok", false)) 200 else 400, response)
            }
            path == "/state" || path == "/action" -> HttpResult(
                405,
                error("METHOD_NOT_ALLOWED", "HTTP-Methode ist für diesen Endpunkt nicht erlaubt."),
            )
            else -> HttpResult(404, error("NOT_FOUND", "Unbekannter Bridge-Endpunkt."))
        }
        respond(socket, result.status, result.body)
    }

    private fun readHttpLine(input: InputStream): String? {
        val bytes = ArrayList<Byte>(128)
        while (bytes.size <= MAX_HTTP_LINE_BYTES) {
            val value = input.read()
            if (value == -1) return if (bytes.isEmpty()) null else String(bytes.toByteArray(), Charsets.US_ASCII)
            if (value == '\n'.code) return String(bytes.toByteArray(), Charsets.US_ASCII)
            if (value != '\r'.code) bytes.add(value.toByte())
        }
        throw IllegalArgumentException("HTTP line too long")
    }

    private fun readExactBytes(input: InputStream, length: Int): ByteArray? {
        val data = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val count = input.read(data, offset, length - offset)
            if (count <= 0) return null
            offset += count
        }
        return data
    }

    private fun exchangeDialogueSession(body: String, headers: Map<String, String>): HttpResult {
        val requestId = UUID.randomUUID().toString()
        val bodyCode = runCatching { JSONObject(body).optString("pairingCode") }.getOrDefault("")
        val pairingCode = headers["x-x88-pairing-code"].orEmpty().ifBlank { bodyCode }
        val grant = DialogueSessionStore.exchange(pairingCode)
            ?: return HttpResult(
                401,
                error("PAIRING_DENIED", "Pairing-Code ist ungültig oder abgelaufen.", requestId),
            )
        return HttpResult(
            200,
            JSONObject()
                .put("ok", true)
                .put("requestId", requestId)
                .put("model", "x88-dialogue-session-v1")
                .put("token", grant.token)
                .put("expiresAtMs", grant.expiresAtMs)
                .put("expiresInMs", DialogueSessionStore.SESSION_TTL_MS),
        )
    }

    private fun dialogue(context: Context, body: String, headers: Map<String, String>): HttpResult {
        val requestId = UUID.randomUUID().toString()
        if (!DialogueSessionStore.validateBearer(headers["authorization"])) {
            return HttpResult(
                401,
                error("DIALOGUE_SESSION_UNAUTHORIZED", "X-88 Session-Token fehlt oder ist abgelaufen.", requestId),
            )
        }

        val gate = ArianaGate(context)
        if (!gate.isMasterEnabled || gate.isBlocked) {
            return HttpResult(
                403,
                error("MASTER_DISABLED", "Master-Zugriff ist deaktiviert.", requestId),
            )
        }

        val root = runCatching { JSONObject(body) }.getOrElse {
            return HttpResult(400, error("INVALID_JSON", "JSON konnte nicht gelesen werden.", requestId))
        }
        val request = root.optJSONObject("request") ?: root
        val text = request.optString("text")
        if (text.isBlank()) {
            return HttpResult(400, error("INVALID_INPUT", "Dialogtext fehlt.", requestId))
        }

        PresenceSignalController.emit(context, PresenceSignalController.State.THINKING)
        val outcome = DialogueRouter.generate(text)
        if (!outcome.ok) {
            PresenceSignalController.emit(context, PresenceSignalController.State.ATTENTION)
            val status = when (outcome.error) {
                "PROVIDER_UNAVAILABLE" -> 503
                "PROVIDER_TIMEOUT" -> 504
                "PROVIDER_FAILED" -> 502
                else -> 400
            }
            return HttpResult(
                status,
                error(outcome.error ?: "DIALOGUE_FAILED", "Dialogmodul konnte keine Antwort liefern.", requestId)
                    .put("providerId", outcome.providerId),
            )
        }

        PresenceSignalController.emit(context, PresenceSignalController.State.DONE)
        return HttpResult(
            200,
            JSONObject()
                .put("ok", true)
                .put("requestId", requestId)
                .put("model", "x88-loopback-dialogue-response-v1")
                .put("providerId", outcome.providerId)
                .put("reply", outcome.reply),
        )
    }

    private fun state(context: Context): JSONObject {
        val gate = ArianaGate(context)
        return JSONObject()
            .put("ok", true)
            .put("requestId", UUID.randomUUID().toString())
            .put("masterEnabled", gate.isMasterEnabled)
            .put("blocked", gate.isBlocked)
            .put("bridge", "127.0.0.1:$PORT")
            .put("presenceState", PresenceSignalController.currentState())
            .put("activeSessions", JSONArray(SessionRegistry.snapshot().map { it.id }))
            .put("dialogueSessionActive", DialogueSessionStore.hasActiveSession())
            .put("dialogueProviderId", DialogueRouter.providerId())
    }

    private fun action(context: Context, body: String): JSONObject {
        val requestId = UUID.randomUUID().toString()
        val json = runCatching { JSONObject(body) }.getOrElse {
            return error("INVALID_JSON", "JSON konnte nicht gelesen werden.", requestId)
        }
        val action = json.optString("action")
        val gate = ArianaGate(context)
        if (action !in setOf("stop_all", "camera_stop", "microphone_stop", "screen_stop", "presence_clear") &&
            (!gate.isMasterEnabled || gate.isBlocked)
        ) {
            return error("MASTER_DISABLED", "Master-Zugriff ist deaktiviert.", requestId)
        }

        return when (action) {
            "get_device_status", "get_permission_status", "get_active_sessions" -> state(context).put("requestId", requestId)
            "notification_list" -> JSONObject()
                .put("ok", true)
                .put("requestId", requestId)
                .put("notifications", JSONArray(NotificationStore.listRecent().map { entry ->
                    JSONObject()
                        .put("packageName", entry.packageName)
                        .put("appLabel", entry.appLabel)
                        .put("title", entry.title)
                        .put("text", entry.text)
                        .put("postTime", entry.postTime)
                        .put("key", entry.key)
                }))
            "notification_clear" -> {
                NotificationStore.clear()
                ok(requestId)
            }
            "presence_thinking" -> presence(context, PresenceSignalController.State.THINKING, requestId)
            "presence_done" -> presence(context, PresenceSignalController.State.DONE, requestId)
            "presence_attention" -> presence(context, PresenceSignalController.State.ATTENTION, requestId)
            "presence_quiet" -> presence(context, PresenceSignalController.State.QUIET, requestId)
            "presence_test" -> presence(context, PresenceSignalController.State.TEST, requestId)
            "presence_clear" -> {
                PresenceSignalController.clear(context)
                ok(requestId).put("presenceState", "clear")
            }
            "camera_snapshot" -> {
                val frame = CameraFrameStore.latest()
                    ?: return error("FEATURE_NOT_ACTIVE", "Noch kein Kamerabild verfügbar. Starte die Kamera sichtbar in der App.", requestId)
                JSONObject()
                    .put("ok", true)
                    .put("requestId", requestId)
                    .put("mimeType", "image/jpeg")
                    .put("width", frame.width)
                    .put("height", frame.height)
                    .put("capturedAt", frame.capturedAt)
                    .put("jpegBase64", Base64.encodeToString(frame.jpeg, Base64.NO_WRAP))
            }
            "camera_stop" -> {
                ArianaCaptureService.stopFeature(context, Feature.CAMERA)
                ok(requestId)
            }
            "microphone_stop" -> {
                ArianaCaptureService.stopFeature(context, Feature.MICROPHONE)
                ok(requestId)
            }
            "screen_stop" -> {
                ArianaCaptureService.stopFeature(context, Feature.SCREEN)
                ok(requestId)
            }
            "stop_all" -> {
                ArianaCaptureService.stopAll(context)
                SessionRegistry.clear()
                DialogueSessionStore.revoke()
                ok(requestId)
            }
            "camera_start", "microphone_start", "screen_start" ->
                error("USER_INTERACTION_REQUIRED", "Diese Funktion muss sichtbar in der App bestätigt werden.", requestId)
            else -> error("INVALID_REQUEST", "Unbekannte Aktion.", requestId)
        }
    }

    private fun presence(
        context: Context,
        state: PresenceSignalController.State,
        requestId: String,
    ): JSONObject {
        val posted = PresenceSignalController.emit(context, state)
        return ok(requestId)
            .put("presenceState", state.wireName)
            .put("notificationPosted", posted)
    }

    private fun allowRequest(): Boolean = synchronized(rateLock) {
        val now = System.currentTimeMillis()
        if (now - windowStartedAt >= 60_000L) {
            windowStartedAt = now
            requestsInWindow = 0
        }
        if (requestsInWindow >= 60) return@synchronized false
        requestsInWindow += 1
        true
    }

    private fun ok(requestId: String) = JSONObject().put("ok", true).put("requestId", requestId)

    private fun error(code: String, message: String, requestId: String = UUID.randomUUID().toString()) =
        JSONObject().put("ok", false).put("requestId", requestId).put("error", code).put("message", message)

    private fun respond(socket: Socket, status: Int, json: JSONObject) {
        val body = json.toString().toByteArray(Charsets.UTF_8)
        val reason = when (status) {
            200 -> "OK"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            403 -> "Forbidden"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            413 -> "Payload Too Large"
            429 -> "Too Many Requests"
            502 -> "Bad Gateway"
            503 -> "Service Unavailable"
            504 -> "Gateway Timeout"
            else -> "Error"
        }
        val out = socket.getOutputStream()
        out.write("HTTP/1.1 $status $reason\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: ${body.size}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n".toByteArray())
        out.write(body)
        out.flush()
    }
}

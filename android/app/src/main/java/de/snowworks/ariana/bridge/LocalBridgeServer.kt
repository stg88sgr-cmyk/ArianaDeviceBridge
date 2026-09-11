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
import java.io.BufferedReader
import java.io.InputStreamReader
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
    private val running = AtomicBoolean(false)
    @Volatile private var serverSocket: ServerSocket? = null
    @Volatile private var worker: Thread? = null
    private val rateLock = Any()
    private var windowStartedAt = 0L
    private var requestsInWindow = 0

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
        val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
        val requestLine = reader.readLine() ?: return
        val parts = requestLine.split(' ')
        if (parts.size < 2) return respond(socket, 400, error("INVALID_REQUEST", "Ungültige Anfrage."))

        val headers = mutableMapOf<String, String>()
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
            val idx = line.indexOf(':')
            if (idx > 0) headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
        }

        if (headers["x-ariana-token"] != token) {
            return respond(socket, 401, error("UNAUTHORIZED", "Lokales Bridge-Token fehlt oder ist ungültig."))
        }
        val length = headers["content-length"]?.toIntOrNull() ?: 0
        if (length < 0 || length > MAX_BODY_BYTES) {
            return respond(socket, 413, error("REQUEST_TOO_LARGE", "Request ist zu groß."))
        }
        val body = if (length > 0) {
            val chars = CharArray(length)
            var offset = 0
            while (offset < length) {
                val read = reader.read(chars, offset, length - offset)
                if (read <= 0) break
                offset += read
            }
            String(chars, 0, offset)
        } else ""

        val path = parts[1]
        val response = when (path) {
            "/state" -> state(context)
            "/action" -> action(context, body)
            else -> error("NOT_FOUND", "Unbekannter Bridge-Endpunkt.")
        }
        respond(socket, if (response.optBoolean("ok", false)) 200 else 400, response)
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
            401 -> "Unauthorized"
            413 -> "Payload Too Large"
            429 -> "Too Many Requests"
            else -> "Bad Request"
        }
        val out = socket.getOutputStream()
        out.write("HTTP/1.1 $status $reason\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
        out.write(body)
        out.flush()
    }
}

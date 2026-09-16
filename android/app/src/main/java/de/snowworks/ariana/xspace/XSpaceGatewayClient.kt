package de.snowworks.ariana.xspace

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * Local-only client for the X-88 X Space gateway.
 *
 * The gateway is intentionally fixed to 127.0.0.1:8877. This class never
 * accepts a remote host, so enabling X Space support does not create a LAN
 * control surface on the Android device.
 */
object XSpaceGatewayClient {
    private const val GATEWAY_URL = "ws://127.0.0.1:8877/xspace/ws"
    private val spaceUrlPattern = Regex("^https://(?:www\\.)?(?:x\\.com|twitter\\.com)/i/spaces/[A-Za-z0-9_-]{4,128}(?:[/?#].*)?$")

    data class Snapshot(
        val connected: Boolean = false,
        val connecting: Boolean = false,
        val joined: Boolean = false,
        val muted: Boolean = true,
        val spaceUrl: String? = null,
        val lastStatus: String? = null,
        val lastError: String? = null,
        val lastTranscript: String? = null,
        val lastSpeaker: String? = null,
    )

    fun interface EventListener {
        fun onEvent(event: JSONObject)
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val listeners = CopyOnWriteArrayList<EventListener>()
    private val lock = Any()

    @Volatile private var socket: WebSocket? = null
    @Volatile private var snapshot = Snapshot()

    fun snapshot(): Snapshot = snapshot

    internal fun isValidSpaceUrl(value: String): Boolean = spaceUrlPattern.matches(value.trim())

    internal fun normalizeSpeakText(text: String): String = text.trim().take(2000)

    fun addListener(listener: EventListener) {
        listeners += listener
    }

    fun removeListener(listener: EventListener) {
        listeners -= listener
    }

    @Synchronized
    fun connect(token: String? = null): Boolean {
        if (socket != null || snapshot.connecting || snapshot.connected) return true

        val request = Request.Builder()
            .url(GATEWAY_URL)
            .apply {
                token?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    header("Authorization", "Bearer $it")
                }
            }
            .build()

        snapshot = snapshot.copy(connecting = true, lastError = null)
        socket = http.newWebSocket(request, listener)
        return true
    }

    @Synchronized
    fun disconnect() {
        socket?.close(1000, "android client disconnect")
        socket = null
        snapshot = Snapshot()
    }

    fun join(spaceUrl: String): Boolean {
        val normalized = spaceUrl.trim()
        if (!isValidSpaceUrl(normalized)) {
            snapshot = snapshot.copy(lastError = "INVALID_X_SPACE_URL")
            return false
        }
        val sent = send(JSONObject().put("type", "join").put("url", normalized))
        if (sent) snapshot = snapshot.copy(spaceUrl = normalized, lastError = null)
        return sent
    }

    fun leave(): Boolean = send(JSONObject().put("type", "leave"))

    fun speak(text: String): Boolean {
        val safe = normalizeSpeakText(text)
        if (safe.isEmpty()) return false
        return send(JSONObject().put("type", "speak").put("text", safe))
    }

    fun mute(): Boolean = send(JSONObject().put("type", "mute"))

    fun unmute(): Boolean = send(JSONObject().put("type", "unmute"))

    fun requestStatus(): Boolean = send(JSONObject().put("type", "status"))

    private fun send(command: JSONObject): Boolean = socket?.send(command.toString()) == true

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            snapshot = snapshot.copy(connected = true, connecting = false, lastError = null)
            requestStatus()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val event = runCatching { JSONObject(text) }.getOrElse {
                snapshot = snapshot.copy(lastError = "INVALID_GATEWAY_EVENT")
                return
            }
            applyEvent(event)
            listeners.forEach { listener -> runCatching { listener.onEvent(event) } }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, reason)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            synchronized(lock) {
                if (socket === webSocket) socket = null
                snapshot = Snapshot(lastStatus = "closed:$code")
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            synchronized(lock) {
                if (socket === webSocket) socket = null
                snapshot = snapshot.copy(
                    connected = false,
                    connecting = false,
                    joined = false,
                    lastError = t.message ?: "XSPACE_GATEWAY_FAILED",
                )
            }
        }
    }

    private fun applyEvent(event: JSONObject) {
        when (event.optString("type")) {
            "connected" -> snapshot = snapshot.copy(connected = true, joined = true, lastError = null)
            "disconnected" -> snapshot = snapshot.copy(joined = false)
            "agent_status" -> snapshot = snapshot.copy(lastStatus = event.optString("status"))
            "audio_state" -> snapshot = snapshot.copy(muted = event.optBoolean("muted", snapshot.muted))
            "transcript" -> snapshot = snapshot.copy(
                lastTranscript = event.optString("text").take(2000),
                lastSpeaker = event.optString("speaker").takeIf { it.isNotBlank() },
            )
            "error" -> snapshot = snapshot.copy(lastError = event.optString("message", "XSPACE_GATEWAY_ERROR"))
            "state", "status" -> snapshot = snapshot.copy(
                connected = true,
                joined = event.optBoolean("connected", snapshot.joined),
                muted = event.optBoolean("muted", snapshot.muted),
                spaceUrl = event.optString("activeSpaceUrl").takeIf { it.isNotBlank() } ?: snapshot.spaceUrl,
                lastStatus = event.optString("agentStatus").takeIf { it.isNotBlank() } ?: snapshot.lastStatus,
            )
        }
    }
}

package de.snowworks.ariana.bridge

import android.content.Context
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket

object BridgeSelfTest {
    data class Result(
        val ok: Boolean,
        val message: String,
        val state: JSONObject? = null,
    )

    fun run(context: Context): Result {
        if (!LocalBridgeServer.isRunning()) {
            return Result(false, "Lokale Bridge läuft nicht.")
        }

        val token = BridgeTokenStore(context.applicationContext).getOrCreate()
        return runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", LocalBridgeServer.PORT), CONNECT_TIMEOUT_MS)
                socket.soTimeout = READ_TIMEOUT_MS

                val request = buildString {
                    append("GET /state HTTP/1.1\r\n")
                    append("Host: 127.0.0.1:${LocalBridgeServer.PORT}\r\n")
                    append("X-Ariana-Token: $token\r\n")
                    append("Connection: close\r\n\r\n")
                }
                socket.getOutputStream().apply {
                    write(request.toByteArray(Charsets.US_ASCII))
                    flush()
                }

                val raw = socket.getInputStream().readBytes().toString(Charsets.UTF_8)
                val headerEnd = raw.indexOf("\r\n\r\n")
                if (headerEnd <= 0) return@use Result(false, "Bridge-Antwort ist unvollständig.")

                val statusLine = raw.substringBefore("\r\n")
                val body = raw.substring(headerEnd + 4)
                if (!statusLine.startsWith("HTTP/1.1 200")) {
                    return@use Result(false, "Bridge antwortet mit $statusLine")
                }

                val state = runCatching { JSONObject(body) }.getOrNull()
                    ?: return@use Result(false, "Bridge-Antwort enthält kein gültiges JSON.")
                val expectedBridge = "127.0.0.1:${LocalBridgeServer.PORT}"
                val healthy = state.optBoolean("ok", false) && state.optString("bridge") == expectedBridge
                if (!healthy) {
                    return@use Result(false, "Bridge-State ist nicht konsistent.", state)
                }

                Result(
                    true,
                    "ARIANA Core v1 antwortet lokal auf $expectedBridge/state.",
                    state,
                )
            }
        }.getOrElse { error ->
            Result(false, "Bridge-Selbsttest fehlgeschlagen: ${error.javaClass.simpleName}")
        }
    }

    private const val CONNECT_TIMEOUT_MS = 1_500
    private const val READ_TIMEOUT_MS = 2_000
}

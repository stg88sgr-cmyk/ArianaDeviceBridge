package de.snowworks.ariana.bridge

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Tiny in-app client for SAFE loopback bridge actions.
 *
 * It is hard-wired to 127.0.0.1 and authenticates with the same Android-Keystore
 * protected token as LocalBridgeServer. No LAN or internet target is accepted.
 */
object LocalBridgeActionClient {
    data class Result(
        val ok: Boolean,
        val payload: JSONObject? = null,
        val error: String? = null,
    )

    fun execute(context: Context, action: String): Result {
        if (!LocalBridgeServer.isRunning()) {
            return Result(false, error = "BRIDGE_NOT_RUNNING")
        }

        val connection = (URL("http://127.0.0.1:${LocalBridgeServer.PORT}/action")
            .openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("X-Ariana-Token", BridgeTokenStore(context).getOrCreate())
        }

        return try {
            val body = JSONObject().put("action", action).toString().toByteArray(Charsets.UTF_8)
            connection.outputStream.use { it.write(body) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val json = runCatching { JSONObject(raw) }.getOrNull()
            if (status in 200..299 && json?.optBoolean("ok", false) == true) {
                Result(true, payload = json)
            } else {
                Result(
                    false,
                    payload = json,
                    error = json?.optString("error")?.takeIf { it.isNotBlank() } ?: "HTTP_$status",
                )
            }
        } catch (error: Throwable) {
            Result(false, error = error.javaClass.simpleName)
        } finally {
            connection.disconnect()
        }
    }

    private const val CONNECT_TIMEOUT_MS = 1_500
    private const val READ_TIMEOUT_MS = 2_500
}

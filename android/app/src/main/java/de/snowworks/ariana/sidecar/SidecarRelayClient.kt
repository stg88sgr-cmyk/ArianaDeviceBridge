package de.snowworks.ariana.sidecar

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class SidecarRelayClient {
    companion object {
        const val RELAY_URL = "http://127.0.0.1:8766/sidecar"
        const val HEALTH_URL = "http://127.0.0.1:8766/health"
        private const val CONNECT_TIMEOUT_MS = 3_000
        private const val READ_TIMEOUT_MS = 90_000
        private const val MAX_HISTORY_MESSAGES = 24
        private const val MAX_MESSAGE_CHARS = 4_000
    }

    data class Result(
        val ok: Boolean,
        val reply: String? = null,
        val model: String? = null,
        val error: String? = null,
    )

    fun send(
        channel: SidecarChannel,
        history: List<SidecarMessage>,
        message: String,
        bearerToken: String,
    ): Result {
        val cleanMessage = message.trim().take(MAX_MESSAGE_CHARS)
        if (cleanMessage.isBlank()) return Result(false, error = "EMPTY_MESSAGE")
        val token = bearerToken.trim()
        if (token.length < 16) return Result(false, error = "RELAY_TOKEN_REQUIRED")

        val payload = JSONObject()
            .put("channel", channel.wireId)
            .put("message", cleanMessage)
            .put("history", JSONArray().apply {
                history.takeLast(MAX_HISTORY_MESSAGES).forEach { item ->
                    put(
                        JSONObject()
                            .put("role", item.role.wireName)
                            .put("text", item.text.take(MAX_MESSAGE_CHARS)),
                    )
                }
            })

        return requestJson(RELAY_URL, "POST", token, payload.toString()) { json ->
            val reply = json.optString("reply").trim()
            if (reply.isBlank()) {
                Result(false, model = json.optString("model").ifBlank { null }, error = json.optString("error").ifBlank { "EMPTY_REPLY" })
            } else {
                Result(true, reply = reply, model = json.optString("model").ifBlank { null })
            }
        }
    }

    fun health(): Result = requestJson(HEALTH_URL, "GET", null, null) { json ->
        if (json.optBoolean("ok", false)) {
            Result(true, model = json.optString("model").ifBlank { null }, reply = "RELAY_OK")
        } else {
            Result(false, error = json.optString("error").ifBlank { "RELAY_OFFLINE" })
        }
    }

    private fun requestJson(
        target: String,
        method: String,
        bearerToken: String?,
        body: String?,
        parse: (JSONObject) -> Result,
    ): Result {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(target).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
                if (!bearerToken.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $bearerToken")
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
            }
            if (body != null) {
                connection.outputStream.use { output -> output.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val source = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = source?.use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
            }.orEmpty()
            val json = runCatching { JSONObject(text) }.getOrElse { JSONObject().put("error", "INVALID_RELAY_RESPONSE") }
            if (code !in 200..299) {
                Result(false, error = json.optString("error").ifBlank { "RELAY_HTTP_$code" })
            } else {
                parse(json)
            }
        } catch (_: Exception) {
            Result(false, error = "RELAY_UNREACHABLE")
        } finally {
            connection?.disconnect()
        }
    }
}

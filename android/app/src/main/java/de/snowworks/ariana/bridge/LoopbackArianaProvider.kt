package de.snowworks.ariana.bridge

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Hard-coded same-device Ariana Core client. */
class LoopbackArianaProvider {
    fun isHealthy(): Boolean = runCatching {
        val c = open("/health", "GET", 700, 700)
        try { c.responseCode == 200 } finally { c.disconnect() }
    }.getOrDefault(false)

    fun generate(text: String): String {
        val payload = JSONObject()
            .put("model", "ARIANA-X88-local")
            .put("stream", false)
            .put("max_tokens", 96)
            .put("messages", JSONArray().put(
                JSONObject().put("role", "user").put("content", text)
            ))
            .toString()
            .toByteArray(Charsets.UTF_8)

        val c = open("/v1/chat/completions", "POST", 1500, 120000)
        return try {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            c.setFixedLengthStreamingMode(payload.size)
            c.outputStream.use { it.write(payload) }
            val status = c.responseCode
            if (status !in 200..299) {
                throw DialogueRouter.ProviderException("ARIANA_CORE_HTTP_$status")
            }
            val body = c.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(body)
            val reply = root
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()
                .take(DialogueRouter.MAX_REPLY_CHARS)
            if (reply.isBlank()) {
                throw DialogueRouter.ProviderException("ARIANA_CORE_EMPTY_REPLY")
            }
            reply
        } catch (error: DialogueRouter.ProviderException) {
            throw error
        } catch (error: Exception) {
            throw DialogueRouter.ProviderException("ARIANA_CORE_UNAVAILABLE", error)
        } finally {
            c.disconnect()
        }
    }

    private fun open(path: String, method: String, connectMs: Int, readMs: Int): HttpURLConnection =
        (URL("http://127.0.0.1:8767$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = connectMs
            readTimeout = readMs
            useCaches = false
            instanceFollowRedirects = false
        }
}

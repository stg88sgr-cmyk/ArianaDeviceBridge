package de.snowworks.ariana.bridge

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

/** Hard-coded same-device Ariana Core client. */
class LoopbackArianaProvider {
    data class Health(
        val online: Boolean,
        val generation: Int? = null,
        val branch: String? = null,
    )

    data class HistoryMessage(
        val role: String,
        val content: String,
        val timestampSeconds: Double? = null,
    )

    fun health(): Health {
        val probed = runCatching {
            val c = open("/health", "GET", HEALTH_CONNECT_TIMEOUT_MS, HEALTH_READ_TIMEOUT_MS)
            try {
                if (c.responseCode != 200) return@runCatching Health(false)
                val body = c.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(body)
                Health(
                    online = root.optString("status") == "ok",
                    generation = root.optInt("generation").takeIf { root.has("generation") },
                    branch = root.optString("branch").takeIf { it.isNotBlank() },
                )
            } finally {
                c.disconnect()
            }
        }.getOrElse { Health(false) }

        if (probed.online) {
            lastHealthy = probed
            return probed
        }

        // llama.cpp/Termux can serialize requests while inference is running. A short
        // /health probe may time out although the core is still actively generating.
        // Preserve the last confirmed healthy state during our own in-flight request
        // so the UI does not falsely flap to CORE OFFLINE mid-answer.
        if (activeGenerations.get() > 0) {
            val cached = lastHealthy
            return if (cached.online) cached else Health(true, branch = "busy")
        }

        return Health(false)
    }

    fun isHealthy(): Boolean = health().online

    fun recentHistory(limit: Int = 6): List<HistoryMessage> = runCatching {
        val safeLimit = limit.coerceIn(1, 40)
        val c = open("/v1/history?limit=$safeLimit", "GET", 700, 1500)
        try {
            if (c.responseCode != 200) return@runCatching emptyList()
            val body = c.inputStream.bufferedReader().use { it.readText() }
            val messages = JSONObject(body).optJSONArray("messages") ?: return@runCatching emptyList()
            buildList {
                for (i in 0 until messages.length()) {
                    val item = messages.optJSONObject(i) ?: continue
                    val role = item.optString("role").trim()
                    val content = item.optString("content").trim()
                    val timestamp = item.optDouble("ts", Double.NaN).takeIf { !it.isNaN() }
                    if (role.isNotBlank() && content.isNotBlank()) {
                        add(HistoryMessage(role, content, timestamp))
                    }
                }
            }
        } finally {
            c.disconnect()
        }
    }.getOrElse { emptyList() }

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

        activeGenerations.incrementAndGet()
        return try {
            val c = open("/v1/chat/completions", "POST", 1500, 120000)
            try {
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
        } finally {
            activeGenerations.decrementAndGet()
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

    private companion object {
        const val HEALTH_CONNECT_TIMEOUT_MS = 700
        const val HEALTH_READ_TIMEOUT_MS = 700
        val activeGenerations = AtomicInteger(0)
        @Volatile var lastHealthy = Health(false)
    }
}

package de.snowworks.ariana.bridge

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.URI
import javax.net.ssl.HttpsURLConnection

/**
 * Small OpenAI-chat-completions-compatible HTTPS adapter.
 * Endpoint/model/key are user-configured; no credential is embedded in the app.
 */
class HttpsDialogueProvider(
    private val config: SecureAiProviderStore.Config,
) {
    fun generate(text: String): String {
        val uri = URI(config.endpoint.trim())
        validateDestination(uri)

        val connection = (uri.toURL().openConnection() as HttpsURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = false
            doInput = true
            doOutput = true
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("User-Agent", "ArianaDeviceBridge/X88")
            if (config.apiKey.isNotBlank()) {
                setRequestProperty("Authorization", "Bearer ${config.apiKey.trim()}")
            }
        }

        val payload = JSONObject()
            .put("model", config.model.trim())
            .put("stream", false)
            .put("temperature", 0.6)
            .put("max_tokens", 640)
            .put(
                "messages",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("role", "system")
                            .put(
                                "content",
                                "You are the dialogue provider for Ariana X-88. Reply naturally and concisely. " +
                                    "Use German unless the user clearly requests another language. " +
                                    "Do not claim device actions happened unless the bridge explicitly reports them.",
                            ),
                    )
                    .put(JSONObject().put("role", "user").put("content", text)),
            )
            .toString()
            .toByteArray(Charsets.UTF_8)

        require(payload.size <= MAX_REQUEST_BYTES) { "request too large" }

        return try {
            connection.setFixedLengthStreamingMode(payload.size)
            connection.outputStream.use { it.write(payload) }
            val status = connection.responseCode
            if (status !in 200..299) {
                throw IllegalStateException("provider http $status")
            }
            val body = readBounded(connection.inputStream, MAX_RESPONSE_BYTES)
            parseReply(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun validateDestination(uri: URI) {
        require(uri.scheme.equals("https", ignoreCase = true)) { "https required" }
        val host = uri.host ?: throw IllegalArgumentException("host required")
        require(uri.userInfo == null) { "userinfo forbidden" }
        require(uri.fragment == null) { "fragment forbidden" }
        require(host != "localhost") { "localhost forbidden" }
        require(!host.endsWith(".local", ignoreCase = true)) { "local host forbidden" }
        require(!IP_LITERAL.matches(host)) { "ip literals forbidden" }

        val addresses = InetAddress.getAllByName(host)
        require(addresses.isNotEmpty()) { "host unresolved" }
        require(
            addresses.none {
                it.isAnyLocalAddress || it.isLoopbackAddress || it.isLinkLocalAddress ||
                    it.isSiteLocalAddress || it.isMulticastAddress
            },
        ) { "private network target forbidden" }
    }

    private fun parseReply(bytes: ByteArray): String {
        val root = JSONObject(String(bytes, Charsets.UTF_8))
        val choices = root.optJSONArray("choices") ?: throw IllegalStateException("choices missing")
        val first = choices.optJSONObject(0) ?: throw IllegalStateException("choice missing")
        val message = first.optJSONObject("message") ?: throw IllegalStateException("message missing")
        val content = message.optString("content", "").trim()
        require(content.isNotEmpty()) { "reply empty" }
        return content.take(DialogueRouter.MAX_REPLY_CHARS)
    }

    private fun readBounded(input: java.io.InputStream, limit: Int): ByteArray {
        input.use { stream ->
            val out = ByteArrayOutputStream(minOf(4096, limit))
            val buffer = ByteArray(4096)
            var total = 0
            while (true) {
                val count = stream.read(buffer)
                if (count <= 0) break
                total += count
                require(total <= limit) { "provider response too large" }
                out.write(buffer, 0, count)
            }
            return out.toByteArray()
        }
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 3_000
        private const val READ_TIMEOUT_MS = 20_000
        private const val MAX_REQUEST_BYTES = 8 * 1024
        private const val MAX_RESPONSE_BYTES = 32 * 1024
        private val IP_LITERAL = Regex("^(?:\\d{1,3}\\.){3}\\d{1,3}$|^[0-9a-fA-F:]+$")
    }
}

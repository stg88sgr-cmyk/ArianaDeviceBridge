package de.snowworks.ariana.bridge

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLException

/**
 * Small OpenAI-chat-completions-compatible HTTPS adapter.
 * Endpoint/model/key are user-configured; no credential is embedded in the app.
 */
class HttpsDialogueProvider(
    private val config: SecureAiProviderStore.Config,
) {
    fun generate(text: String): String {
        val uri = runCatching { URI(config.endpoint.trim()) }.getOrElse {
            throw DialogueRouter.ProviderException("PROVIDER_CONFIG_INVALID", it)
        }
        try {
            validateDestination(uri)
        } catch (error: DialogueRouter.ProviderException) {
            throw error
        } catch (error: Exception) {
            throw DialogueRouter.ProviderException("PROVIDER_TARGET_BLOCKED", error)
        }

        val connection = try {
            (uri.toURL().openConnection() as HttpsURLConnection).apply {
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
        } catch (error: Exception) {
            throw DialogueRouter.ProviderException("PROVIDER_CONFIG_INVALID", error)
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

        if (payload.size > MAX_REQUEST_BYTES) {
            throw DialogueRouter.ProviderException("PROVIDER_REQUEST_TOO_LARGE")
        }

        return try {
            connection.setFixedLengthStreamingMode(payload.size)
            connection.outputStream.use { it.write(payload) }
            val status = connection.responseCode
            if (status !in 200..299) throw httpError(status)
            val body = readBounded(connection.inputStream, MAX_RESPONSE_BYTES)
            parseReply(body)
        } catch (error: DialogueRouter.ProviderException) {
            throw error
        } catch (error: SocketTimeoutException) {
            throw DialogueRouter.ProviderException("PROVIDER_REMOTE_TIMEOUT", error)
        } catch (error: UnknownHostException) {
            throw DialogueRouter.ProviderException("PROVIDER_DNS_FAILED", error)
        } catch (error: SSLException) {
            throw DialogueRouter.ProviderException("PROVIDER_TLS_FAILED", error)
        } catch (error: IOException) {
            throw DialogueRouter.ProviderException("PROVIDER_NETWORK_FAILED", error)
        } catch (error: Exception) {
            throw DialogueRouter.ProviderException("PROVIDER_FAILED", error)
        } finally {
            connection.disconnect()
        }
    }

    private fun httpError(status: Int): DialogueRouter.ProviderException =
        DialogueRouter.ProviderException(
            when (status) {
                401, 403 -> "PROVIDER_AUTH_FAILED"
                408, 504 -> "PROVIDER_REMOTE_TIMEOUT"
                409 -> "PROVIDER_CONFLICT"
                413 -> "PROVIDER_REQUEST_TOO_LARGE"
                422 -> "PROVIDER_REQUEST_REJECTED"
                429 -> "PROVIDER_RATE_LIMITED"
                in 400..499 -> "PROVIDER_REQUEST_REJECTED"
                in 500..599 -> "PROVIDER_UPSTREAM_FAILED"
                else -> "PROVIDER_HTTP_FAILED"
            },
        )

    private fun validateDestination(uri: URI) {
        if (!uri.scheme.equals("https", ignoreCase = true)) {
            throw DialogueRouter.ProviderException("PROVIDER_CONFIG_INVALID")
        }
        val host = uri.host ?: throw DialogueRouter.ProviderException("PROVIDER_CONFIG_INVALID")
        if (uri.userInfo != null || uri.fragment != null) {
            throw DialogueRouter.ProviderException("PROVIDER_CONFIG_INVALID")
        }
        if (host.equals("localhost", ignoreCase = true) ||
            host.endsWith(".local", ignoreCase = true) ||
            IP_LITERAL.matches(host)
        ) {
            throw DialogueRouter.ProviderException("PROVIDER_TARGET_BLOCKED")
        }

        val addresses = try {
            InetAddress.getAllByName(host)
        } catch (error: UnknownHostException) {
            throw DialogueRouter.ProviderException("PROVIDER_DNS_FAILED", error)
        }
        if (addresses.isEmpty()) throw DialogueRouter.ProviderException("PROVIDER_DNS_FAILED")
        if (
            addresses.any {
                it.isAnyLocalAddress || it.isLoopbackAddress || it.isLinkLocalAddress ||
                    it.isSiteLocalAddress || it.isMulticastAddress
            }
        ) {
            throw DialogueRouter.ProviderException("PROVIDER_TARGET_BLOCKED")
        }
    }

    private fun parseReply(bytes: ByteArray): String {
        try {
            val root = JSONObject(String(bytes, Charsets.UTF_8))
            val choices = root.optJSONArray("choices")
                ?: throw DialogueRouter.ProviderException("PROVIDER_RESPONSE_INVALID")
            val first = choices.optJSONObject(0)
                ?: throw DialogueRouter.ProviderException("PROVIDER_RESPONSE_INVALID")
            val message = first.optJSONObject("message")
                ?: throw DialogueRouter.ProviderException("PROVIDER_RESPONSE_INVALID")
            val content = message.optString("content", "").trim()
            if (content.isEmpty()) throw DialogueRouter.ProviderException("PROVIDER_EMPTY_REPLY")
            return content.take(DialogueRouter.MAX_REPLY_CHARS)
        } catch (error: DialogueRouter.ProviderException) {
            throw error
        } catch (error: JSONException) {
            throw DialogueRouter.ProviderException("PROVIDER_RESPONSE_INVALID", error)
        }
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
                if (total > limit) throw DialogueRouter.ProviderException("PROVIDER_RESPONSE_TOO_LARGE")
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

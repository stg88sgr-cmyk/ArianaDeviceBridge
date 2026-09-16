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

class HttpsDialogueProvider(
    private val config: SecureAiProviderStore.Config,
) {
    fun generate(text: String): String {
        val policy = CloudAiPolicy.evaluate(text)
        val cloudText = when (policy.disposition) {
            CloudAiPolicy.Disposition.LOCAL_ONLY -> throw DialogueRouter.ProviderException(policy.reason ?: "CLOUD_POLICY_LOCAL_ONLY")
            CloudAiPolicy.Disposition.ALLOW,
            CloudAiPolicy.Disposition.REDACTED,
            -> policy.text
        }
        if (cloudText.isBlank()) throw DialogueRouter.ProviderException("CLOUD_POLICY_EMPTY")

        val uri = runCatching { URI(config.endpoint.trim()) }.getOrElse {
            throw DialogueRouter.ProviderException("PROVIDER_CONFIG_INVALID", it)
        }

        val payload = JSONObject()
            .put("model", config.model.trim())
            .put("stream", false)
            .put("temperature", 0.6)
            .put("reasoning_effort", "low")
            .put("max_completion_tokens", MAX_COMPLETION_TOKENS)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", "You are the dialogue provider for Ariana X-88. Reply naturally and concisely. Use German unless the user clearly requests another language. Do not claim device actions happened unless the bridge explicitly reports them."))
                    .put(JSONObject().put("role", "user").put("content", cloudText)),
            )
            .toString()
            .toByteArray(Charsets.UTF_8)

        if (payload.size > MAX_REQUEST_BYTES) throw DialogueRouter.ProviderException("PROVIDER_REQUEST_TOO_LARGE")

        var lastError: DialogueRouter.ProviderException? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                return executeOnce(uri, payload)
            } catch (error: DialogueRouter.ProviderException) {
                lastError = error
                if (attempt + 1 >= MAX_ATTEMPTS || !isRetryable(error.code)) throw error
                try {
                    Thread.sleep(RETRY_DELAY_MS)
                } catch (interrupted: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw DialogueRouter.ProviderException("PROVIDER_NETWORK_FAILED", interrupted)
                }
            }
        }
        throw lastError ?: DialogueRouter.ProviderException("PROVIDER_FAILED")
    }

    private fun executeOnce(uri: URI, payload: ByteArray): String {
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
                if (config.apiKey.isNotBlank()) setRequestProperty("Authorization", "Bearer ${config.apiKey.trim()}")
            }
        } catch (error: Exception) {
            throw DialogueRouter.ProviderException("PROVIDER_CONFIG_INVALID", error)
        }

        return try {
            connection.setFixedLengthStreamingMode(payload.size)
            connection.outputStream.use { it.write(payload) }
            val status = connection.responseCode
            if (status !in 200..299) throw httpError(status)
            parseReply(readBounded(connection.inputStream, MAX_RESPONSE_BYTES))
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

    private fun isRetryable(code: String): Boolean = code in setOf(
        "PROVIDER_REMOTE_TIMEOUT",
        "PROVIDER_DNS_FAILED",
        "PROVIDER_TLS_FAILED",
        "PROVIDER_NETWORK_FAILED",
        "PROVIDER_UPSTREAM_FAILED",
    )

    private fun httpError(status: Int) = DialogueRouter.ProviderException(
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
        if (!uri.scheme.equals("https", true)) throw DialogueRouter.ProviderException("PROVIDER_CONFIG_INVALID")
        val host = uri.host ?: throw DialogueRouter.ProviderException("PROVIDER_CONFIG_INVALID")
        if (uri.userInfo != null || uri.fragment != null) throw DialogueRouter.ProviderException("PROVIDER_CONFIG_INVALID")
        if (host.equals("localhost", true) || host.endsWith(".local", true) || IP_LITERAL.matches(host)) throw DialogueRouter.ProviderException("PROVIDER_TARGET_BLOCKED")
        val addresses = try { InetAddress.getAllByName(host) } catch (error: UnknownHostException) { throw DialogueRouter.ProviderException("PROVIDER_DNS_FAILED", error) }
        if (addresses.isEmpty()) throw DialogueRouter.ProviderException("PROVIDER_DNS_FAILED")
        if (addresses.any { it.isAnyLocalAddress || it.isLoopbackAddress || it.isLinkLocalAddress || it.isSiteLocalAddress || it.isMulticastAddress }) throw DialogueRouter.ProviderException("PROVIDER_TARGET_BLOCKED")
    }

    private fun parseReply(bytes: ByteArray): String {
        try {
            val root = JSONObject(String(bytes, Charsets.UTF_8))
            val choices = root.optJSONArray("choices") ?: throw DialogueRouter.ProviderException("PROVIDER_RESPONSE_INVALID")
            val first = choices.optJSONObject(0) ?: throw DialogueRouter.ProviderException("PROVIDER_RESPONSE_INVALID")
            val message = first.optJSONObject("message") ?: throw DialogueRouter.ProviderException("PROVIDER_RESPONSE_INVALID")
            val rawContent = message.opt("content")
            val content = when (rawContent) {
                null, JSONObject.NULL -> ""
                is String -> rawContent.trim()
                is JSONArray -> buildString {
                    for (index in 0 until rawContent.length()) {
                        val part = rawContent.optJSONObject(index) ?: continue
                        val textValue = part.opt("text")
                        if (textValue is String && textValue.isNotBlank()) {
                            if (isNotEmpty()) append(' ')
                            append(textValue.trim())
                        }
                    }
                }.trim()
                else -> ""
            }
            if (content.isEmpty()) {
                val finishReason = first.optString("finish_reason", "").trim()
                if (finishReason.equals("length", ignoreCase = true)) {
                    throw DialogueRouter.ProviderException("PROVIDER_OUTPUT_BUDGET_EXHAUSTED")
                }
                throw DialogueRouter.ProviderException("PROVIDER_EMPTY_REPLY")
            }
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
        private const val CONNECT_TIMEOUT_MS = 5_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val MAX_ATTEMPTS = 2
        private const val RETRY_DELAY_MS = 700L
        private const val MAX_COMPLETION_TOKENS = 4096
        private const val MAX_REQUEST_BYTES = 8 * 1024
        private const val MAX_RESPONSE_BYTES = 32 * 1024
        private val IP_LITERAL = Regex("^(?:\\d{1,3}\\.){3}\\d{1,3}$|^[0-9a-fA-F:]+$")
    }
}

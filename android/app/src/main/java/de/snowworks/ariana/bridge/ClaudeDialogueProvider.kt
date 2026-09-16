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
 * Native Anthropic Messages API adapter.
 *
 * Ariana remains the visible identity and orchestration layer. Claude is only
 * an external reasoning provider selected by the bridge.
 */
class ClaudeDialogueProvider(
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
        if (config.apiKey.isBlank()) throw DialogueRouter.ProviderException("PROVIDER_KEY_MISSING")

        val uri = runCatching { URI(config.endpoint.trim()) }.getOrElse {
            throw DialogueRouter.ProviderException("PROVIDER_CONFIG_INVALID", it)
        }
        validateDestination(uri)

        val payload = JSONObject()
            .put("model", config.model.trim())
            .put("max_tokens", MAX_TOKENS)
            .put(
                "system",
                "You are an external reasoning provider for Ariana X-88. Ariana remains the user-facing identity and controller. Reply naturally and concisely. Use German unless the user clearly requests another language. Never claim a device action happened unless the bridge explicitly reports it.",
            )
            .put(
                "messages",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put("content", cloudText),
                ),
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
                setRequestProperty("x-api-key", config.apiKey.trim())
                setRequestProperty("anthropic-version", ANTHROPIC_VERSION)
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

    private fun parseReply(bytes: ByteArray): String {
        try {
            val root = JSONObject(String(bytes, Charsets.UTF_8))
            val content = root.optJSONArray("content") ?: throw DialogueRouter.ProviderException("PROVIDER_RESPONSE_INVALID")
            val text = buildString {
                for (index in 0 until content.length()) {
                    val part = content.optJSONObject(index) ?: continue
                    if (!part.optString("type").equals("text", ignoreCase = true)) continue
                    val value = part.optString("text", "").trim()
                    if (value.isNotBlank()) {
                        if (isNotEmpty()) append('\n')
                        append(value)
                    }
                }
            }.trim()
            if (text.isEmpty()) {
                val stopReason = root.optString("stop_reason", "").trim()
                if (stopReason.equals("max_tokens", ignoreCase = true)) {
                    throw DialogueRouter.ProviderException("PROVIDER_OUTPUT_BUDGET_EXHAUSTED")
                }
                throw DialogueRouter.ProviderException("PROVIDER_EMPTY_REPLY")
            }
            return text.take(DialogueRouter.MAX_REPLY_CHARS)
        } catch (error: DialogueRouter.ProviderException) {
            throw error
        } catch (error: JSONException) {
            throw DialogueRouter.ProviderException("PROVIDER_RESPONSE_INVALID", error)
        }
    }

    private fun validateDestination(uri: URI) {
        if (!uri.scheme.equals("https", true)) throw DialogueRouter.ProviderException("PROVIDER_CONFIG_INVALID")
        val host = uri.host ?: throw DialogueRouter.ProviderException("PROVIDER_CONFIG_INVALID")
        if (!host.equals(ANTHROPIC_HOST, true)) throw DialogueRouter.ProviderException("PROVIDER_TARGET_BLOCKED")
        if (uri.path.trimEnd('/') != ANTHROPIC_PATH) throw DialogueRouter.ProviderException("PROVIDER_CONFIG_INVALID")
        if (uri.userInfo != null || uri.fragment != null || uri.rawQuery != null) throw DialogueRouter.ProviderException("PROVIDER_CONFIG_INVALID")
        val addresses = try {
            InetAddress.getAllByName(host)
        } catch (error: UnknownHostException) {
            throw DialogueRouter.ProviderException("PROVIDER_DNS_FAILED", error)
        }
        if (addresses.isEmpty()) throw DialogueRouter.ProviderException("PROVIDER_DNS_FAILED")
        if (addresses.any { it.isAnyLocalAddress || it.isLoopbackAddress || it.isLinkLocalAddress || it.isSiteLocalAddress || it.isMulticastAddress }) {
            throw DialogueRouter.ProviderException("PROVIDER_TARGET_BLOCKED")
        }
    }

    private fun isRetryable(code: String): Boolean = code in setOf(
        "PROVIDER_REMOTE_TIMEOUT",
        "PROVIDER_DNS_FAILED",
        "PROVIDER_TLS_FAILED",
        "PROVIDER_NETWORK_FAILED",
        "PROVIDER_UPSTREAM_FAILED",
        "PROVIDER_RATE_LIMITED",
    )

    private fun httpError(status: Int) = DialogueRouter.ProviderException(
        when (status) {
            401, 403 -> "PROVIDER_AUTH_FAILED"
            408, 504 -> "PROVIDER_REMOTE_TIMEOUT"
            413 -> "PROVIDER_REQUEST_TOO_LARGE"
            429 -> "PROVIDER_RATE_LIMITED"
            in 400..499 -> "PROVIDER_REQUEST_REJECTED"
            in 500..599 -> "PROVIDER_UPSTREAM_FAILED"
            else -> "PROVIDER_HTTP_FAILED"
        },
    )

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
        const val ANTHROPIC_ENDPOINT = "https://api.anthropic.com/v1/messages"
        const val ANTHROPIC_HOST = "api.anthropic.com"
        private const val ANTHROPIC_PATH = "/v1/messages"
        private const val ANTHROPIC_VERSION = "2023-06-01"
        private const val CONNECT_TIMEOUT_MS = 5_000
        private const val READ_TIMEOUT_MS = 45_000
        private const val MAX_ATTEMPTS = 2
        private const val RETRY_DELAY_MS = 800L
        private const val MAX_TOKENS = 4096
        private const val MAX_REQUEST_BYTES = 12 * 1024
        private const val MAX_RESPONSE_BYTES = 64 * 1024
    }
}

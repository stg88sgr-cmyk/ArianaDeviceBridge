package de.snowworks.ariana.core.model

import android.content.Context
import de.snowworks.ariana.core.AuditLog
import de.snowworks.ariana.core.NetworkGate
import java.net.HttpURLConnection
import java.net.URL

/**
 * Routes model traffic only after NetworkGate approval.
 * No provider is selected by default and no network access is enabled here.
 */
class ModelRouter(context: Context) {
    data class Response(
        val statusCode: Int,
        val body: String,
        val providerId: String,
    )

    private val app = context.applicationContext
    private val providers = ModelProviderStore(app)
    private val secrets = ProviderSecretStore(app)
    private val gate = NetworkGate(app)
    private val audit = AuditLog(app)

    fun activeProvider(): ModelProviderStore.Provider? = providers.active()

    fun sendJson(payload: String): Response {
        val provider = providers.active() ?: error("No active model provider configured.")
        val bytes = payload.toByteArray(Charsets.UTF_8)
        val request = NetworkGate.Request(
            destination = provider.endpoint,
            purpose = "model_request:${provider.id}",
            provider = provider.label,
            payload = bytes,
        )
        gate.requireAllowed(request)

        val connection = (URL(provider.endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            secrets.get(provider.id)?.let { token ->
                setRequestProperty("Authorization", "Bearer $token")
            }
        }

        return try {
            connection.outputStream.use { it.write(bytes) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                val text = reader.readText()
                require(text.toByteArray(Charsets.UTF_8).size <= MAX_RESPONSE_BYTES) { "Model response is too large." }
                text
            }.orEmpty()

            audit.append(
                AuditLog.Event(
                    category = "model_router",
                    action = "response",
                    decision = if (code in 200..299) "OK" else "HTTP_$code",
                    destination = provider.endpoint,
                    provider = provider.label,
                    payloadBytes = body.toByteArray(Charsets.UTF_8).size,
                    payloadSha256 = AuditLog.sha256(body.toByteArray(Charsets.UTF_8)),
                ),
            )
            Response(code, body, provider.id)
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 60_000
        private const val MAX_RESPONSE_BYTES = 4 * 1024 * 1024
    }
}

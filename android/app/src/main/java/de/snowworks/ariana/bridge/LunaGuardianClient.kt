package de.snowworks.ariana.bridge

import android.content.Context
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Narrow X-88 -> Luna Guardian control channel.
 *
 * Transport is loopback only. The Luna hub remains responsible for applying
 * Android VpnService policy. No device-wide network change is made here.
 */
class LunaGuardianClient(context: Context) {
    enum class Mode { OFFLINE, ASK, ONLINE }

    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isPaired(): Boolean = !prefs.getString(KEY_TOKEN, null).isNullOrBlank()

    fun pair(token: String) {
        val normalized = token.trim()
        require(normalized.length in 24..256) { "invalid Luna token length" }
        prefs.edit().putString(KEY_TOKEN, normalized).apply()
    }

    fun unpair() {
        prefs.edit().remove(KEY_TOKEN).apply()
    }

    fun setMode(mode: Mode): ArianaResult = request(
        actionId = "luna_mode_${System.currentTimeMillis()}",
        path = "/x88/network/mode",
        payload = JSONObject().put("mode", mode.name),
    )

    fun grantLease(durationSeconds: Int, reason: String): ArianaResult {
        require(durationSeconds in 30..3600) { "lease duration out of range" }
        val safeReason = reason
            .replace(Regex("[\\r\\n\\t]+"), " ")
            .trim()
            .take(120)
        return request(
            actionId = "luna_lease_${System.currentTimeMillis()}",
            path = "/x88/network/lease",
            payload = JSONObject()
                .put("durationSeconds", durationSeconds)
                .put("reason", safeReason),
        )
    }

    fun revokeLease(): ArianaResult = request(
        actionId = "luna_revoke_${System.currentTimeMillis()}",
        path = "/x88/network/lease/revoke",
        payload = JSONObject(),
    )

    fun status(): ArianaResult = request(
        actionId = "luna_status_${System.currentTimeMillis()}",
        path = "/x88/status",
        payload = JSONObject(),
    )

    private fun request(actionId: String, path: String, payload: JSONObject): ArianaResult {
        val token = prefs.getString(KEY_TOKEN, null)
            ?: return ArianaResult.denied(actionId, "LUNA_NOT_PAIRED", "Luna Guardian is not paired")

        val endpoint = "$BASE_URL$path"
        val egress = X88EgressPolicy.evaluate(
            actor = X88EgressPolicy.Actor.LUNA_XXY,
            purpose = X88EgressPolicy.Purpose.LOCAL_BRIDGE,
            endpoint = endpoint,
        )
        if (!egress.allowed) {
            return ArianaResult.blocked(actionId, egress.code, "Loopback policy blocked Luna control")
        }

        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            connectTimeout = 2_500
            readTimeout = 5_000
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("X-X88-Actor", "ARIANA_X88")
        }

        return try {
            connection.outputStream.use { output ->
                output.write(payload.toString().toByteArray(Charsets.UTF_8))
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty().take(4096)
            val response = runCatching { JSONObject(body) }.getOrNull()
            val remoteCode = response?.optString("code")?.takeIf { it.isNotBlank() }
                ?: if (code in 200..299) "LUNA_OK" else "LUNA_HTTP_$code"

            X88SecurityAudit.record(
                actor = "ARIANA_X88",
                event = if (code in 200..299) "luna_control_ok" else "luna_control_failed",
                detail = "path=$path;code=$remoteCode",
            )

            if (code in 200..299) {
                ArianaResult.ok(
                    actionId = actionId,
                    code = remoteCode,
                    message = response?.optString("message")?.take(160).orEmpty().ifBlank { "Luna command applied" },
                    metadata = mapOf("path" to path),
                )
            } else {
                ArianaResult.error(
                    actionId = actionId,
                    code = remoteCode,
                    message = response?.optString("message")?.take(160).orEmpty().ifBlank { "Luna command failed" },
                    metadata = mapOf("path" to path, "http" to code.toString()),
                )
            }
        } catch (t: Throwable) {
            X88SecurityAudit.record(
                actor = "ARIANA_X88",
                event = "luna_control_unreachable",
                detail = "path=$path;error=${t.javaClass.simpleName}",
            )
            ArianaResult.error(
                actionId = actionId,
                code = "LUNA_UNREACHABLE",
                message = t.message?.take(160) ?: "Luna Guardian unreachable",
            )
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val BASE_URL = "http://127.0.0.1:8766"
        const val PREFS = "x88_luna_guardian"
        const val KEY_TOKEN = "pairing_token_v1"
    }
}

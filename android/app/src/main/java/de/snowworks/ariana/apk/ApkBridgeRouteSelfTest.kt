package de.snowworks.ariana.apk

import android.content.Context
import de.snowworks.ariana.bridge.BridgeTokenStore
import de.snowworks.ariana.bridge.LocalBridgeServer
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket

object ApkBridgeRouteSelfTest {
    data class Check(val name: String, val ok: Boolean, val detail: String)
    data class Result(val ok: Boolean, val checks: List<Check>)

    fun run(context: Context): Result {
        if (!LocalBridgeServer.isRunning()) {
            return Result(false, listOf(Check("APK bridge routes", false, "Lokale Bridge läuft nicht.")))
        }
        val token = BridgeTokenStore(context.applicationContext).getOrCreate()
        val checks = mutableListOf<Check>()

        checks += checkRoute(token, "/v2/apk/status", "ariana-apk-status-v1", "APK status route")
        checks += checkRoute(token, "/v2/apk/list", "ariana-apk-list-v1", "APK list route")

        val inspect = request("GET", "/v2/apk/inspect/latest", token)
        val inspectOk = inspect.status == 200 && (
            inspect.json?.optString("model") == "ariana-apk-inspection-v1" ||
                inspect.json?.optString("error") == "APK_NOT_FOUND"
            )
        checks += Check(
            name = "APK inspect route",
            ok = inspectOk,
            detail = if (inspectOk) {
                if (inspect.json?.optString("error") == "APK_NOT_FOUND") {
                    "Route erreichbar; aktuell liegt keine APK im Download-Bereich."
                } else {
                    "Route erreichbar und liefert eine APK-Inspektion."
                }
            } else {
                "Unerwartete Antwort: HTTP ${inspect.status}."
            },
        )

        val unauthorized = request("GET", "/v2/apk/status", "invalid-selftest-token")
        val authOk = unauthorized.status == 401 && unauthorized.json?.optString("error") == "UNAUTHORIZED"
        checks += Check(
            name = "APK route token gate",
            ok = authOk,
            detail = if (authOk) "Ungültiges Bridge-Token wird korrekt abgewiesen." else "Token-Gate lieferte HTTP ${unauthorized.status}.",
        )

        return Result(checks.all { it.ok }, checks)
    }

    private fun checkRoute(token: String, path: String, expectedModel: String, name: String): Check {
        val response = request("GET", path, token)
        val ok = response.status == 200 &&
            response.json?.optBoolean("ok", false) == true &&
            response.json.optString("model") == expectedModel
        return Check(
            name = name,
            ok = ok,
            detail = if (ok) "$path antwortet korrekt mit $expectedModel." else "Unerwartete Antwort: HTTP ${response.status}.",
        )
    }

    private data class HttpResponse(val status: Int, val json: JSONObject?)

    private fun request(method: String, path: String, token: String): HttpResponse {
        Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", LocalBridgeServer.PORT), 1_500)
            socket.soTimeout = 2_500
            val request = buildString {
                append("$method $path HTTP/1.1\r\n")
                append("Host: 127.0.0.1:${LocalBridgeServer.PORT}\r\n")
                append("Accept: application/json\r\n")
                append("X-Ariana-Token: $token\r\n")
                append("Connection: close\r\n\r\n")
            }
            socket.getOutputStream().apply {
                write(request.toByteArray(Charsets.US_ASCII))
                flush()
            }
            val raw = socket.getInputStream().readBytes().toString(Charsets.UTF_8)
            val split = raw.indexOf("\r\n\r\n")
            require(split > 0) { "incomplete HTTP response" }
            val status = raw.substringBefore("\r\n").split(' ').getOrNull(1)?.toIntOrNull()
                ?: error("invalid HTTP status")
            val body = raw.substring(split + 4)
            return HttpResponse(status, body.takeIf { it.isNotBlank() }?.let { runCatching { JSONObject(it) }.getOrNull() })
        }
    }
}

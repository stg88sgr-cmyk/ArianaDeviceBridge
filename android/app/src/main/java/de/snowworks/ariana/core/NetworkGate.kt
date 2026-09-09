package de.snowworks.ariana.core

import android.content.Context
import java.net.IDN
import java.net.URI

/**
 * App-level deny-by-default egress policy.
 *
 * This class does not pretend to be an Android-wide firewall. It is the single
 * policy gate that every future X-Ariana HTTP/model client must call before it
 * opens a connection. External access starts OFF and the external allow-list
 * starts empty. Loopback traffic is treated separately for local models/tools.
 */
class NetworkGate(context: Context) {
    enum class Decision { ALLOW, DENY }

    data class Request(
        val destination: String,
        val purpose: String,
        val provider: String? = null,
        val payload: ByteArray? = null,
    )

    data class Result(
        val decision: Decision,
        val reason: String,
        val canonicalHost: String? = null,
    ) {
        val allowed: Boolean get() = decision == Decision.ALLOW
    }

    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val audit = AuditLog(app)

    val isExternalAccessEnabled: Boolean
        get() = prefs.getBoolean(KEY_EXTERNAL_ENABLED, false)

    fun setExternalAccessEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_EXTERNAL_ENABLED, enabled).apply()
        audit.append(
            AuditLog.Event(
                category = "network_policy",
                action = "external_access",
                decision = if (enabled) "ENABLED" else "DISABLED",
                reason = "Explicit local policy change",
            ),
        )
    }

    fun allowedExternalHosts(): Set<String> =
        prefs.getStringSet(KEY_ALLOWED_HOSTS, emptySet()).orEmpty().toSet()

    fun allowExternalHost(host: String) {
        val canonical = canonicalizeHost(host)
        require(!isLoopback(canonical)) { "Loopback does not belong in the external allow-list." }
        val updated = allowedExternalHosts().toMutableSet().apply { add(canonical) }
        prefs.edit().putStringSet(KEY_ALLOWED_HOSTS, updated).apply()
        audit.append(
            AuditLog.Event(
                category = "network_policy",
                action = "allow_host",
                decision = "ALLOWLISTED",
                destination = canonical,
                reason = "Explicit local policy change",
            ),
        )
    }

    fun removeExternalHost(host: String) {
        val canonical = canonicalizeHost(host)
        val updated = allowedExternalHosts().toMutableSet().apply { remove(canonical) }
        prefs.edit().putStringSet(KEY_ALLOWED_HOSTS, updated).apply()
        audit.append(
            AuditLog.Event(
                category = "network_policy",
                action = "remove_host",
                decision = "REMOVED",
                destination = canonical,
                reason = "Explicit local policy change",
            ),
        )
    }

    fun clearExternalHosts() {
        prefs.edit().remove(KEY_ALLOWED_HOSTS).apply()
        audit.append(
            AuditLog.Event(
                category = "network_policy",
                action = "clear_hosts",
                decision = "CLEARED",
                reason = "External allow-list cleared",
            ),
        )
    }

    fun evaluate(request: Request): Result {
        val result = evaluateInternal(request)
        val payload = request.payload
        audit.append(
            AuditLog.Event(
                category = "network_egress",
                action = request.purpose.take(MAX_PURPOSE_CHARS),
                decision = result.decision.name,
                reason = result.reason,
                destination = request.destination.take(MAX_DESTINATION_CHARS),
                provider = request.provider?.take(MAX_PROVIDER_CHARS),
                payloadBytes = payload?.size,
                payloadSha256 = payload?.let(AuditLog::sha256),
            ),
        )
        return result
    }

    fun requireAllowed(request: Request) {
        val result = evaluate(request)
        check(result.allowed) { "Network request blocked: ${result.reason}" }
    }

    private fun evaluateInternal(request: Request): Result {
        if (request.purpose.isBlank()) return Result(Decision.DENY, "Purpose is required.")
        if (request.payload != null && request.payload.size > MAX_PAYLOAD_BYTES) {
            return Result(Decision.DENY, "Payload exceeds local policy limit.")
        }

        val uri = runCatching { URI(request.destination) }.getOrElse {
            return Result(Decision.DENY, "Destination is not a valid URI.")
        }
        if (uri.userInfo != null) return Result(Decision.DENY, "URI user-info is forbidden.")
        val rawHost = uri.host ?: return Result(Decision.DENY, "Destination host is missing.")
        val host = runCatching { canonicalizeHost(rawHost) }.getOrElse {
            return Result(Decision.DENY, "Destination host is invalid.")
        }

        if (isLoopback(host)) {
            if (uri.scheme !in setOf("http", "https")) {
                return Result(Decision.DENY, "Only HTTP(S) loopback traffic is allowed.", host)
            }
            return Result(Decision.ALLOW, "Loopback destination.", host)
        }

        if (uri.scheme != "https") return Result(Decision.DENY, "External traffic requires HTTPS.", host)
        if (uri.port !in setOf(-1, 443)) return Result(Decision.DENY, "External HTTPS must use the default port.", host)
        if (!isExternalAccessEnabled) return Result(Decision.DENY, "External access is disabled.", host)
        if (host !in allowedExternalHosts()) return Result(Decision.DENY, "Host is not allow-listed.", host)

        return Result(Decision.ALLOW, "External host explicitly allow-listed.", host)
    }

    private fun canonicalizeHost(host: String): String {
        val trimmed = host.trim().trimEnd('.').lowercase()
        require(trimmed.isNotEmpty() && trimmed.length <= 253) { "Invalid host." }
        val ascii = IDN.toASCII(trimmed, IDN.USE_STD3_ASCII_RULES).lowercase()
        require(ascii.matches(HOST_REGEX) || isLoopback(ascii)) { "Invalid host." }
        return ascii
    }

    private fun isLoopback(host: String): Boolean =
        host == "localhost" || host == "127.0.0.1" || host == "::1"

    companion object {
        private const val PREFS = "x_ariana_network_gate"
        private const val KEY_EXTERNAL_ENABLED = "external_enabled"
        private const val KEY_ALLOWED_HOSTS = "allowed_hosts"
        private const val MAX_PAYLOAD_BYTES = 2 * 1024 * 1024
        private const val MAX_PURPOSE_CHARS = 128
        private const val MAX_DESTINATION_CHARS = 512
        private const val MAX_PROVIDER_CHARS = 64
        private val HOST_REGEX = Regex("(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)*[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")
    }
}

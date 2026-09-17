package de.snowworks.ariana.image.generator

import java.net.URI

object EndpointSecurity {
    private val allowedHosts = setOf("127.0.0.1", "localhost", "::1")
    private val allowedSchemes = setOf("http", "https")

    data class ValidatedEndpoint(val host: String, val port: Int, val uri: URI)

    fun validate(endpoint: String): ValidatedEndpoint {
        if (endpoint.isBlank()) throw SecurityException("blank endpoint")
        val uri = try { URI(endpoint.trim()) } catch (_: Exception) { throw SecurityException("malformed URI") }
        val scheme = uri.scheme?.lowercase() ?: throw SecurityException("missing scheme")
        if (scheme !in allowedSchemes) throw SecurityException("unsupported scheme: $scheme")
        if (uri.userInfo != null || (uri.rawAuthority ?: "").contains('@')) throw SecurityException("userinfo not allowed")
        val rawHost = uri.host ?: throw SecurityException("blank host")
        val host = rawHost.lowercase().trim().removePrefix("[").removeSuffix("]")
        if (host !in allowedHosts) throw SecurityException("remote host rejected: $rawHost")
        val port = uri.port
        if (port != -1 && port !in 1..65535) throw SecurityException("invalid port: $port")
        return ValidatedEndpoint(host, if (port == -1) defaultPort(scheme) else port, uri)
    }

    fun isAllowedHost(host: String?): Boolean {
        if (host == null) return false
        return host.lowercase().trim().removePrefix("[").removeSuffix("]") in allowedHosts
    }

    private fun defaultPort(scheme: String) = if (scheme == "https") 443 else 80
}

package de.snowworks.ariana.bridge

import java.net.URI

/**
 * Application-layer egress firewall for X-88 controlled AI traffic.
 *
 * This gate is deny-by-default. It protects network calls made through the
 * guarded bridge/provider paths. It is not a device-wide OS firewall.
 */
object X88EgressPolicy {
    enum class Actor { ARIANA_X88, LUNA_XXY, META_AI }
    enum class Purpose { LOCAL_BRIDGE, META_EXECUTION, GITHUB_SOURCE }

    data class Decision(
        val allowed: Boolean,
        val code: String,
        val host: String = "",
        val port: Int = -1,
        val purpose: Purpose,
    )

    private data class Rule(
        val actor: Actor,
        val purpose: Purpose,
        val hostMatcher: (String) -> Boolean,
        val ports: Set<Int>,
        val requireHttps: Boolean,
    )

    private val rules = listOf(
        Rule(
            actor = Actor.LUNA_XXY,
            purpose = Purpose.LOCAL_BRIDGE,
            hostMatcher = { it == "127.0.0.1" || it == "localhost" },
            ports = setOf(8765, 44417),
            requireHttps = false,
        ),
        Rule(
            actor = Actor.LUNA_XXY,
            purpose = Purpose.META_EXECUTION,
            hostMatcher = { it == "api.meta.ai" || it.endsWith(".meta.ai") },
            ports = setOf(443),
            requireHttps = true,
        ),
        Rule(
            actor = Actor.LUNA_XXY,
            purpose = Purpose.GITHUB_SOURCE,
            hostMatcher = {
                it == "api.github.com" ||
                    it == "github.com" ||
                    it == "raw.githubusercontent.com"
            },
            ports = setOf(443),
            requireHttps = true,
        ),
    )

    fun evaluate(actor: Actor, purpose: Purpose, endpoint: String): Decision {
        val uri = runCatching { URI(endpoint.trim()) }.getOrNull()
            ?: return blocked(actor, purpose, "EGRESS_INVALID_URI")

        val host = uri.host?.lowercase()?.trim().orEmpty()
        if (host.isBlank()) return blocked(actor, purpose, "EGRESS_HOST_REQUIRED")
        if (uri.userInfo != null || uri.fragment != null) {
            return blocked(actor, purpose, "EGRESS_URI_COMPONENT_BLOCKED", host)
        }

        val scheme = uri.scheme?.lowercase().orEmpty()
        val port = when {
            uri.port > 0 -> uri.port
            scheme == "https" -> 443
            scheme == "http" -> 80
            else -> -1
        }

        val rule = rules.firstOrNull {
            it.actor == actor &&
                it.purpose == purpose &&
                it.hostMatcher(host) &&
                port in it.ports &&
                (!it.requireHttps || scheme == "https")
        }

        if (rule == null) {
            return blocked(actor, purpose, "EGRESS_DENY_DEFAULT", host, port)
        }

        X88SecurityAudit.record(
            actor = actor.name,
            event = "egress_allow",
            detail = "purpose=${purpose.name};host=$host;port=$port",
        )
        return Decision(true, "EGRESS_ALLOWED", host, port, purpose)
    }

    fun evaluateMeta(config: SecureAiProviderStore.Config): Decision =
        evaluate(Actor.LUNA_XXY, Purpose.META_EXECUTION, config.endpoint)

    private fun blocked(
        actor: Actor,
        purpose: Purpose,
        code: String,
        host: String = "",
        port: Int = -1,
    ): Decision {
        X88SecurityAudit.record(
            actor = actor.name,
            event = "egress_block",
            detail = "code=$code;purpose=${purpose.name};host=$host;port=$port",
        )
        return Decision(false, code, host, port, purpose)
    }
}

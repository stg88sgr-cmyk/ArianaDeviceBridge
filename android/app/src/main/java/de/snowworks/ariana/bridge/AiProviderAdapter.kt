package de.snowworks.ariana.bridge

/**
 * Provider-neutral boundary for Ariana dialogue backends.
 *
 * Implementations may be local or remote. The adapter exposes identity and
 * generation only; orchestration owns routing and safety policy.
 */
interface AiProviderAdapter {
    val id: String
    val timeoutMs: Long
    fun generate(text: String): String
}

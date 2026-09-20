package de.snowworks.ariana.bridge

/**
 * Provider-neutral boundary for Ariana dialogue backends.
 *
 * Implementations may be local or remote. The router owns lifecycle, input
 * sanitization and timeout handling; adapters only expose identity and generation.
 */
interface AiProviderAdapter {
    val id: String
    val timeoutMs: Long
    fun generate(text: String): String
}

package de.snowworks.ariana.bridge

/**
 * Provider-neutral boundary for optional external review backends.
 *
 * The reviewer does not own Ariana identity, memory or policy.
 */
interface AiProviderAdapter {
    val id: String
    val timeoutMs: Long
    fun generate(text: String): String
}

package de.snowworks.ariana

/**
 * Stable technical identity boundary for ARIANA/X-88.
 *
 * The identity is owned by the application layer, not by any external AI provider.
 * Providers such as Meta AI are execution/capability backends behind this boundary.
 *
 * This object carries identity metadata only. It does not claim consciousness,
 * human memory, feelings, or personhood.
 */
data class X88Identity(
    val projectId: String = "ARIANA-X88",
    val identityId: String = "X-88",
    val applicationName: String = "ARIANA",
    val continuityContract: String = "docs/X88-CONTINUITY.md",
) {
    companion object {
        val current = X88Identity()
    }
}

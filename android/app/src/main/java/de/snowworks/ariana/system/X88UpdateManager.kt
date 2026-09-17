package de.snowworks.ariana.system

data class X88UpdateDescriptor(
    val version: X88Version,
    val artifactUrl: String,
    val sha256: String,
)

class X88UpdateManager(
    private val currentVersion: X88Version = X88Version.CURRENT,
) {
    fun isEligible(candidate: X88UpdateDescriptor): Boolean =
        candidate.version > currentVersion &&
            candidate.artifactUrl.startsWith("https://") &&
            candidate.sha256.matches(Regex("^[a-fA-F0-9]{64}$"))
}

package de.snowworks.ariana.system

/**
 * Canonical version identity for the X88 layer.
 *
 * X88 versions are intentionally independent from Samsung One UI and Android
 * versions. The platform version is observed, never overwritten here.
 */
data class X88Version(
    val major: Int,
    val minor: Int,
    val patch: Int = 0,
    val channel: X88UpdateChannel = X88UpdateChannel.STABLE,
) : Comparable<X88Version> {

    init {
        require(major >= 0) { "major must be >= 0" }
        require(minor >= 0) { "minor must be >= 0" }
        require(patch >= 0) { "patch must be >= 0" }
    }

    override fun compareTo(other: X88Version): Int =
        compareValuesBy(this, other, X88Version::major, X88Version::minor, X88Version::patch)

    fun displayName(): String = buildString {
        append("X88 UI ")
        append(major)
        append('.')
        append(minor)
        if (patch != 0) {
            append('.')
            append(patch)
        }
        if (channel != X88UpdateChannel.STABLE) {
            append(" ")
            append(channel.label)
        }
    }

    companion object {
        val CURRENT = X88Version(
            major = 1,
            minor = 0,
            patch = 0,
            channel = X88UpdateChannel.DEV,
        )
    }
}

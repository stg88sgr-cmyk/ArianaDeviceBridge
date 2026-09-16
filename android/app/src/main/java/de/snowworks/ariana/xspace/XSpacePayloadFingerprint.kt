package de.snowworks.ariana.xspace

import java.security.MessageDigest

/** Pure JVM helper for binding approvals to the exact reviewed X Space payload. */
object XSpacePayloadFingerprint {

    fun canonical(
        action: String,
        spaceUrl: String = "",
        text: String = "",
        gatewayToken: String = "",
    ): String {
        val normalizedAction = action.trim().lowercase()
        return when (normalizedAction) {
            "xspace_join" -> "spaceUrl=" + spaceUrl.trim()
            "xspace_speak" -> "text=" + text.trim().take(2000)
            "xspace_connect" -> "gatewayToken=" + gatewayToken.trim()
            "xspace_leave", "xspace_mute", "xspace_unmute" -> ""
            else -> error("Unsupported X Space approval action: $normalizedAction")
        }
    }

    fun sha256(action: String, canonicalPayload: String): String {
        val normalizedAction = action.trim().lowercase()
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest("$normalizedAction\n$canonicalPayload".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun forValues(
        action: String,
        spaceUrl: String = "",
        text: String = "",
        gatewayToken: String = "",
    ): String = sha256(
        action = action,
        canonicalPayload = canonical(action, spaceUrl, text, gatewayToken),
    )
}

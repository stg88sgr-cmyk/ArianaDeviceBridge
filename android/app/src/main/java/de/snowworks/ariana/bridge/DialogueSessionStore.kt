package de.snowworks.ariana.bridge

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * In-memory pairing/session state for Ariana X-88 dialogue traffic.
 * Pairing codes and bearer tokens are never written to disk.
 */
object DialogueSessionStore {
    const val PAIRING_TTL_MS = 120_000L
    const val SESSION_TTL_MS = 600_000L

    data class PairingCode(
        val code: String,
        val expiresAtMs: Long,
    )

    data class SessionGrant(
        val token: String,
        val expiresAtMs: Long,
    )

    private data class PairingState(
        val code: String,
        val expiresAtMs: Long,
    )

    private data class SessionState(
        val token: String,
        val expiresAtMs: Long,
    )

    private val random = SecureRandom()
    private const val PAIRING_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    @Volatile private var pairing: PairingState? = null
    @Volatile private var session: SessionState? = null

    @Synchronized
    fun beginPairing(nowMs: Long = System.currentTimeMillis()): PairingCode {
        val code = buildString(8) {
            repeat(8) {
                append(PAIRING_ALPHABET[random.nextInt(PAIRING_ALPHABET.length)])
            }
        }
        val expiresAt = nowMs + PAIRING_TTL_MS
        pairing = PairingState(code, expiresAt)
        return PairingCode(code, expiresAt)
    }

    @Synchronized
    fun exchange(pairingCode: String, nowMs: Long = System.currentTimeMillis()): SessionGrant? {
        val current = pairing ?: return null
        if (nowMs > current.expiresAtMs) {
            pairing = null
            return null
        }

        val normalized = pairingCode.trim().uppercase()
        val matches = MessageDigest.isEqual(
            normalized.toByteArray(Charsets.UTF_8),
            current.code.toByteArray(Charsets.UTF_8),
        )
        if (!matches) return null

        pairing = null
        val bytes = ByteArray(32).also(random::nextBytes)
        val token = Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)
        val expiresAt = nowMs + SESSION_TTL_MS
        session = SessionState(token, expiresAt)
        return SessionGrant(token, expiresAt)
    }

    @Synchronized
    fun validateBearer(authorization: String?, nowMs: Long = System.currentTimeMillis()): Boolean {
        val current = session ?: return false
        if (nowMs > current.expiresAtMs) {
            session = null
            return false
        }
        val supplied = authorization
            ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
            ?.substringAfter(' ')
            ?.trim()
            .orEmpty()
        if (supplied.isEmpty()) return false
        return MessageDigest.isEqual(
            supplied.toByteArray(Charsets.UTF_8),
            current.token.toByteArray(Charsets.UTF_8),
        )
    }

    @Synchronized
    fun revoke() {
        pairing = null
        session = null
    }

    @Synchronized
    fun hasActiveSession(nowMs: Long = System.currentTimeMillis()): Boolean {
        val current = session ?: return false
        if (nowMs > current.expiresAtMs) {
            session = null
            return false
        }
        return true
    }
}

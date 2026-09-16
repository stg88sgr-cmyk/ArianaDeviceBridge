package de.snowworks.ariana.xspace

import org.json.JSONObject

/**
 * Volatile review queue for X Space actions that require visible user approval.
 *
 * The UI gets a sanitized summary, while the exact payload stays local in memory
 * and is retrieved only by the confirmed executor. Secrets such as gatewayToken
 * are never exposed through the review model.
 */
object XSpaceApprovalRequestStore {
    private const val TTL_MS = 2 * 60 * 1000L
    private const val MAX_ENTRIES = 12

    data class ReviewRequest(
        val proposalId: String,
        val action: String,
        val summary: String,
        val createdAtMs: Long,
        val expiresAtMs: Long,
    )

    private data class Entry(
        val review: ReviewRequest,
        val payloadJson: String,
    )

    private val entries = LinkedHashMap<String, Entry>()

    @Synchronized
    fun register(
        proposalId: String,
        action: String,
        payload: JSONObject,
        nowMs: Long = System.currentTimeMillis(),
    ): ReviewRequest {
        purge(nowMs)
        while (entries.size >= MAX_ENTRIES) {
            entries.remove(entries.entries.firstOrNull()?.key ?: break)
        }

        val normalizedAction = action.trim().lowercase()
        val payloadCopy = JSONObject(payload.toString())
        val review = ReviewRequest(
            proposalId = proposalId,
            action = normalizedAction,
            summary = summary(normalizedAction, payloadCopy),
            createdAtMs = nowMs,
            expiresAtMs = nowMs + TTL_MS,
        )
        entries[proposalId] = Entry(review, payloadCopy.toString())
        return review
    }

    @Synchronized
    fun listPending(nowMs: Long = System.currentTimeMillis()): List<ReviewRequest> {
        purge(nowMs)
        return entries.values.map { it.review }.sortedByDescending { it.createdAtMs }
    }

    @Synchronized
    fun get(
        proposalId: String,
        nowMs: Long = System.currentTimeMillis(),
    ): ReviewRequest? {
        purge(nowMs)
        return entries[proposalId]?.review
    }

    @Synchronized
    fun payloadFor(
        proposalId: String,
        expectedAction: String,
        nowMs: Long = System.currentTimeMillis(),
    ): JSONObject? {
        purge(nowMs)
        val entry = entries[proposalId] ?: return null
        if (entry.review.action != expectedAction.trim().lowercase()) return null
        return runCatching { JSONObject(entry.payloadJson) }.getOrNull()
    }

    @Synchronized
    fun remove(proposalId: String) {
        entries.remove(proposalId)
    }

    @Synchronized
    fun revokeAll() {
        entries.clear()
    }

    @Synchronized
    private fun purge(nowMs: Long) {
        entries.entries.removeAll { it.value.review.expiresAtMs <= nowMs }
    }

    private fun summary(action: String, payload: JSONObject): String = when (action) {
        "xspace_join" -> "X Space beitreten: " + payload.optString("spaceUrl").trim().take(320)
        "xspace_speak" -> "In X Space sprechen: “" + payload.optString("text").trim().take(500) + "”"
        "xspace_connect" -> "Lokale X-Space-Gateway-Verbindung herstellen"
        "xspace_leave" -> "Aktuellen X Space verlassen"
        "xspace_mute" -> "X-Space-Mikrofon stummschalten"
        "xspace_unmute" -> "X-Space-Mikrofon aktivieren"
        else -> "X-Space-Aktion bestätigen: $action"
    }
}

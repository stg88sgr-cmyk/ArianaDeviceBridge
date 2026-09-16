package de.snowworks.ariana.xspace

import org.json.JSONObject

/**
 * Binds an X Space confirmation proposal to the exact externally-visible
 * payload the user reviewed. Entries are volatile and one-time use.
 */
object XSpaceApprovalBindingStore {
    private const val TTL_MS = 2 * 60 * 1000L
    private const val MAX_ENTRIES = 12

    private data class Binding(
        val proposalId: String,
        val action: String,
        val fingerprint: String,
        val expiresAtMs: Long,
    )

    private val bindings = LinkedHashMap<String, Binding>()

    @Synchronized
    fun register(
        proposalId: String,
        action: String,
        payload: JSONObject,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        purge(nowMs)
        while (bindings.size >= MAX_ENTRIES) {
            bindings.remove(bindings.entries.firstOrNull()?.key ?: break)
        }
        val normalizedAction = action.trim().lowercase()
        bindings[proposalId] = Binding(
            proposalId = proposalId,
            action = normalizedAction,
            fingerprint = fingerprint(normalizedAction, payload),
            expiresAtMs = nowMs + TTL_MS,
        )
    }

    @Synchronized
    fun consume(
        proposalId: String,
        action: String,
        payload: JSONObject,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean {
        purge(nowMs)
        val binding = bindings.remove(proposalId) ?: return false
        val normalizedAction = action.trim().lowercase()
        return binding.action == normalizedAction &&
            binding.fingerprint == fingerprint(normalizedAction, payload)
    }

    @Synchronized
    fun revokeAll() {
        bindings.clear()
    }

    private fun purge(nowMs: Long) {
        bindings.entries.removeAll { it.value.expiresAtMs <= nowMs }
    }

    private fun fingerprint(action: String, payload: JSONObject): String =
        XSpacePayloadFingerprint.forValues(
            action = action,
            spaceUrl = payload.optString("spaceUrl"),
            text = payload.optString("text"),
            gatewayToken = payload.optString("gatewayToken"),
        )
}

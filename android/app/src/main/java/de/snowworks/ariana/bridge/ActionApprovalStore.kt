package de.snowworks.ariana.bridge

import java.util.UUID

/**
 * Volatile human-in-the-loop store for X-88 action proposals.
 *
 * Nothing in this store performs device work. CONFIRM proposals can be approved
 * or denied by local UI. Approval yields a short-lived one-time grant that a
 * local executor must consume before starting the matching device action.
 */
object ActionApprovalStore {
    const val PROPOSAL_TTL_MS = 2 * 60 * 1000L
    const val APPROVAL_TTL_MS = 60 * 1000L
    private const val MAX_PENDING = 12
    private const val MAX_APPROVED = 12

    data class PendingProposal(
        val id: String,
        val action: String,
        val reason: String,
        val createdAtMs: Long,
        val expiresAtMs: Long,
    )

    data class ApprovalGrant(
        val id: String,
        val proposalId: String,
        val action: String,
        val approvedAtMs: Long,
        val expiresAtMs: Long,
    )

    private val pending = LinkedHashMap<String, PendingProposal>()
    private val approved = LinkedHashMap<String, ApprovalGrant>()

    @Synchronized
    fun createPending(action: String, reason: String, nowMs: Long = System.currentTimeMillis()): PendingProposal {
        purgeExpired(nowMs)
        trimOldest(pending, MAX_PENDING - 1)
        val proposal = PendingProposal(
            id = UUID.randomUUID().toString(),
            action = action,
            reason = reason,
            createdAtMs = nowMs,
            expiresAtMs = nowMs + PROPOSAL_TTL_MS,
        )
        pending[proposal.id] = proposal
        return proposal
    }

    @Synchronized
    fun listPending(nowMs: Long = System.currentTimeMillis()): List<PendingProposal> {
        purgeExpired(nowMs)
        return pending.values.sortedByDescending { it.createdAtMs }
    }

    @Synchronized
    fun approve(proposalId: String, nowMs: Long = System.currentTimeMillis()): ApprovalGrant? {
        purgeExpired(nowMs)
        val proposal = pending.remove(proposalId) ?: return null
        trimOldest(approved, MAX_APPROVED - 1)
        val grant = ApprovalGrant(
            id = UUID.randomUUID().toString(),
            proposalId = proposal.id,
            action = proposal.action,
            approvedAtMs = nowMs,
            expiresAtMs = nowMs + APPROVAL_TTL_MS,
        )
        approved[grant.id] = grant
        return grant
    }

    @Synchronized
    fun deny(proposalId: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        purgeExpired(nowMs)
        return pending.remove(proposalId) != null
    }

    /**
     * Strong one-time consumption primitive. The executor must present the exact
     * grant returned by approve() and the expected action. A mismatched action
     * never consumes the grant, which prevents cross-action or stale-grant use.
     */
    @Synchronized
    fun consumeGrant(
        grantId: String,
        action: String,
        nowMs: Long = System.currentTimeMillis(),
    ): ApprovalGrant? {
        purgeExpired(nowMs)
        val grant = approved[grantId] ?: return null
        if (grant.action != action) return null
        approved.remove(grantId)
        return grant
    }

    /**
     * Compatibility helper for callers that only know an action name. Prefer
     * consumeGrant() for newly added executors so a visible approval is bound to
     * the exact grant produced by that user interaction.
     */
    @Synchronized
    fun consumeForAction(action: String, nowMs: Long = System.currentTimeMillis()): ApprovalGrant? {
        purgeExpired(nowMs)
        val entry = approved.entries.lastOrNull { it.value.action == action } ?: return null
        approved.remove(entry.key)
        return entry.value
    }

    @Synchronized
    fun pendingCount(nowMs: Long = System.currentTimeMillis()): Int {
        purgeExpired(nowMs)
        return pending.size
    }

    @Synchronized
    fun approvedCount(nowMs: Long = System.currentTimeMillis()): Int {
        purgeExpired(nowMs)
        return approved.size
    }

    @Synchronized
    fun revokeAll() {
        pending.clear()
        approved.clear()
    }

    @Synchronized
    private fun purgeExpired(nowMs: Long) {
        pending.entries.removeAll { it.value.expiresAtMs <= nowMs }
        approved.entries.removeAll { it.value.expiresAtMs <= nowMs }
    }

    private fun <T> trimOldest(map: LinkedHashMap<String, T>, keepAtMost: Int) {
        while (map.size > keepAtMost) {
            val oldestKey = map.entries.firstOrNull()?.key ?: return
            map.remove(oldestKey)
        }
    }
}

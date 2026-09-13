package de.snowworks.ariana.bridge

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ActionApprovalStoreTest {

    @Before
    fun setUp() {
        ActionApprovalStore.revokeAll()
    }

    @After
    fun tearDown() {
        ActionApprovalStore.revokeAll()
    }

    @Test
    fun exactGrantCanBeConsumedOnlyOnce() {
        val proposal = ActionApprovalStore.createPending(
            action = "open_settings",
            reason = "VISIBLE_USER_CONFIRMATION_REQUIRED",
            nowMs = 1_000L,
        )
        val grant = ActionApprovalStore.approve(proposal.id, nowMs = 2_000L)
        assertNotNull(grant)

        val consumed = ActionApprovalStore.consumeGrant(
            grantId = grant!!.id,
            action = "open_settings",
            nowMs = 3_000L,
        )
        assertNotNull(consumed)
        assertEquals(grant.id, consumed!!.id)
        assertEquals(0, ActionApprovalStore.approvedCount(nowMs = 3_000L))

        assertNull(
            ActionApprovalStore.consumeGrant(
                grantId = grant.id,
                action = "open_settings",
                nowMs = 3_001L,
            ),
        )
    }

    @Test
    fun wrongActionDoesNotConsumeExactGrant() {
        val proposal = ActionApprovalStore.createPending(
            action = "open_settings",
            reason = "VISIBLE_USER_CONFIRMATION_REQUIRED",
            nowMs = 10_000L,
        )
        val grant = ActionApprovalStore.approve(proposal.id, nowMs = 11_000L)!!

        assertNull(
            ActionApprovalStore.consumeGrant(
                grantId = grant.id,
                action = "camera_start",
                nowMs = 12_000L,
            ),
        )
        assertEquals(1, ActionApprovalStore.approvedCount(nowMs = 12_000L))

        assertNotNull(
            ActionApprovalStore.consumeGrant(
                grantId = grant.id,
                action = "open_settings",
                nowMs = 12_001L,
            ),
        )
    }

    @Test
    fun expiredProposalCannotBeApproved() {
        val createdAt = 20_000L
        val proposal = ActionApprovalStore.createPending(
            action = "open_settings",
            reason = "VISIBLE_USER_CONFIRMATION_REQUIRED",
            nowMs = createdAt,
        )

        val expiredAt = createdAt + ActionApprovalStore.PROPOSAL_TTL_MS
        assertNull(ActionApprovalStore.approve(proposal.id, nowMs = expiredAt))
        assertEquals(0, ActionApprovalStore.pendingCount(nowMs = expiredAt))
    }

    @Test
    fun expiredGrantCannotBeConsumed() {
        val proposal = ActionApprovalStore.createPending(
            action = "open_settings",
            reason = "VISIBLE_USER_CONFIRMATION_REQUIRED",
            nowMs = 30_000L,
        )
        val approvedAt = 31_000L
        val grant = ActionApprovalStore.approve(proposal.id, nowMs = approvedAt)!!

        val expiredAt = approvedAt + ActionApprovalStore.APPROVAL_TTL_MS
        assertNull(
            ActionApprovalStore.consumeGrant(
                grantId = grant.id,
                action = "open_settings",
                nowMs = expiredAt,
            ),
        )
        assertEquals(0, ActionApprovalStore.approvedCount(nowMs = expiredAt))
    }

    @Test
    fun denyAndRevokeRemoveAuthorizations() {
        val first = ActionApprovalStore.createPending("open_settings", "confirm", nowMs = 40_000L)
        val second = ActionApprovalStore.createPending("camera_start", "confirm", nowMs = 40_001L)

        assertTrue(ActionApprovalStore.deny(first.id, nowMs = 40_002L))
        assertFalse(ActionApprovalStore.deny(first.id, nowMs = 40_003L))

        val grant = ActionApprovalStore.approve(second.id, nowMs = 40_004L)
        assertNotNull(grant)
        assertEquals(1, ActionApprovalStore.approvedCount(nowMs = 40_004L))

        ActionApprovalStore.revokeAll()
        assertEquals(0, ActionApprovalStore.pendingCount(nowMs = 40_005L))
        assertEquals(0, ActionApprovalStore.approvedCount(nowMs = 40_005L))
    }
}

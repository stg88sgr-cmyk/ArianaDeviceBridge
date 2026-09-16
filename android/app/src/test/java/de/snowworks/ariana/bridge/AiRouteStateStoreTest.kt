package de.snowworks.ariana.bridge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiRouteStateStoreTest {
    @Test
    fun firstStateIsRecorded() {
        val previous = AiRouteStateStore.State("LOCAL", "GENERAL", false, 0L)
        val next = AiRouteStateStore.State("LOCAL", "GENERAL", false, 1L)
        assertTrue(AiRouteStateStore.shouldRecordTransition(previous, next))
    }

    @Test
    fun identicalStateIsNotDuplicated() {
        val previous = AiRouteStateStore.State("CLAUDE", "CODE_ARCHITECTURE", false, 1L)
        val next = AiRouteStateStore.State("CLAUDE", "CODE_ARCHITECTURE", false, 2L)
        assertFalse(AiRouteStateStore.shouldRecordTransition(previous, next))
    }

    @Test
    fun engineTaskOrFallbackChangeIsRecorded() {
        val base = AiRouteStateStore.State("LOCAL", "GENERAL", false, 1L)
        assertTrue(AiRouteStateStore.shouldRecordTransition(base, base.copy(engine = "META", updatedAtMs = 2L)))
        assertTrue(AiRouteStateStore.shouldRecordTransition(base, base.copy(taskClass = "SECOND_OPINION", updatedAtMs = 2L)))
        assertTrue(AiRouteStateStore.shouldRecordTransition(base, base.copy(fallbackUsed = true, updatedAtMs = 2L)))
    }

    @Test
    fun boundedHistoryKeepsRoomForNewestEntry() {
        assertEquals(0, AiRouteStateStore.historyStartIndex(0))
        assertEquals(0, AiRouteStateStore.historyStartIndex(AiRouteStateStore.MAX_HISTORY - 1))
        assertEquals(1, AiRouteStateStore.historyStartIndex(AiRouteStateStore.MAX_HISTORY))
        assertEquals(9, AiRouteStateStore.historyStartIndex(20))
    }
}

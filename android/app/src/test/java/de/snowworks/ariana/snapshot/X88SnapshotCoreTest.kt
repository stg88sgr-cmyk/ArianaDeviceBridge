package de.snowworks.ariana.snapshot

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class X88SnapshotCoreTest {
    @Test
    fun createsAndValidatesSnapshot() {
        val snapshot = X88SnapshotCore.create("boot", 40, """{"health":"healthy"}""")
        assertTrue(X88SnapshotCore.validate(snapshot))
        assertEquals(40, snapshot.stage)
        assertEquals(1, snapshot.schemaVersion)
    }

    @Test
    fun rejectsTamperedPayload() {
        val snapshot = X88SnapshotCore.create("boot", 40, """{"health":"healthy"}""")
        val tampered = snapshot.copy(payload = """{"health":"unavailable"}""")
        assertFalse(X88SnapshotCore.validate(tampered))
    }

    @Test
    fun storeReplacesSnapshotById() {
        val store = InMemoryX88SnapshotStore()
        store.save(X88SnapshotCore.create("boot", 40, "one"))
        store.save(X88SnapshotCore.create("boot", 40, "two"))
        assertEquals("two", store.load("boot")?.payload)
        assertEquals(1, store.all().size)
    }
}

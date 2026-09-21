package de.snowworks.ariana.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class X88MemoryRepositoryTest {

    @Test
    fun importPersistsAndRecallReturnsMemoryAfterRepositoryRecreation() {
        val store = FakeX88MemoryStore()
        val first = X88MemoryRepository(DefaultX88MemoryBridge(), store)

        val imported = first.import(
            ImportedMemory(
                content = "  X88   remembers this.  ",
                source = X88MemoryItem.Source.ARIANA,
                capturedAtEpochMs = 100L,
                project = "Ariana/X88"
            )
        )

        assertNotNull(imported)

        val recreated = X88MemoryRepository(DefaultX88MemoryBridge(), store)
        val recalled = recreated.recall()

        assertEquals(1, recalled.size)
        assertEquals("X88 remembers this.", recalled.single().content)
        assertEquals("Ariana/X88", recalled.single().project)
    }

    private class FakeX88MemoryStore : X88MemoryStore {
        private var items: List<X88MemoryItem> = emptyList()

        override fun load(): List<X88MemoryItem> = items

        override fun save(items: List<X88MemoryItem>) {
            this.items = items
        }

        override fun clear() {
            items = emptyList()
        }
    }
}

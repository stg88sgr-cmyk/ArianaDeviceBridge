package de.snowworks.ariana.memory

import org.junit.Assert.assertEquals
import org.junit.Test

class X88MemoryRepositoryClearTest {

    @Test
    fun clearRemovesPersistedMemories() {
        val store = FakeX88MemoryStore()
        val repository = X88MemoryRepository(DefaultX88MemoryBridge(), store)

        repository.import(
            ImportedMemory(
                content = "temporary memory",
                source = X88MemoryItem.Source.LOCAL,
                capturedAtEpochMs = 1L
            )
        )

        repository.clear()

        assertEquals(emptyList<X88MemoryItem>(), repository.recall())
    }

    private class FakeX88MemoryStore : X88MemoryStore {
        private var items: List<X88MemoryItem> = emptyList()

        override fun load(): List<X88MemoryItem> = items
        override fun save(items: List<X88MemoryItem>) { this.items = items }
        override fun clear() { items = emptyList() }
    }
}

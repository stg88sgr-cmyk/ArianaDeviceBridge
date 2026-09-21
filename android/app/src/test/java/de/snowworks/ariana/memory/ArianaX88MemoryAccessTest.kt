package de.snowworks.ariana.memory

import org.junit.Assert.assertEquals
import org.junit.Test

class ArianaX88MemoryAccessTest {

    @Test
    fun saveAndRecallUsesTheSharedMemoryRepository() {
        val store = FakeX88MemoryStore()
        val access = ArianaX88MemoryAccess(X88MemoryRepository(DefaultX88MemoryBridge(), store))

        val saved = access.remember(
            ImportedMemory(
                content = "Ariana/X88 persistent memory",
                source = X88MemoryItem.Source.ARIANA,
                capturedAtEpochMs = 42L,
                project = "Ariana/X88"
            )
        )

        assertEquals("Ariana/X88 persistent memory", saved?.content)
        assertEquals(listOf(saved), access.recall())
    }

    @Test
    fun recallCanUseProjectTopicAndBucketFilters() {
        val store = FakeX88MemoryStore()
        val access = ArianaX88MemoryAccess(X88MemoryRepository(DefaultX88MemoryBridge(), store))

        access.remember(
            ImportedMemory(
                content = "Ariana design memory",
                source = X88MemoryItem.Source.ARIANA,
                capturedAtEpochMs = 100L,
                project = "Ariana/X88",
                topic = "design",
                bucket = X88MemoryItem.Bucket.PERMANENT
            )
        )
        access.remember(
            ImportedMemory(
                content = "Other project",
                source = X88MemoryItem.Source.LOCAL,
                capturedAtEpochMs = 101L,
                project = "Snowworks",
                topic = "design"
            )
        )

        val result = access.recall(
            project = "Ariana/X88",
            topic = "design",
            bucket = X88MemoryItem.Bucket.PERMANENT
        )

        assertEquals(listOf("Ariana design memory"), result.map { it.content })
    }

    private class FakeX88MemoryStore : X88MemoryStore {
        private var items: List<X88MemoryItem> = emptyList()
        override fun load(): List<X88MemoryItem> = items
        override fun save(items: List<X88MemoryItem>) { this.items = items }
        override fun clear() { items = emptyList() }
    }
}

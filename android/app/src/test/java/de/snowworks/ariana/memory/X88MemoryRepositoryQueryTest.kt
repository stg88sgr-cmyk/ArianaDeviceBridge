package de.snowworks.ariana.memory

import org.junit.Assert.assertEquals
import org.junit.Test

class X88MemoryRepositoryQueryTest {

    @Test
    fun recallCanFilterByProjectTopicAndBucket() {
        val store = FakeX88MemoryStore()
        val repository = X88MemoryRepository(DefaultX88MemoryBridge(), store)

        repository.import(memory("A", "Ariana/X88", "memory", X88MemoryItem.Bucket.IMPORTANT))
        repository.import(memory("B", "Snowworks", "design", X88MemoryItem.Bucket.TODAY))
        repository.import(memory("C", "Ariana/X88", "design", X88MemoryItem.Bucket.PERMANENT))

        val result = repository.recall(
            project = "Ariana/X88",
            topic = "design",
            bucket = X88MemoryItem.Bucket.PERMANENT
        )

        assertEquals(listOf("C"), result.map { it.content })
    }

    @Test
    fun omittedFiltersReturnAllMemories() {
        val store = FakeX88MemoryStore()
        val repository = X88MemoryRepository(DefaultX88MemoryBridge(), store)

        repository.import(memory("A", "Ariana/X88", "memory", X88MemoryItem.Bucket.IMPORTANT))
        repository.import(memory("B", "Snowworks", "design", X88MemoryItem.Bucket.TODAY))

        assertEquals(2, repository.recall().size)
    }

    private fun memory(
        content: String,
        project: String,
        topic: String,
        bucket: X88MemoryItem.Bucket
    ) = ImportedMemory(
        content = content,
        source = X88MemoryItem.Source.ARIANA,
        capturedAtEpochMs = content.first().code.toLong(),
        project = project,
        topic = topic,
        bucket = bucket
    )

    private class FakeX88MemoryStore : X88MemoryStore {
        private var items: List<X88MemoryItem> = emptyList()
        override fun load(): List<X88MemoryItem> = items
        override fun save(items: List<X88MemoryItem>) { this.items = items }
        override fun clear() { items = emptyList() }
    }
}

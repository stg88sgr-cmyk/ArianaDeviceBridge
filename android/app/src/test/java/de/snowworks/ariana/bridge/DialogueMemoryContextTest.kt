package de.snowworks.ariana.bridge

import de.snowworks.ariana.memory.ArianaX88MemoryAccess
import de.snowworks.ariana.memory.DefaultX88MemoryBridge
import de.snowworks.ariana.memory.ImportedMemory
import de.snowworks.ariana.memory.X88MemoryItem
import de.snowworks.ariana.memory.X88MemoryRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DialogueMemoryContextTest {

    @Test
    fun contextContainsOnlyRequestedProjectAndTopic() {
        val store = FakeStore()
        val access = ArianaX88MemoryAccess(X88MemoryRepository(DefaultX88MemoryBridge(), store))

        access.remember(memory("A", "Ariana/X88", "design"))
        access.remember(memory("B", "Snowworks", "design"))

        val context = DialogueMemoryContext.from(access, project = "Ariana/X88", topic = "design")

        assertEquals("- A", context)
    }

    @Test
    fun contextIsBounded() {
        val store = FakeStore()
        val access = ArianaX88MemoryAccess(X88MemoryRepository(DefaultX88MemoryBridge(), store))
        access.remember(memory("x".repeat(3000), "Ariana/X88", "test"))

        val context = DialogueMemoryContext.from(access, project = "Ariana/X88")

        assertTrue(context.length <= DialogueMemoryContext.MAX_CHARS)
    }

    @Test
    fun contextPrioritizesPermanentThenImportantThenToday() {
        val store = FakeStore()
        val access = ArianaX88MemoryAccess(X88MemoryRepository(DefaultX88MemoryBridge(), store))

        access.remember(memory("today", "Ariana/X88", "priority", X88MemoryItem.Bucket.TODAY))
        access.remember(memory("important", "Ariana/X88", "priority", X88MemoryItem.Bucket.IMPORTANT))
        access.remember(memory("permanent", "Ariana/X88", "priority", X88MemoryItem.Bucket.PERMANENT))

        val context = DialogueMemoryContext.from(access, project = "Ariana/X88", topic = "priority")

        assertEquals("- permanent\n- important\n- today", context)
    }

    private fun memory(
        content: String,
        project: String,
        topic: String,
        bucket: X88MemoryItem.Bucket = X88MemoryItem.Bucket.TODAY,
    ) = ImportedMemory(
        content = content,
        source = X88MemoryItem.Source.ARIANA,
        capturedAtEpochMs = content.hashCode().toLong(),
        project = project,
        topic = topic,
        bucket = bucket,
    )

    private class FakeStore : de.snowworks.ariana.memory.X88MemoryStore {
        private var items = emptyList<X88MemoryItem>()
        override fun load() = items
        override fun save(items: List<X88MemoryItem>) { this.items = items }
        override fun clear() { items = emptyList() }
    }
}

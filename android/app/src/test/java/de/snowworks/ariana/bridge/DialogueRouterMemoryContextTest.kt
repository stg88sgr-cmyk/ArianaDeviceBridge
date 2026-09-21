package de.snowworks.ariana.bridge

import de.snowworks.ariana.memory.ArianaX88MemoryAccess
import de.snowworks.ariana.memory.DefaultX88MemoryBridge
import de.snowworks.ariana.memory.ImportedMemory
import de.snowworks.ariana.memory.X88MemoryItem
import de.snowworks.ariana.memory.X88MemoryRepository
import de.snowworks.ariana.memory.X88MemoryStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DialogueRouterMemoryContextTest {

    @Test
    fun memoryContextCanBeBuiltFromRelevantArianaMemories() {
        val store = FakeStore()
        val access = ArianaX88MemoryAccess(X88MemoryRepository(DefaultX88MemoryBridge(), store))

        access.remember(
            ImportedMemory(
                content = "Use provider-neutral X88 memory.",
                source = X88MemoryItem.Source.ARIANA,
                capturedAtEpochMs = 1L,
                project = "Ariana/X88",
                topic = "architecture",
                bucket = X88MemoryItem.Bucket.PERMANENT
            )
        )

        val context = DialogueMemoryContext.from(
            access,
            project = "Ariana/X88",
            topic = "architecture"
        )

        assertTrue(context.contains("Use provider-neutral X88 memory."))
    }

    private class FakeStore : X88MemoryStore {
        private var items = emptyList<X88MemoryItem>()
        override fun load() = items
        override fun save(items: List<X88MemoryItem>) { this.items = items }
        override fun clear() { items = emptyList() }
    }
}

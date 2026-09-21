package de.snowworks.ariana.bridge

import de.snowworks.ariana.memory.ArianaX88MemoryAccess
import de.snowworks.ariana.memory.DefaultX88MemoryBridge
import de.snowworks.ariana.memory.ImportedMemory
import de.snowworks.ariana.memory.X88MemoryItem
import de.snowworks.ariana.memory.X88MemoryRepository
import de.snowworks.ariana.memory.X88MemoryStore
import org.junit.Assert.assertTrue
import org.junit.Test

class DialogueRouterMemoryTest {

    @Test
    fun generateWithMemoryAddsOnlyMatchingProjectAndTopicContext() {
        val store = FakeStore()
        val access = ArianaX88MemoryAccess(X88MemoryRepository(DefaultX88MemoryBridge(), store))
        access.remember(ImportedMemory("Keep this X88 rule", X88MemoryItem.Source.ARIANA, 1L, "Ariana/X88", "rules", X88MemoryItem.Bucket.PERMANENT))
        access.remember(ImportedMemory("Do not include this", X88MemoryItem.Source.LOCAL, 2L, "Snowworks", "rules"))

        var received = ""
        DialogueRouter.register("test-memory", 5_000L) { received = it; "ok" }

        val outcome = DialogueRouter.generateWithMemory("What is the rule?", access, "Ariana/X88", "rules")

        assertTrue(outcome.ok)
        assertTrue(received.contains("Keep this X88 rule"))
        assertTrue(!received.contains("Do not include this"))
        DialogueRouter.unregister()
    }

    private class FakeStore : X88MemoryStore {
        private var items = emptyList<X88MemoryItem>()
        override fun load() = items
        override fun save(items: List<X88MemoryItem>) { this.items = items }
        override fun clear() { items = emptyList() }
    }
}

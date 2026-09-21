package de.snowworks.ariana.bridge

import de.snowworks.ariana.memory.ArianaX88MemoryAccess
import de.snowworks.ariana.memory.DefaultX88MemoryBridge
import de.snowworks.ariana.memory.ImportedMemory
import de.snowworks.ariana.memory.X88MemoryItem
import de.snowworks.ariana.memory.X88MemoryRepository
import de.snowworks.ariana.memory.X88MemoryStore
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test

class DialogueRouterMemoryIntegrationTest {

    @After
    fun tearDown() {
        DialogueRouter.unregister()
    }

    @Test
    fun localDialogueReceivesRelevantX88MemoryContext() {
        var received = ""
        assertTrue(DialogueRouter.register("test-local") { text ->
            received = text
            "ok"
        })

        val store = FakeStore()
        val access = ArianaX88MemoryAccess(X88MemoryRepository(DefaultX88MemoryBridge(), store))
        access.remember(
            ImportedMemory(
                content = "X88 uses provider-neutral memory.",
                source = X88MemoryItem.Source.ARIANA,
                capturedAtEpochMs = 1L,
                project = "Ariana/X88",
                topic = "architecture",
                bucket = X88MemoryItem.Bucket.PERMANENT
            )
        )

        val outcome = DialogueRouter.generateWithMemory(
            rawText = "Wie ist die Architektur?",
            memoryAccess = access,
            project = "Ariana/X88",
            topic = "architecture"
        )

        assertTrue(outcome.ok)
        assertTrue(received.contains("X88 uses provider-neutral memory."))
        assertTrue(received.contains("Wie ist die Architektur?"))
    }

    private class FakeStore : X88MemoryStore {
        private var items = emptyList<X88MemoryItem>()
        override fun load() = items
        override fun save(items: List<X88MemoryItem>) { this.items = items }
        override fun clear() { items = emptyList() }
    }
}

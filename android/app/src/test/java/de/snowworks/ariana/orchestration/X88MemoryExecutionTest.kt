package de.snowworks.ariana.orchestration

import de.snowworks.ariana.memory.ImportedMemory
import de.snowworks.ariana.memory.X88MemoryItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class X88MemoryExecutionTest {
    @Test
    fun executionImportsThroughPreferredMemoryBackend() {
        val bridge = RecordingMemoryBridge()
        val catalog = X88MemoryBackendCatalog(
            listOf(X88MemoryCapabilityAdapter("memory-local", bridge))
        )
        val executor = X88MemoryExecutor(catalog)

        val result = executor.import(
            ImportedMemory(
                content = "remember X88",
                source = X88MemoryItem.Source.ARIANA,
                capturedAtEpochMs = 42L,
            )
        )

        assertNotNull(result)
        assertEquals("remember X88", bridge.importedContent)
    }

    @Test
    fun executionReturnsNullWhenNoMemoryBackendIsAvailable() {
        val executor = X88MemoryExecutor(X88MemoryBackendCatalog())

        val result = executor.import(
            ImportedMemory(
                content = "remember X88",
                source = X88MemoryItem.Source.ARIANA,
                capturedAtEpochMs = 42L,
            )
        )

        assertNull(result)
    }

    private class RecordingMemoryBridge : de.snowworks.ariana.memory.X88MemoryBridge {
        var importedContent: String? = null

        override fun import(item: ImportedMemory): X88MemoryItem? {
            importedContent = item.content
            return X88MemoryItem(
                id = "test-id",
                content = item.content,
                source = item.source,
                capturedAtEpochMs = item.capturedAtEpochMs,
                contentHash = "test-hash",
            )
        }

        override fun normalize(item: ImportedMemory) = item
        override fun deduplicate(item: X88MemoryItem) = true
    }
}

package de.snowworks.ariana.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class X88MemoryBridgeTest {
    @Test
    fun importNormalizesContentAndCreatesStableMetadata() {
        val bridge = DefaultX88MemoryBridge()

        val item = bridge.import(
            ImportedMemory(
                content = "  Ariana   X88\n Memory  ",
                source = X88MemoryItem.Source.META_AI,
                capturedAtEpochMs = 1234L,
                project = "X88",
                topic = "memory",
                bucket = X88MemoryItem.Bucket.IMPORTANT,
            ),
        )

        assertNotNull(item)
        assertEquals("Ariana X88 Memory", item?.content)
        assertEquals("X88", item?.project)
        assertEquals(X88MemoryItem.Bucket.IMPORTANT, item?.bucket)
        assertEquals(64, item?.contentHash?.length)
    }

    @Test
    fun repeatedContentIsRejectedByDeduplication() {
        val bridge = DefaultX88MemoryBridge()
        val input = ImportedMemory(
            content = "same memory",
            source = X88MemoryItem.Source.LOCAL,
            capturedAtEpochMs = 1000L,
        )

        assertNotNull(bridge.import(input))
        assertNull(bridge.import(input.copy(capturedAtEpochMs = 2000L)))
    }
}

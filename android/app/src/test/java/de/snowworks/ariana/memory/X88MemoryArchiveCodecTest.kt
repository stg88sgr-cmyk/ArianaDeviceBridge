package de.snowworks.ariana.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class X88MemoryArchiveCodecTest {

    @Test
    fun roundTripPreservesMemoryMetadata() {
        val item = X88MemoryItem(
            id = "id-1",
            content = "Ariana remembers: X88 läuft.",
            source = X88MemoryItem.Source.ARIANA,
            capturedAtEpochMs = 123L,
            project = "Ariana/X88",
            topic = "memory",
            bucket = X88MemoryItem.Bucket.IMPORTANT,
            contentType = X88MemoryItem.ContentType.TEXT,
            sourceReference = "session|42",
            contentHash = "abc123"
        )

        val encoded = X88MemoryArchiveCodec.encode(listOf(item))
        val decoded = X88MemoryArchiveCodec.decode(encoded)

        assertEquals(listOf(item), decoded)
    }

    @Test
    fun emptyAndMalformedRecordsAreIgnored() {
        val decoded = X88MemoryArchiveCodec.decode("\nnot-a-valid-record\n")

        assertTrue(decoded.isEmpty())
    }
}

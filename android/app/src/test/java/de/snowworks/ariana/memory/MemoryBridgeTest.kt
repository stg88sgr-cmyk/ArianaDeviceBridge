package de.snowworks.ariana.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryBridgeTest {
    @Test
    fun importNormalizesProviderDataWithoutKnowingProviderDetails() {
        val bridge = MemoryBridge(
            object : MemorySourceAdapter {
                override val sourceId = "example-provider"
                override fun importItems() = listOf(
                    MemoryItem(
                        id = "m1",
                        source = " example-provider ",
                        content = "  hello X88  ",
                        createdAtEpochMs = 1234L,
                        metadata = mapOf(" topic " to "test"),
                    ),
                )
            },
        )

        val item = bridge.import().single()

        assertEquals("example-provider", item.source)
        assertEquals("hello X88", item.content)
        assertEquals(1234L, item.createdAtEpochMs)
        assertTrue(item.metadata.containsKey(" topic "))
    }

    @Test
    fun importDropsDuplicateIdsAtTheCoreBoundary() {
        val bridge = MemoryBridge(
            object : MemorySourceAdapter {
                override val sourceId = "provider"
                override fun importItems() = listOf(
                    MemoryItem("same", "provider", "one", null),
                    MemoryItem("same", "provider", "two", null),
                    MemoryItem("other", "provider", "three", null),
                )
            },
        )

        val items = bridge.import()

        assertEquals(listOf("same", "other"), items.map { it.id })
    }
}

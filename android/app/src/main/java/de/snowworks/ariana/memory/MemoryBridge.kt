package de.snowworks.ariana.memory

/**
 * Neutral memory boundary. Provider-specific integrations implement MemorySourceAdapter
 * and the X88 core consumes only MemoryItem values.
 *
 * The bridge deliberately has no dependency on Meta AI, credentials, Android permissions,
 * or network transport.
 */
data class MemoryItem(
    val id: String,
    val source: String,
    val content: String,
    val createdAtEpochMs: Long?,
    val metadata: Map<String, String> = emptyMap(),
)

interface MemorySourceAdapter {
    val sourceId: String
    fun importItems(): List<MemoryItem>
}

class MemoryBridge(
    private val adapter: MemorySourceAdapter,
) {
    fun import(): List<MemoryItem> =
        adapter.importItems()
            .map(::normalize)
            .distinctBy { it.id }

    private fun normalize(item: MemoryItem): MemoryItem =
        item.copy(
            source = item.source.trim(),
            content = item.content.trim(),
            metadata = item.metadata.mapKeys { it.key.trim() },
        )
}

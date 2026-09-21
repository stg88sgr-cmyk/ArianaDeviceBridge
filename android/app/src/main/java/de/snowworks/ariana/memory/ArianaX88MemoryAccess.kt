package de.snowworks.ariana.memory

/**
 * Public Ariana/X88 seam for durable memory access.
 *
 * The application layer depends on this facade instead of knowing which
 * storage provider is used. The repository remains provider-neutral.
 */
class ArianaX88MemoryAccess(
    private val repository: X88MemoryRepository,
) {
    fun remember(item: ImportedMemory): X88MemoryItem? =
        repository.import(item)

    fun recall(
        project: String? = null,
        topic: String? = null,
        bucket: X88MemoryItem.Bucket? = null,
    ): List<X88MemoryItem> =
        repository.recall(project, topic, bucket)

    fun clear(): Unit =
        repository.clear()
}

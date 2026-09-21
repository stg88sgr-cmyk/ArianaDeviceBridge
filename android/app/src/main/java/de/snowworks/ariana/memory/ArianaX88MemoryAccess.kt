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
        repository.import(item.copy(source = item.source))

    fun recall(): List<X88MemoryItem> =
        repository.recall()

    fun clear(): Unit =
        repository.clear()
}

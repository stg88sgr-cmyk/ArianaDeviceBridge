package de.snowworks.ariana.orchestration

import de.snowworks.ariana.memory.ImportedMemory
import de.snowworks.ariana.memory.X88MemoryItem

/**
 * Executes LOCAL_MEMORY through the catalog's preferred backend.
 *
 * A missing backend is represented by null and does not fall back to another
 * capability or external provider.
 */
class X88MemoryExecutor(
    private val catalog: X88MemoryBackendCatalog,
) {
    fun import(item: ImportedMemory): X88MemoryItem? =
        catalog.preferred()?.adapter?.import(item)
}

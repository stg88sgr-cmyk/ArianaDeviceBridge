package de.snowworks.ariana.orchestration

import de.snowworks.ariana.memory.ImportedMemory
import de.snowworks.ariana.memory.X88MemoryBridge
import de.snowworks.ariana.memory.X88MemoryItem

/**
 * Executable adapter for the LOCAL_MEMORY capability.
 *
 * The adapter delegates all memory semantics to X88MemoryBridge. It does not
 * own persistence, provider selection, network access, or Android permissions.
 */
class X88MemoryCapabilityAdapter(
    val id: String,
    private val bridge: X88MemoryBridge,
) {
    init {
        require(id.isNotBlank())
    }

    fun import(item: ImportedMemory): X88MemoryItem? =
        bridge.import(item)

    fun normalize(item: ImportedMemory): ImportedMemory =
        bridge.normalize(item)

    fun deduplicate(item: X88MemoryItem): Boolean =
        bridge.deduplicate(item)
}

data class X88MemoryBackend(
    val descriptor: X88Backend,
    val adapter: X88MemoryCapabilityAdapter,
)

class X88MemoryBackendCatalog(
    adapters: Collection<X88MemoryCapabilityAdapter> = emptyList(),
) {
    private val entries: Map<String, X88MemoryCapabilityAdapter> =
        adapters.associateBy { it.id }

    fun register(adapter: X88MemoryCapabilityAdapter): X88MemoryBackendCatalog =
        X88MemoryBackendCatalog(entries.values + adapter)

    fun unregister(id: String): X88MemoryBackendCatalog =
        X88MemoryBackendCatalog(entries.values.filterNot { it.id == id })

    fun backends(): List<X88MemoryBackend> =
        entries.values.map {
            X88MemoryBackend(
                descriptor = X88Backend(
                    id = it.id,
                    capability = X88Capability.LOCAL_MEMORY,
                    local = true,
                ),
                adapter = it,
            )
        }

    fun preferred(): X88MemoryBackend? =
        backends().firstOrNull()
}

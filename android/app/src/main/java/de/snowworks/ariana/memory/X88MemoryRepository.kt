package de.snowworks.ariana.memory

interface X88MemoryStore {
    fun load(): List<X88MemoryItem>
    fun save(items: List<X88MemoryItem>)
    fun clear()
}

class X88MemoryRepository(
    private val bridge: X88MemoryBridge,
    private val store: X88MemoryStore,
) {
    fun import(item: ImportedMemory): X88MemoryItem? {
        val normalized = bridge.normalize(item)
        val hash = contentHash(normalized.content)
        val existing = store.load()
        if (existing.any { it.contentHash == hash }) return null

        val memory = bridge.import(normalized) ?: return null
        store.save(existing + memory)
        return memory
    }

    fun recall(): List<X88MemoryItem> = store.load()

    fun clear() = store.clear()

    private fun contentHash(value: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}

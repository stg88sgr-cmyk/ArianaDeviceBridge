package de.snowworks.ariana.memory

import java.security.MessageDigest

interface X88MemoryBridge {
    fun import(item: ImportedMemory): X88MemoryItem?
    fun normalize(item: ImportedMemory): ImportedMemory
    fun deduplicate(item: X88MemoryItem): Boolean
}

data class ImportedMemory(
    val content: String,
    val source: X88MemoryItem.Source,
    val capturedAtEpochMs: Long,
    val project: String? = null,
    val topic: String? = null,
    val bucket: X88MemoryItem.Bucket = X88MemoryItem.Bucket.TODAY,
    val contentType: X88MemoryItem.ContentType = X88MemoryItem.ContentType.TEXT,
    val sourceReference: String? = null
)

class DefaultX88MemoryBridge : X88MemoryBridge {
    private val knownHashes = mutableSetOf<String>()

    override fun import(item: ImportedMemory): X88MemoryItem? {
        val normalized = normalize(item)
        val memory = X88MemoryItem(
            id = stableId(normalized),
            content = normalized.content,
            source = normalized.source,
            capturedAtEpochMs = normalized.capturedAtEpochMs,
            project = normalized.project,
            topic = normalized.topic,
            bucket = normalized.bucket,
            contentType = normalized.contentType,
            sourceReference = normalized.sourceReference,
            contentHash = hash(normalized.content)
        )
        return if (deduplicate(memory)) memory else null
    }

    override fun normalize(item: ImportedMemory): ImportedMemory =
        item.copy(content = item.content.trim().replace(Regex("\\s+"), " "))

    override fun deduplicate(item: X88MemoryItem): Boolean =
        knownHashes.add(item.contentHash)

    private fun stableId(item: ImportedMemory): String =
        hash(item.source.toString() + "|" + item.capturedAtEpochMs + "|" + item.content)

    private fun hash(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}

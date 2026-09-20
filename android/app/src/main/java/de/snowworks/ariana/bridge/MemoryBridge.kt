package de.snowworks.ariana.bridge

/**
 * Step 7: provider-neutral memory boundary.
 *
 * External assistants/providers are treated only as import sources. The X88
 * core consumes normalized MemoryItems and never needs to know the source.
 */
object MemoryBridge {
    data class MemoryItem(
        val id: String,
        val content: String,
        val source: String = "x88-memory",
        val timestamp: String? = null,
        val confidence: Double? = null,
        val originalRole: String? = null,
        val metadata: Map<String, String> = emptyMap(),
    )

    data class ImportRecord(
        val source: String,
        val importedAt: String? = null,
        val content: String,
        val id: String? = null,
        val timestamp: String? = null,
        val confidence: Double? = null,
        val originalRole: String? = null,
        val metadata: Map<String, String> = emptyMap(),
    )

    fun import(records: Iterable<ImportRecord>): List<MemoryItem> {
        val normalized = records.mapNotNull(::normalize).filter(::isUsable)
        return normalized
            .groupBy { dedupeKey(it) }
            .values
            .map { group ->
                group.maxWithOrNull(
                    compareBy<MemoryItem> { it.confidence ?: 0.0 }
                        .thenBy { it.timestamp ?: "" }
                )!!
            }
            .sortedByDescending { it.confidence ?: 0.0 }
    }

    fun normalize(record: ImportRecord): MemoryItem? {
        val cleanContent = record.content.trim()
        if (cleanContent.length <= 3) return null

        val cleanSource = record.source.trim().ifBlank { "x88-memory" }
        val cleanId = record.id?.trim()?.takeIf { it.isNotEmpty() }
            ?: stableId(cleanSource, cleanContent)

        return MemoryItem(
            id = cleanId,
            content = cleanContent,
            source = cleanSource,
            timestamp = record.timestamp ?: record.importedAt,
            confidence = record.confidence?.coerceIn(0.0, 1.0),
            originalRole = record.originalRole?.trim()?.takeIf { it.isNotEmpty() },
            metadata = record.metadata.toMap(),
        )
    }

    fun isUsable(memory: MemoryItem): Boolean =
        memory.content.trim().length > 3 &&
            (memory.confidence == null || memory.confidence > 0.28)

    private fun dedupeKey(memory: MemoryItem): String =
        memory.content.trim().lowercase().replace(Regex("\\s+"), " ")

    private fun stableId(source: String, content: String): String {
        val bytes = "$source\u0000${content.trim()}".toByteArray(Charsets.UTF_8)
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }.take(24)
    }
}

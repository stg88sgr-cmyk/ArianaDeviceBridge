package de.snowworks.ariana.bridge

import de.snowworks.ariana.memory.ArianaX88MemoryAccess
import de.snowworks.ariana.memory.X88MemoryItem

/**
 * Builds a bounded, provider-neutral context from durable X88 memories.
 */
object DialogueMemoryContext {
    const val MAX_CHARS = 2400

    fun auto(access: ArianaX88MemoryAccess, query: String): List<X88MemoryItem> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) return emptyList()
        val all = access.recall()
        val project = all.asSequence()
            .mapNotNull { it.project }
            .distinct()
            .sortedByDescending { it.length }
            .firstOrNull { normalizedQuery.contains(it, ignoreCase = true) }
        val topic = all.asSequence()
            .mapNotNull { it.topic }
            .distinct()
            .sortedByDescending { it.length }
            .firstOrNull { normalizedQuery.contains(it, ignoreCase = true) }
        return access.recall(project = project, topic = topic)
    }

    fun from(
        access: ArianaX88MemoryAccess,
        project: String? = null,
        topic: String? = null,
    ): String =
        access.recall(project = project, topic = topic)
            .asSequence()
            .sortedBy { bucketPriority(it.bucket) }
            .map { it.content.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n") { "- $it" }
            .take(MAX_CHARS)

    private fun bucketPriority(bucket: X88MemoryItem.Bucket): Int =
        when (bucket) {
            X88MemoryItem.Bucket.PERMANENT -> 0
            X88MemoryItem.Bucket.IMPORTANT -> 1
            X88MemoryItem.Bucket.TODAY -> 2
        }
}

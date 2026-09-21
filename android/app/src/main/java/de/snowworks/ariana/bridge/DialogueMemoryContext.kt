package de.snowworks.ariana.bridge

import de.snowworks.ariana.memory.ArianaX88MemoryAccess

/**
 * Builds a bounded, provider-neutral context from durable X88 memories.
 */
object DialogueMemoryContext {
    const val MAX_CHARS = 2400

    fun from(
        access: ArianaX88MemoryAccess,
        project: String? = null,
        topic: String? = null,
    ): String =
        access.recall(project = project, topic = topic)
            .asSequence()
            .map { it.content.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("
") { "- $it" }
            .take(MAX_CHARS)
}

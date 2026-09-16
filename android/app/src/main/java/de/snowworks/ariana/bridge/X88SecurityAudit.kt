package de.snowworks.ariana.bridge

import java.util.ArrayDeque

/**
 * Metadata-only security journal for X-88 policy decisions.
 * Never store prompts, API keys, message bodies or user content here.
 */
object X88SecurityAudit {
    data class Entry(
        val timestampMs: Long,
        val actor: String,
        val event: String,
        val detail: String,
    )

    private const val MAX_ENTRIES = 200
    private val lock = Any()
    private val entries = ArrayDeque<Entry>()

    fun record(actor: String, event: String, detail: String = "") {
        val safeActor = normalize(actor, 40)
        val safeEvent = normalize(event, 48)
        val safeDetail = detail
            .replace(Regex("[\\r\\n\\t]+"), " ")
            .replace(Regex("(?i)(bearer|api[_-]?key|token)\\s*[:=]\\s*[^ ]+"), "$1=[redacted]")
            .trim()
            .take(180)

        synchronized(lock) {
            entries.addFirst(Entry(System.currentTimeMillis(), safeActor, safeEvent, safeDetail))
            while (entries.size > MAX_ENTRIES) entries.removeLast()
        }
    }

    fun snapshot(): List<Entry> = synchronized(lock) { entries.toList() }

    fun clear() = synchronized(lock) { entries.clear() }

    private fun normalize(value: String, max: Int): String = value
        .trim()
        .lowercase()
        .replace(Regex("[^a-z0-9_.-]+"), "_")
        .take(max)
}

package de.snowworks.app.ui

import java.util.ArrayDeque

/**
 * Small process-local audit journal for X-88 UI/runtime events.
 *
 * It intentionally does not persist spoken text, notification contents,
 * file names, model prompts, or other user content.
 */
object X88EventJournal {

    data class Entry(
        val timestampMs: Long,
        val kind: String,
        val detail: String,
    )

    private const val MAX_ENTRIES = 120
    private val entries = ArrayDeque<Entry>()
    private val lock = Any()

    fun add(kind: String, detail: String = "") {
        val safeKind = kind
            .trim()
            .lowercase()
            .replace(Regex("[^a-z0-9_]+"), "_")
            .take(48)

        val safeDetail = detail
            .replace(Regex("[\\r\\n\\t]+"), " ")
            .trim()
            .take(160)

        synchronized(lock) {
            entries.addFirst(
                Entry(
                    timestampMs = System.currentTimeMillis(),
                    kind = safeKind,
                    detail = safeDetail,
                ),
            )
            while (entries.size > MAX_ENTRIES) entries.removeLast()
        }
    }

    fun snapshot(): List<Entry> = synchronized(lock) {
        entries.toList()
    }

    fun clear() = synchronized(lock) {
        entries.clear()
    }
}

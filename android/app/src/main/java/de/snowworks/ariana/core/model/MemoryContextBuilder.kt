package de.snowworks.ariana.core.model

import de.snowworks.ariana.core.MemoryVault

/**
 * Selects a bounded local memory subset before a model request.
 * The whole vault is never included by default.
 */
class MemoryContextBuilder(private val vault: MemoryVault) {
    data class Selection(
        val records: List<MemoryVault.Record>,
        val renderedText: String,
        val totalChars: Int,
    )

    fun select(query: String, maxRecords: Int = DEFAULT_MAX_RECORDS, maxChars: Int = DEFAULT_MAX_CHARS): Selection {
        require(maxRecords in 1..MAX_RECORDS_LIMIT)
        require(maxChars in 256..MAX_CHARS_LIMIT)

        val terms = tokenize(query)
        val ranked = vault.list()
            .asSequence()
            .map { record -> record to score(record, terms) }
            .sortedWith(compareByDescending<Pair<MemoryVault.Record, Int>> { it.second }
                .thenByDescending { it.first.updatedAt })
            .take(maxRecords * 3)
            .toList()

        val selected = mutableListOf<MemoryVault.Record>()
        val builder = StringBuilder()

        for ((record, _) in ranked) {
            if (selected.size >= maxRecords) break
            val block = buildString {
                append("[").append(record.kind).append("] ")
                append(record.text.trim())
                if (record.tags.isNotEmpty()) append("\nTags: ").append(record.tags.joinToString(", "))
            }
            val extra = if (builder.isEmpty()) block.length else block.length + 2
            if (builder.length + extra > maxChars) continue
            if (builder.isNotEmpty()) builder.append("\n\n")
            builder.append(block)
            selected += record
        }

        return Selection(selected, builder.toString(), builder.length)
    }

    private fun score(record: MemoryVault.Record, terms: Set<String>): Int {
        if (terms.isEmpty()) return 0
        val textTokens = tokenize(record.text)
        val tagTokens = record.tags.flatMapTo(mutableSetOf()) { tokenize(it) }
        val kindTokens = tokenize(record.kind)
        var score = 0
        for (term in terms) {
            if (term in textTokens) score += 2
            if (term in tagTokens) score += 4
            if (term in kindTokens) score += 3
        }
        return score
    }

    private fun tokenize(value: String): Set<String> = value
        .lowercase()
        .split(TOKEN_SPLIT)
        .asSequence()
        .map(String::trim)
        .filter { it.length >= 3 }
        .take(MAX_QUERY_TERMS)
        .toSet()

    companion object {
        private const val DEFAULT_MAX_RECORDS = 12
        private const val DEFAULT_MAX_CHARS = 12_000
        private const val MAX_RECORDS_LIMIT = 50
        private const val MAX_CHARS_LIMIT = 64_000
        private const val MAX_QUERY_TERMS = 64
        private val TOKEN_SPLIT = Regex("[^\\p{L}\\p{N}_-]+")
    }
}

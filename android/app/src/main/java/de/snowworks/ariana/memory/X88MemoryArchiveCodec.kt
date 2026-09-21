package de.snowworks.ariana.memory

import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Compact, provider-neutral persistence format for X88 memory items.
 *
 * Each item is one line with Base64-encoded fields, so arbitrary user text
 * and separators remain safe. Unknown/malformed records are skipped on load.
 */
object X88MemoryArchiveCodec {
    private const val FIELD_SEPARATOR = "\t"
    private const val FIELD_COUNT = 10

    fun encode(items: List<X88MemoryItem>): String =
        items.joinToString("\n") { item ->
            listOf(
                item.id,
                item.content,
                item.source.name,
                item.capturedAtEpochMs.toString(),
                item.project,
                item.topic,
                item.bucket.name,
                item.contentType.name,
                item.sourceReference,
                item.contentHash,
            ).joinToString(FIELD_SEPARATOR) { encodeField(it) }
        }

    fun decode(payload: String): List<X88MemoryItem> =
        payload.lineSequence()
            .mapNotNull { line -> decodeLine(line) }
            .toList()

    private fun decodeLine(line: String): X88MemoryItem? {
        if (line.isBlank()) return null
        val fields = line.split(FIELD_SEPARATOR)
        if (fields.size != FIELD_COUNT) return null

        return runCatching {
            X88MemoryItem(
                id = decodeField(fields[0]),
                content = decodeField(fields[1]),
                source = X88MemoryItem.Source.valueOf(decodeField(fields[2])),
                capturedAtEpochMs = decodeField(fields[3]).toLong(),
                project = decodeNullable(fields[4]),
                topic = decodeNullable(fields[5]),
                bucket = X88MemoryItem.Bucket.valueOf(decodeField(fields[6])),
                contentType = X88MemoryItem.ContentType.valueOf(decodeField(fields[7])),
                sourceReference = decodeNullable(fields[8]),
                contentHash = decodeField(fields[9]),
            )
        }.getOrNull()
    }

    private fun encodeField(value: String?): String =
        Base64.getEncoder().encodeToString((value ?: "").toByteArray(StandardCharsets.UTF_8))

    private fun decodeField(value: String): String =
        String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8)

    private fun decodeNullable(value: String): String? =
        decodeField(value).ifEmpty { null }
}

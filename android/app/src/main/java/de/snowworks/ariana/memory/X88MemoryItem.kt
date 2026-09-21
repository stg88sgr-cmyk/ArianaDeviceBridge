package de.snowworks.ariana.memory

data class X88MemoryItem(
    val id: String,
    val content: String,
    val source: Source,
    val capturedAtEpochMs: Long,
    val project: String? = null,
    val topic: String? = null,
    val bucket: Bucket = Bucket.TODAY,
    val contentType: ContentType = ContentType.TEXT,
    val sourceReference: String? = null,
    val contentHash: String
) {
    enum class Source { META_AI, ARIANA, LOCAL, IMPORT }
    enum class Bucket { TODAY, IMPORTANT, PERMANENT }
    enum class ContentType { TEXT, IMAGE, FILE, LINK }
}

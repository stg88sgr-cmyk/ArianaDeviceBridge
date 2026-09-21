package de.snowworks.ariana.evidence

import java.security.MessageDigest

enum class EvidenceKind { TEST, BUILD, RUNTIME, INTEGRITY, MANUAL }
enum class EvidenceStatus { VERIFIED, FAILED, INCONCLUSIVE }

data class X88EvidenceRecord(
    val id: String,
    val stage: Int,
    val kind: EvidenceKind,
    val status: EvidenceStatus,
    val subject: String,
    val summary: String,
    val source: String,
    val timestampEpochMs: Long,
    val artifactSha256: String? = null,
)

data class X88EvidenceReport(
    val stage: Int,
    val records: List<X88EvidenceRecord>,
    val verified: Boolean,
)

class X88EvidenceEngine(private val clock: () -> Long = { System.currentTimeMillis() }) {
    private val records = linkedMapOf<String, X88EvidenceRecord>()

    @Synchronized
    fun record(stage: Int, kind: EvidenceKind, status: EvidenceStatus, subject: String,
               summary: String, source: String, artifact: ByteArray? = null): X88EvidenceRecord {
        require(stage in 1..88)
        require(subject.isNotBlank() && summary.isNotBlank() && source.isNotBlank())
        val id = stableId(stage, kind, subject, source, artifact)
        return X88EvidenceRecord(
            id, stage, kind, status, subject, summary, source, clock(),
            artifact?.let { sha256(it) }
        ).also { records[id] = it }
    }

    @Synchronized
    fun report(stage: Int): X88EvidenceReport {
        val stageRecords = records.values.filter { it.stage == stage }
        return X88EvidenceReport(
            stage, stageRecords,
            stageRecords.isNotEmpty() && stageRecords.all { it.status == EvidenceStatus.VERIFIED }
        )
    }

    @Synchronized fun all(): List<X88EvidenceRecord> = records.values.toList()
    @Synchronized fun clear() = records.clear()

    private fun stableId(stage: Int, kind: EvidenceKind, subject: String,
                         source: String, artifact: ByteArray?): String =
        sha256(("$stage|$kind|$subject|$source|" +
            artifact?.let { sha256(it) }.orEmpty()).toByteArray(Charsets.UTF_8)).take(16)

    private fun sha256(value: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(value)
            .joinToString("") { "%02x".format(it) }
}

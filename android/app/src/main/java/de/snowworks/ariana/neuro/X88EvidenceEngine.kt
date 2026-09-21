package de.snowworks.ariana.neuro

import java.security.MessageDigest
import java.util.ArrayDeque

enum class EvidenceKind {
    SIGNAL,
    TRAINING,
    HEALTH,
    PERSISTENCE,
}

data class X88EvidenceRecord(
    val id: String,
    val kind: EvidenceKind,
    val source: String,
    val claim: String,
    val observed: String,
    val timestamp: Long,
    val digest: String,
)

/**
 * V37 Evidence Engine.
 *
 * Captures compact, machine-readable evidence of internal verification events.
 * Evidence is bounded and contains no prompts, replies, credentials or raw
 * conversation text.
 */
class X88EvidenceEngine(
    private val capacity: Int = 256,
) : NeuroModule {
    override val id: String = "x88-v37-evidence-engine"

    override val inputs: Set<NeuroChannel> = setOf(
        NeuroChannel.TELEMETRY,
        NeuroChannel.INTEGRITY,
        NeuroChannel.MEMORY,
        NeuroChannel.ACTION_RESULT,
        NeuroChannel.DEVICE_STATE,
    )

    override val outputs: Set<NeuroChannel> = emptySet()

    private val records = ArrayDeque<X88EvidenceRecord>()

    init {
        require(capacity > 0) { "capacity must be > 0" }
    }

    override suspend fun onSignal(signal: NeuroSignal): List<NeuroSignal> {
        val claim = signal.payload["claim"]
            ?: signal.payload["event"]
            ?: signal.channel.name.lowercase()
        val observed = signal.payload["observed"]
            ?: signal.payload["result"]
            ?: signal.payload["status"]
            ?: "signal-received"

        record(
            kind = when (signal.channel) {
                NeuroChannel.INTEGRITY -> EvidenceKind.HEALTH
                NeuroChannel.MEMORY -> EvidenceKind.TRAINING
                NeuroChannel.ACTION_RESULT -> EvidenceKind.SIGNAL
                else -> EvidenceKind.SIGNAL
            },
            source = signal.source,
            claim = claim,
            observed = observed,
            timestamp = signal.timestamp,
        )
        return emptyList()
    }

    @Synchronized
    fun record(
        kind: EvidenceKind,
        source: String,
        claim: String,
        observed: String,
        timestamp: Long = System.currentTimeMillis(),
    ): X88EvidenceRecord {
        val normalized = listOf(
            kind.name,
            source,
            claim,
            observed,
            timestamp.toString(),
        ).joinToString("|")

        val record = X88EvidenceRecord(
            id = sha256(normalized).take(24),
            kind = kind,
            source = source,
            claim = claim,
            observed = observed,
            timestamp = timestamp,
            digest = sha256(normalized),
        )

        if (records.size == capacity) records.removeFirst()
        records.addLast(record)
        return record
    }

    @Synchronized
    fun recent(limit: Int = 32): List<X88EvidenceRecord> =
        records.takeLast(limit.coerceIn(0, capacity))

    @Synchronized
    fun count(): Int = records.size

    @Synchronized
    fun clear() = records.clear()

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}

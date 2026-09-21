package de.snowworks.ariana.snapshot

import java.security.MessageDigest

data class X88Snapshot(
    val id: String,
    val stage: Int,
    val schemaVersion: Int,
    val payload: String,
    val sha256: String,
)

interface X88SnapshotStore {
    fun save(snapshot: X88Snapshot)
    fun load(id: String): X88Snapshot?
    fun all(): List<X88Snapshot>
}

class InMemoryX88SnapshotStore : X88SnapshotStore {
    private val snapshots = linkedMapOf<String, X88Snapshot>()

    @Synchronized
    override fun save(snapshot: X88Snapshot) {
        require(snapshot.id.isNotBlank())
        require(snapshot.payload.isNotBlank())
        require(snapshot.sha256 == X88SnapshotCore.sha256(snapshot.payload))
        snapshots[snapshot.id] = snapshot
    }

    @Synchronized
    override fun load(id: String): X88Snapshot? = snapshots[id]

    @Synchronized
    override fun all(): List<X88Snapshot> = snapshots.values.toList()
}

object X88SnapshotCore {
    const val CURRENT_SCHEMA_VERSION = 1

    fun create(id: String, stage: Int, payload: String): X88Snapshot {
        require(id.isNotBlank())
        require(payload.isNotBlank())
        return X88Snapshot(id, stage, CURRENT_SCHEMA_VERSION, payload, sha256(payload))
    }

    fun validate(snapshot: X88Snapshot): Boolean =
        snapshot.schemaVersion == CURRENT_SCHEMA_VERSION &&
            snapshot.id.isNotBlank() &&
            snapshot.payload.isNotBlank() &&
            snapshot.sha256 == sha256(snapshot.payload)

    internal fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}

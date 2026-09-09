package de.snowworks.ariana.core

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/**
 * Local append-only audit trail for sensitive X-Ariana actions.
 *
 * Payload contents are deliberately not stored. A caller may provide a SHA-256
 * hash so the exact payload can later be verified without exposing it in logs.
 */
class AuditLog(context: Context) {
    data class Event(
        val category: String,
        val action: String,
        val decision: String,
        val reason: String? = null,
        val destination: String? = null,
        val provider: String? = null,
        val payloadBytes: Int? = null,
        val payloadSha256: String? = null,
        val timestamp: Long = System.currentTimeMillis(),
        val eventId: String = UUID.randomUUID().toString(),
    )

    private val directory = File(context.applicationContext.filesDir, DIRECTORY).apply { mkdirs() }
    private val file = File(directory, AUDIT_FILE)

    @Synchronized
    fun append(event: Event) {
        validate(event)
        rotateIfNeeded()
        file.appendText(toJson(event).toString() + "\n", Charsets.UTF_8)
    }

    @Synchronized
    fun recent(limit: Int = 100): List<Event> {
        require(limit in 1..MAX_READ_EVENTS)
        if (!file.exists()) return emptyList()
        return file.readLines(Charsets.UTF_8)
            .takeLast(limit)
            .mapNotNull { line -> runCatching { fromJson(JSONObject(line)) }.getOrNull() }
    }

    @Synchronized
    fun exportJsonLines(): String = if (file.exists()) file.readText(Charsets.UTF_8) else ""

    @Synchronized
    fun clear() {
        file.delete()
        File(directory, "$AUDIT_FILE.1").delete()
    }

    private fun rotateIfNeeded() {
        if (!file.exists() || file.length() < MAX_FILE_BYTES) return
        val previous = File(directory, "$AUDIT_FILE.1")
        previous.delete()
        if (!file.renameTo(previous)) error("Could not rotate audit log.")
    }

    private fun validate(event: Event) {
        require(event.category.length in 1..64)
        require(event.action.length in 1..128)
        require(event.decision.length in 1..32)
        require((event.reason?.length ?: 0) <= 512)
        require((event.destination?.length ?: 0) <= 512)
        require((event.provider?.length ?: 0) <= 64)
        require((event.payloadBytes ?: 0) >= 0)
        require(event.payloadSha256 == null || event.payloadSha256.matches(SHA256_REGEX))
    }

    private fun toJson(event: Event): JSONObject = JSONObject()
        .put("schemaVersion", SCHEMA_VERSION)
        .put("eventId", event.eventId)
        .put("timestamp", event.timestamp)
        .put("category", event.category)
        .put("action", event.action)
        .put("decision", event.decision)
        .apply {
            event.reason?.let { put("reason", it) }
            event.destination?.let { put("destination", it) }
            event.provider?.let { put("provider", it) }
            event.payloadBytes?.let { put("payloadBytes", it) }
            event.payloadSha256?.let { put("payloadSha256", it) }
        }

    private fun fromJson(json: JSONObject): Event {
        require(json.optInt("schemaVersion", -1) == SCHEMA_VERSION)
        return Event(
            category = json.getString("category"),
            action = json.getString("action"),
            decision = json.getString("decision"),
            reason = json.optString("reason").takeIf { it.isNotEmpty() },
            destination = json.optString("destination").takeIf { it.isNotEmpty() },
            provider = json.optString("provider").takeIf { it.isNotEmpty() },
            payloadBytes = if (json.has("payloadBytes")) json.getInt("payloadBytes") else null,
            payloadSha256 = json.optString("payloadSha256").takeIf { it.isNotEmpty() },
            timestamp = json.getLong("timestamp"),
            eventId = json.getString("eventId"),
        )
    }

    companion object {
        private const val DIRECTORY = "x_ariana"
        private const val AUDIT_FILE = "audit.jsonl"
        private const val SCHEMA_VERSION = 1
        private const val MAX_FILE_BYTES = 2L * 1024L * 1024L
        private const val MAX_READ_EVENTS = 1_000
        private val SHA256_REGEX = Regex("[0-9a-f]{64}")

        fun sha256(payload: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(payload)
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }
}

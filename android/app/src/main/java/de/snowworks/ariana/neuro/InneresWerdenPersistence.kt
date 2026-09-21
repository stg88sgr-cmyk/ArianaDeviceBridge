package de.snowworks.ariana.neuro

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Durable persistence boundary for the local Inneres-Werden parameter model.
 *
 * Only numeric model state is persisted. Prompts, replies, credentials,
 * permissions and private conversation text never cross this boundary.
 */
interface InneresWerdenSnapshotStore {
    val healthy: Boolean

    fun load(): InneresWerdenModelSnapshot?
    fun save(snapshot: InneresWerdenModelSnapshot): Boolean
    fun clear(): Boolean
}

class SharedPreferencesInneresWerdenSnapshotStore(
    context: Context,
    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE),
) : InneresWerdenSnapshotStore {

    @Volatile
    override var healthy: Boolean = true
        private set

    override fun load(): InneresWerdenModelSnapshot? {
        val encoded = preferences.getString(KEY_SNAPSHOT, null)
        if (encoded == null) {
            healthy = true
            return null
        }

        return runCatching { decode(encoded) }
            .onSuccess { healthy = true }
            .onFailure { healthy = false }
            .getOrNull()
    }

    override fun save(snapshot: InneresWerdenModelSnapshot): Boolean {
        val success = preferences.edit()
            .putString(KEY_SNAPSHOT, encode(snapshot))
            .commit()
        healthy = success
        return success
    }

    override fun clear(): Boolean {
        val success = preferences.edit().remove(KEY_SNAPSHOT).commit()
        healthy = success
        return success
    }

    private fun encode(snapshot: InneresWerdenModelSnapshot): String {
        val payload = JSONObject()
            .put("schemaVersion", SCHEMA_VERSION)
            .put("modelVersion", snapshot.version)
            .put("weights", JSONArray(snapshot.weights))
            .put("bias", snapshot.bias)
            .put("learningRate", snapshot.learningRate)
            .put("l2", snapshot.l2)
            .put("steps", snapshot.steps)

        return JSONObject()
            .put("envelopeSchemaVersion", ENVELOPE_SCHEMA_VERSION)
            .put("payload", payload)
            .put("sha256", sha256(payload.toString()))
            .toString()
    }

    private fun decode(encoded: String): InneresWerdenModelSnapshot {
        val root = JSONObject(encoded)
        require(root.optInt("envelopeSchemaVersion", -1) == ENVELOPE_SCHEMA_VERSION)
        val payload = root.getJSONObject("payload")
        require(payload.getInt("schemaVersion") == SCHEMA_VERSION)
        require(root.getString("sha256") == sha256(payload.toString()))

        val weightsJson = payload.getJSONArray("weights")
        require(weightsJson.length() == InneresWerdenModelSnapshot.FEATURE_COUNT)
        val weights = List(weightsJson.length()) { weightsJson.getDouble(it) }

        return InneresWerdenModelSnapshot(
            version = payload.getInt("modelVersion"),
            weights = weights,
            bias = payload.getDouble("bias"),
            learningRate = payload.getDouble("learningRate"),
            l2 = payload.getDouble("l2"),
            steps = payload.getLong("steps"),
        )
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val PREFERENCES_NAME = "x88_inneres_werden_v1"
        private const val KEY_SNAPSHOT = "model_snapshot"
        private const val ENVELOPE_SCHEMA_VERSION = 1
        private const val SCHEMA_VERSION = 1
    }
}

/** Deterministic store used by JVM/unit tests without Android persistence. */
class InMemoryInneresWerdenSnapshotStore(
    initial: InneresWerdenModelSnapshot? = null,
) : InneresWerdenSnapshotStore {
    private var snapshot: InneresWerdenModelSnapshot? = initial

    override var healthy: Boolean = true
        private set

    override fun load(): InneresWerdenModelSnapshot? = snapshot

    override fun save(snapshot: InneresWerdenModelSnapshot): Boolean {
        this.snapshot = snapshot
        healthy = true
        return true
    }

    override fun clear(): Boolean {
        snapshot = null
        healthy = true
        return true
    }
}

package de.snowworks.ariana.luna

import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference

/**
 * Runtime presence for the LUNA XXY creation peer.
 *
 * This store is observational only. It does not grant permissions, execute
 * hardware actions, mutate X-88 policy, or bypass confirmation gates.
 */
object LunaPresenceStore {

    private const val DEFAULT_STALE_AFTER_MS = 30_000L

    enum class SyncState {
        DISCONNECTED,
        CONNECTING,
        SYNCHRONIZED,
        BUSY,
        STALE,
        ERROR,
    }

    data class Snapshot(
        val connected: Boolean = false,
        val role: String = "CREATION",
        val peerId: String = "LUNA_XXY",
        val lastHeartbeatAtMs: Long? = null,
        val lastTaskId: String? = null,
        val lastTask: String? = null,
        val lastResult: String? = null,
        val syncState: SyncState = SyncState.DISCONNECTED,
        val lastError: String? = null,
        val sequence: Long = 0L,
    ) {
        fun isFresh(nowMs: Long, staleAfterMs: Long = DEFAULT_STALE_AFTER_MS): Boolean {
            val heartbeat = lastHeartbeatAtMs ?: return false
            return connected && nowMs - heartbeat <= staleAfterMs
        }

        fun effectiveSyncState(
            nowMs: Long,
            staleAfterMs: Long = DEFAULT_STALE_AFTER_MS,
        ): SyncState = when {
            !connected -> SyncState.DISCONNECTED
            lastHeartbeatAtMs == null -> SyncState.CONNECTING
            !isFresh(nowMs, staleAfterMs) -> SyncState.STALE
            else -> syncState
        }

        fun toJson(nowMs: Long = System.currentTimeMillis()): JSONObject = JSONObject()
            .put("peerId", peerId)
            .put("role", role)
            .put("connected", connected)
            .put("fresh", isFresh(nowMs))
            .put("syncState", effectiveSyncState(nowMs).name)
            .put("lastHeartbeatAtMs", lastHeartbeatAtMs ?: JSONObject.NULL)
            .put("lastTaskId", lastTaskId ?: JSONObject.NULL)
            .put("lastTask", lastTask ?: JSONObject.NULL)
            .put("lastResult", lastResult ?: JSONObject.NULL)
            .put("lastError", lastError ?: JSONObject.NULL)
            .put("sequence", sequence)
    }

    private val current = AtomicReference(Snapshot())

    fun snapshot(nowMs: Long = System.currentTimeMillis()): Snapshot {
        val state = current.get()
        val effective = state.effectiveSyncState(nowMs)
        return if (effective == state.syncState) state else state.copy(syncState = effective)
    }

    fun connect(nowMs: Long = System.currentTimeMillis()): Snapshot = update { previous ->
        previous.copy(
            connected = true,
            lastHeartbeatAtMs = nowMs,
            syncState = SyncState.SYNCHRONIZED,
            lastError = null,
            sequence = previous.sequence + 1,
        )
    }

    fun heartbeat(nowMs: Long = System.currentTimeMillis()): Snapshot = update { previous ->
        previous.copy(
            connected = true,
            lastHeartbeatAtMs = nowMs,
            syncState = if (previous.syncState == SyncState.BUSY) SyncState.BUSY else SyncState.SYNCHRONIZED,
            lastError = null,
            sequence = previous.sequence + 1,
        )
    }

    fun beginTask(
        taskId: String,
        task: String,
        nowMs: Long = System.currentTimeMillis(),
    ): Snapshot = update { previous ->
        previous.copy(
            connected = true,
            lastHeartbeatAtMs = nowMs,
            lastTaskId = taskId,
            lastTask = task,
            lastResult = null,
            syncState = SyncState.BUSY,
            lastError = null,
            sequence = previous.sequence + 1,
        )
    }

    fun completeTask(
        taskId: String,
        result: String,
        nowMs: Long = System.currentTimeMillis(),
    ): Snapshot = update { previous ->
        previous.copy(
            connected = true,
            lastHeartbeatAtMs = nowMs,
            lastTaskId = taskId,
            lastResult = result,
            syncState = SyncState.SYNCHRONIZED,
            lastError = null,
            sequence = previous.sequence + 1,
        )
    }

    fun failTask(
        taskId: String?,
        error: String,
        nowMs: Long = System.currentTimeMillis(),
    ): Snapshot = update { previous ->
        previous.copy(
            connected = true,
            lastHeartbeatAtMs = nowMs,
            lastTaskId = taskId ?: previous.lastTaskId,
            syncState = SyncState.ERROR,
            lastError = error,
            sequence = previous.sequence + 1,
        )
    }

    fun disconnect(reason: String? = null): Snapshot = update { previous ->
        previous.copy(
            connected = false,
            syncState = SyncState.DISCONNECTED,
            lastError = reason,
            sequence = previous.sequence + 1,
        )
    }

    fun clear(): Snapshot {
        val reset = Snapshot(sequence = current.get().sequence + 1)
        current.set(reset)
        return reset
    }

    private inline fun update(transform: (Snapshot) -> Snapshot): Snapshot {
        while (true) {
            val before = current.get()
            val after = transform(before)
            if (current.compareAndSet(before, after)) return after
        }
    }
}

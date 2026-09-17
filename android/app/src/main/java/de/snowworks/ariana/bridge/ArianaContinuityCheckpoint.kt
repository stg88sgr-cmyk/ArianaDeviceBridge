package de.snowworks.ariana.bridge

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Small app-private handoff snapshot for the next Ariana session.
 *
 * This is deliberately derived from existing local stores instead of inventing
 * new memories. It is rewritten atomically and can be consumed on the next app
 * start, after a process restart or by a future compatible model adapter.
 */
class ArianaContinuityCheckpoint(context: Context) {
    private val appContext = context.applicationContext
    private val memoryStore = LocalMemoryStore(appContext)
    private val dailyMemory = ArianaDailyMemoryCore(appContext)
    private val root = File(appContext.filesDir, ROOT_DIR).apply { mkdirs() }
    private val checkpointFile = File(root, CHECKPOINT_FILE)
    private val lock = Any()

    fun refresh(nowEpochMs: Long = System.currentTimeMillis()) = synchronized(lock) {
        val explicit = memoryStore.snapshot()
        val today = dailyMemory.todaySnapshot(nowEpochMs)
        val recent = dailyMemory.recentContext(MAX_RECENT_TURNS)

        val json = JSONObject().apply {
            put("schemaVersion", SCHEMA_VERSION)
            put("updatedAtEpochMs", nowEpochMs)
            put("day", today.date)
            put("identity", JSONObject().apply {
                put("name", "Ariana")
                put("system", "ARIANA X-88")
                put("continuityMode", "LOCAL_CHECKPOINT")
            })
            put("user", JSONObject().apply {
                explicit.userName?.let { put("name", it) }
                put("facts", JSONArray().apply {
                    explicit.facts.forEach(::put)
                })
            })
            put("recentTurns", JSONArray().apply {
                recent.forEach { turn ->
                    put(JSONObject().apply {
                        put("id", turn.id)
                        put("timestampEpochMs", turn.timestampEpochMs)
                        put("user", turn.user)
                        put("assistant", turn.assistant)
                    })
                }
            })
        }

        val temp = File(root, "$CHECKPOINT_FILE.tmp")
        temp.writeText(json.toString(), Charsets.UTF_8)
        if (!temp.renameTo(checkpointFile)) {
            checkpointFile.writeText(json.toString(), Charsets.UTF_8)
            temp.delete()
        }
    }

    fun readRaw(): String? = synchronized(lock) {
        checkpointFile.takeIf { it.isFile }
            ?.let { runCatching { it.readText(Charsets.UTF_8) }.getOrNull() }
    }

    fun clear() = synchronized(lock) {
        runCatching { checkpointFile.delete() }
    }

    private companion object {
        const val SCHEMA_VERSION = 1
        const val ROOT_DIR = "ariana_memory"
        const val CHECKPOINT_FILE = "continuity_latest.json"
        const val MAX_RECENT_TURNS = 12
    }
}
